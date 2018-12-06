/*
 * The MIT License
 *
 * Copyright 2018 hughsaunders.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.rackspace.jenkins_nodepool;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.gson.Gson;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.kohsuke.stapler.*;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Representation of a ZooKeeper+NodePool cluster configuration.
 * <p>
 * Instances hold configuration data entered in the Jenkins UI and are
 * serialised/deserialised via Stapler. This class does not implement
 * serialisable, it would be useful if it did, as then readObject could be used
 * to initTransients. However making it serialisable causes problems with the
 * Charset field which doesn't implement serialisable but does serialise just
 * fine * via xstreame :/
 */
public class NodePool implements Describable<NodePool> {

    private static final Logger LOG = Logger.getLogger(NodePool.class.getName());


    /**
     * Create a curator managed connection to ZooKeeper
     *
     * @param connectionString ZooKeeper connection string
     * @param zkRoot           root path to prefix onto all Curator (ZK) requests
     * @return CuratorFramework connection wrapper instance
     */
    public static CuratorFramework createZKConnection(String connectionString, String zkRoot) {
        final CuratorFramework conn = CuratorFrameworkFactory.builder()
                .connectString(connectionString)
                .namespace(zkRoot)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        conn.start();
        return conn;
    }

    // Apparently Charset isn't serialisable.
    private static final String charset = "UTF-8";


    private transient CuratorFramework conn;

    /**
     * ZooKeeper connection string
     */
    private String connectionString;

    /**
     * Jenkins sourced credential identifier
     */
    private String credentialsId;

    /**
     * Serializer used for saving node request data to/from ZooKeeper
     */
    private transient final Gson gson = new Gson();

    /**
     * Prefix used to associate select Jenkins labels with this NodePool
     * cluster.
     */
    private String labelPrefix;

    /**
     * ZNode prefix prepended to all nodes
     */
    private String nodeRoot;

    /**
     * Priority value used as part of the node request path.
     */
    private String priority;

    /**
     * ZNode path prefix prepended to all node requests.
     */
    private String requestRoot;

    /**
     * Name of process submitting a node request.
     */
    private String requestor;

    /**
     * Timeout for a node request
     */
    private Integer requestTimeout;

    /**
     * maximum number of times provisioning a node will be tried for a given task
     */
    private Integer maxAttempts;

    /**
     * Timeout in seconds for ssh connection to a node and installation of the JRE.
     */
    private Integer installTimeout;

    /**
     * List of node requests submitted to this NodePool cluster
     */
    transient List<NodeRequest> requests;

    /**
     * Prefix used to prepend to all NodePool related ZNodes
     */
    private String zooKeeperRoot;

    /**
     * The JDK installation script
     */
    private String jdkInstallationScript;

    /**
     * The JDK home installation folder.
     */
    private String jdkHome;

    /**
     * Constructor invoked by Jenkins's Stapler library.
     *
     * @param connectionString      ZooKeeper connection string
     * @param credentialsId         Credential information identifier
     * @param labelPrefix           Prefix for labels served by this NodePool cluster
     * @param requestRoot           Prefix of node requests
     * @param priority              Priority value of node requests
     * @param requestor             Name of process making node requests
     * @param zooKeeperRoot         Prefix of all NodePool-related ZNodes
     * @param nodeRoot              Prefix of nodes
     * @param requestTimeout        Length of time to wait for node requests to be
     *                              fulfilled
     * @param jdkInstallationScript the JDK installation script
     * @param jdkHome               the JDK home
     * @param installTimeout        timeout in seconds to connect to a new node and provision the JRE
     * @param maxAttempts           maximum number of times to try to provision the node
     */
    @DataBoundConstructor
    public NodePool(String connectionString,
                    String credentialsId, String labelPrefix, String requestRoot,
                    String priority, String requestor, String zooKeeperRoot,
                    String nodeRoot, Integer requestTimeout, String jdkInstallationScript, String jdkHome,
                    Integer installTimeout, Integer maxAttempts) {
        this.connectionString = connectionString;
        this.credentialsId = credentialsId;
        this.requestRoot = requestRoot;
        this.priority = priority;
        this.requestor = requestor;
        this.labelPrefix = labelPrefix;
        this.zooKeeperRoot = zooKeeperRoot;
        this.nodeRoot = nodeRoot;
        this.jdkInstallationScript = jdkInstallationScript;
        this.jdkHome = jdkHome;
        this.installTimeout = installTimeout;
        this.maxAttempts = maxAttempts;
        setRequestTimeout(requestTimeout);
        initTransients();
    }

    /**
     * Hash Code based on connection string
     */
    public int hashCode(){
        return connectionString.hashCode();
    }

    /**
     * Equals based on connection string
     */
    @Override
    public boolean equals(Object other){
        if (other == null) return false;
        if (this == other) return true;
        if (other instanceof NodePool) {
            return getConnectionString().equalsIgnoreCase(((NodePool) other).getConnectionString());
        } else {
            return false;
        }
    }

    /**
     * Accept the node that was created to satisfy the given request.
     *
     * @param request node request
     * @return node name as a String
     * @throws java.lang.Exception on ZooKeeper error
     */
    public List<NodePoolNode> acceptNodes(NodeRequest request) throws Exception {

        initTransients();

        // refer to the request "nodeset" to know which nodes to lock.
        final List<NodePoolNode> allocatedNodes = request.getAllocatedNodes();
        final List<NodePoolNode> acceptedNodes = new ArrayList<>();

        try {
            for (NodePoolNode node : allocatedNodes) {
                LOG.log(INFO, format("Accepting node %s on behalf of request %s", node, request.getZKID()));
                node.setInUse(); // TODO: debug making sure this lock stuff actually works
                acceptedNodes.add(node);
            }
        } catch (Exception e) {
            // (if we hit this, then the request will get re-created on the next isDone() poll.)
            LOG.log(WARNING, format("%s while attempting to accept and lock node. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));

            // roll back acceptance on any nodes we managed to successfully accept
            for (NodePoolNode acceptedNode : acceptedNodes) {
                try {
                    LOG.log(INFO, format("Releasing node %s on behalf of request %s", acceptedNode, request.getZKID()));
                    acceptedNode.release();

                } catch (Exception lockException) {
                    LOG.log(WARNING, format("%s while attempting to release lock on node %s. Message: %s",
                            e.getClass().getSimpleName(), acceptedNode.getName(), lockException.getLocalizedMessage()));
                }
            }

        } finally {
            // regardless of success locking node, delete the request.
            requests.remove(request);
            request.delete();
        }

        return acceptedNodes;
    }

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    public final void setRequestTimeout(Integer requestTimeout) {
        // ensure value can be parsed as an integer
        if (requestTimeout <= 1) {
            throw new IllegalArgumentException("Request timeout must be >=1");
        }
        this.requestTimeout = requestTimeout;
    }

    public Charset getCharset() {
        return Charset.forName(charset);
    }

    public CuratorFramework getConn() {
        initTransients();
        return conn;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Returns the JDK installation script.
     *
     * @return the JDK installation script.
     */
    public String getJdkInstallationScript() {
        return jdkInstallationScript;
    }

    /**
     * Returns the JDK Home for this node.
     *
     * @return the JDK home for his node.
     */
    public String getJdkHome() {
        return jdkHome;
    }

    @Override
    public Descriptor<NodePool> getDescriptor() {
        return new NodePoolDescriptor();

    }

    public Gson getGson() {
        return gson;
    }

    public String getLabelPrefix() {
        return labelPrefix;
    }

    public String getNodeRoot() {
        return nodeRoot;
    }

    public String getPriority() {
        return priority;
    }

    public String getRequestRoot() {
        return requestRoot;
    }

    public String getRequestor() {
        return requestor;
    }

    public List<NodeRequest> getRequests() {
        initTransients();
        return requests;
    }

    public final String getZooKeeperRoot() {
        return zooKeeperRoot;
    }

    /**
     * Convert the given jenkins label into its NodePool equivalent
     *
     * @param jenkinsLabel jenkins label
     * @return NodePool label without the prefix
     */
    public String nodePoolLabelFromJenkinsLabel(String jenkinsLabel) {
        return jenkinsLabel.substring(getLabelPrefix().length());
    }

    public void setConnectionString(String connectionString) {
        if (!connectionString.equals(this.connectionString)) {
            conn = NodePool.createZKConnection(connectionString, getZooKeeperRoot());
        }
        this.connectionString = connectionString;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public void setLabelPrefix(String labelPrefix) {
        this.labelPrefix = labelPrefix;
    }

    public void setNodeRoot(String nodeRoot) {
        this.nodeRoot = nodeRoot;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setRequestRoot(String requestRoot) {
        this.requestRoot = requestRoot;
    }

    public void setRequestor(String requestor) {
        this.requestor = requestor;
    }

    public void setRequests(List<NodeRequest> requests) {
        this.requests = requests;
        initTransients();
    }

    public void setZooKeeperRoot(String zooKeeperRoot) {
        this.zooKeeperRoot = zooKeeperRoot;
    }

    private void initTransients() {
        if (requests == null) {
            requests = new ArrayList<>();
        }
        if (conn == null && connectionString != null) {
            conn = NodePool.createZKConnection(connectionString, zooKeeperRoot);
        }
        if (conn != null && conn.getState() != CuratorFrameworkState.STARTED){
            conn.start();
        }
    }

    /**
     * Extract the request id from the given path
     *
     * @param path path to node request
     * @return request id
     * @throws NodePoolException if the request id cannot be found
     */
    String idForPath(String path) throws NodePoolException {
        if (path.contains("-")) {
            List<String> parts = Arrays.asList(path.split("-"));
            return parts.get(parts.size() - 1);

        } else {
            throw new NodePoolException("Invalid node path while looking for request id: " + path);
        }
    }

    /**
     * Submit request for node(s) required to execute the given task based on the nodes associated with the specified
     * label. Uses a default timeout of 60 seconds.
     *
     * @param job the job to execute
     * @throws IllegalArgumentException if timeout is less than 1 second
     * @throws Exception                if an error occurs managing the provision components
     */
    public void provisionNode(NodePoolJob job) throws Exception {
        provisionNode(job, requestTimeout, maxAttempts, installTimeout);
    }

    /**
     * Submit request for node(s) required to execute the given task.
     *
     * @param job               job/task/build being executed
     * @param requestTimeoutSec the timeout in seconds to provision the node(s)
     * @param maxAttempts       maximum number of times to try to provision the node
     * @param installTimeoutSec timeout in seconds to connect to a new node and provision the JRE
     * @throws IllegalArgumentException if timeout is less than 1 second or if the maxAttempts is less than 1
     * @throws NodePoolException        if an error occurs managing the provision components
     */
    void provisionNode(NodePoolJob job, int requestTimeoutSec, int maxAttempts, int installTimeoutSec) throws NodePoolException {

        if (requestTimeoutSec < 1) {
            throw new IllegalArgumentException("Timeout value is less than 1 second: " + requestTimeoutSec);
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Maximum attempts value is less than 1: " + maxAttempts);
        }

        initTransients();

        for (int i = 0; i < maxAttempts; i++) {
            try {
                if (job.getRun().isBuilding()) {
                    attemptProvision(job, requestTimeoutSec, installTimeoutSec);
                    break;
                } else {
                    // build has been cancelled
                    // nothing else to do, janitor will cleanup any remaining objects
                    return;
                }

            } catch (Exception e) {
                job.logToBoth(format("Node provisioning attempt for task: %s failed. Message: %s",
                        job.getTask().getName(), e.getLocalizedMessage()), WARNING);
                if (i + 1 == maxAttempts) {
                    throw new NodePoolException(format("Maximum attempts exceeded: %d out of %d.",
                            (i + 1), maxAttempts));
                }
            }
        }
    }

    /**
     * Make a single node provisioning attempt.  Update the progress state of the `job`.
     *
     * @param job               object for tracking overall progress of the task/job
     * @param requestTimeoutSec watcher timeout
     * @param installTimeoutSec ssh connection / jre install timeout
     * @throws Exception if the provisioning attempt fails
     */
    void attemptProvision(NodePoolJob job, int requestTimeoutSec, int installTimeoutSec) throws Exception {

        //final Task task = job.getTask();

        //job.logToBoth(String.format("Waiting on node to become available for task: %s with label: %s with timeout: %d seconds...",
        //        task.getName(), job.getLabel(), timeoutInSec));

        final NodeRequest request = createNodeRequest(job);
        requests.add(request);

        try {
            job.addAttempt(request);
            attemptProvisionNode2(request, requestTimeoutSec, installTimeoutSec);
        } catch (Exception e) {
            // provisioning attempt failed
            job.failAttempt(e);
            LOG.severe(format("%s while attempting to provision node %s. Message: %s",
                    e.getClass().getSimpleName(), job.getNodePoolNode().getName(), e.getLocalizedMessage()));
            try {
                LOG.log(FINE, format("Releasing node after failed provisioning attempt: %s",
                        job.getNodePoolNode().getName()));
                job.getNodePoolNode().release();
                try {
                    // Findbugs :(
                    NodePoolSlave nodePoolSlave = job.getNodePoolSlave();
                    if (nodePoolSlave == null) {
                        return;
                    }
                    Computer c = nodePoolSlave.toComputer();
                    if (c != null) {
                        c.doDoDelete();
                    }
                } catch (IOException ex) {
                    Jenkins.getInstance().removeNode(job.getNodePoolSlave());
                }
            } catch (Exception ex) {
                // Failed to cleanup node after a failed attempt
                // This is only an optimisation, the Janitor
                // will ensure cleanup is completed once the
                // build is complete.
                LOG.log(FINE, format("%s while attempting to cleanup after a failed provision attempt. Message: %s",
                        ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
            }
            throw e;
        } finally {
            requests.remove(request);
            request.delete();
        }
        job.succeed();
    }

    NodeRequest createNodeRequest(final NodePoolJob job) throws Exception {
        return new NodeRequest(this, getPriority(), job);
    }

    /**
     * Make a single provisioning attempt.
     *
     * @param request             node request object
     * @param requestTimeoutInSec watcher timeout
     * @throws Exception if provisioning fails
     */
    void attemptProvisionNode2(final NodeRequest request,
                               final int requestTimeoutInSec, final int installTimeoutSec) throws Exception {

        List<NodePoolNode> allocatedNodes;
        NodePoolJob nodePoolJob = request.getJob();
        if (nodePoolJob == null) {
            throw new NodePoolException("NodePoolJob null in NodePool.attemptProvision2 for request:" + request);
        }
        // Let's create a node pool watcher and wait until the request is in the desired state
        // (or until we're timed out)
        final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                conn, request.getPath(), NodePoolState.FULFILLED, nodePoolJob);

        try {
            watcher.waitUntilDone(requestTimeoutInSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            request.delete();
            throw new InterruptedException("Timeout waiting for request to get fulfilled: "
                    + e.getMessage());
        } catch (NodePoolException e) {
            request.delete();
            throw e;
        }

        // Success represents is request fulfilled - everything else is a problem.
        if (request.getState() == NodePoolState.FULFILLED) {
            allocatedNodes = acceptNodes(request);

            // Get allocated nodes from the request and add to Jenkins
            for (NodePoolNode node : allocatedNodes) {
                // This creates the slave, then the launcher and computer
                // it returns before the launch is complete, so errors are
                // not handled.
                final NodePoolSlave nps = new NodePoolSlave(node, getCredentialsId(), nodePoolJob, getJdkHome(), getJdkInstallationScript());
                nodePoolJob.setNodePoolSlave(nps);
                Jenkins.getInstance().addNode(nps);

                LocalDateTime launchDeadline = LocalDateTime.now().plusSeconds(installTimeoutSec);
                NodePoolComputer npc;
                while (true) {
                    npc = (NodePoolComputer) Jenkins.getInstance().getComputer(nodePoolJob.getNodePoolNode().getName());

                    if (npc != null && !npc.isOffline()) {
                        // node is online, great, stop waiting for it.
                        break;
                    }

                    // Check for conditions that mean that we no longer
                    // need to wait for this node (Build finished and Timeout)
                    if (!nodePoolJob.getRun().isBuilding()) {
                        // If the build has completed, we no longer care if
                        // the node managed to come online, so stop waiting.
                        break;
                    }
                    if (LocalDateTime.now().isAfter(launchDeadline)) {
                        LOG.warning(format("Launch deadline expired: %s for job: %s", nps.getDisplayName(), nodePoolJob.getRun().getDisplayName()));
                        break;
                    }

                    Thread.sleep(500);
                }

                if (nodePoolJob.getRun().isBuilding()) {
                    // build still running
                    if (npc == null || npc.isOffline()) {
                        // build still running and node failed to come online
                        throw new NodePoolException("Failed to launch Jenkins agent on " + nps.getNodePoolNode().getName() + " NPC: " + npc);
                    } else {
                        // build running and node is online, add some details to the logs
                        nodePoolJob.logToBoth("NodePoolSlave instance " + nps.getNodePoolNode().getName() +
                                " with host: " + nps.getNodePoolNode().getHost() +
                                " with label: " + request.getJenkinsLabel().getDisplayName() +
                                " from task: " + request.getTask().getName() +
                                " for build: " + nodePoolJob.getBuildId() +
                                " is online.");
                    }
                }
            }
        } else {
            throw new Exception(format("Request failed or aborted while waiting for request state: %s, actual state: %s",
                    NodePoolState.FULFILLED, request.getState()));
        }
    }

    /**
     * Descriptor class to support configuration of a NodePool instance in the
     * Jenkins UI
     */
    @Extension
    public static class NodePoolDescriptor extends Descriptor<NodePool> {

        public void doTestZooKeeperConnection(StaplerRequest req,
                                              StaplerResponse resp, @QueryParameter final String connectionString, @QueryParameter final String zooKeeperRoot) throws IOException, ServletException {
            new FormFieldValidator(req, resp, true) {
                @Override
                protected void check() throws IOException, ServletException {
                    try (CuratorFramework testConn = NodePool.createZKConnection(connectionString, zooKeeperRoot)) {
                        testConn.create()
                                .creatingParentsIfNeeded()
                                .withMode(CreateMode.EPHEMERAL)
                                .forPath(format("/testing/%s", req.getSession().getId()));
                        ok(format("Successfully connected to Zookeeper at %s", connectionString));
                    } catch (Exception e) {
                        error(format("Failed to connection to ZooKeeper :( %s", e.getMessage()));
                    }
                }
            }.process();
        }

        public FormValidation doCheckConnectionString(@QueryParameter String connectionString) {
            if (connectionString.contains(":")) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Connection string must be of the form host:port or host:port,hostn:portn");
            }
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
            if ("".equals(credentialsId)) {
                return FormValidation.error("SSH credentials must be supplied. User+Password or User+Key are ok.");
            } else {
                return FormValidation.ok();

            }
        }

        public FormValidation doCheckLabelPrefix(@QueryParameter String labelPrefix) {
            if ("".equals(labelPrefix)) {
                return FormValidation.error("label prefix must not be blank, that would cause this plugin to request a node for every label.");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckRequestTimeout(@QueryParameter String requestTimeout) {
            String error = "Request Timeout must be a whole number greater than zero.";
            try {
                Integer rt = Integer.parseInt(requestTimeout);
                if (rt <= 0) {
                    return FormValidation.error(error);
                }
                return FormValidation.ok();
            } catch (NumberFormatException ex) {
                return FormValidation.error(error);
            }
        }

        /**
         * Shamelessly stolen from
         * https://github.com/jenkinsci/ssh-slaves-plugin/blob/master/src/main/java/hudson/plugins/sshslaves/SSHConnector.java#L314
         *
         * @param context       item grouping context
         * @param credentialsId Jenkins credential identifier
         * @return a list box model
         */
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String credentialsId) {
            AccessControlled _context = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel()
                        .includeCurrentValue(credentialsId);
            }
            return new StandardUsernameListBoxModel()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            context,
                            StandardUsernameCredentials.class,
                            Collections.<DomainRequirement>singletonList(SSHLauncher.SSH_SCHEME),
                            SSHAuthenticator.matcher(Connection.class)
                    )
                    .includeCurrentValue(credentialsId);
        }

        @Override
        public String getDisplayName() {
            return "NodePool Global Configuration";
        }
    }

    /**
     * Release resources related to this nodepool
     */
    void cleanup() {
        LOG.log(INFO, format("Removing Nodepool Configuration %s", connectionString));
        if (conn != null) {
            conn.close();
        }
    }

    @Override
    public String toString() {
        return connectionString;
    }
}
