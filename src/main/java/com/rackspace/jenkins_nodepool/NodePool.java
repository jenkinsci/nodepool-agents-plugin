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
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Queue.Task;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.SlaveComputer;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Representation of a ZooKeeper+NodePool cluster configuration.
 * <p>
 * Instances hold configuration data entered in the Jenkins UI and are serialized/deserialized via Stapler
 */

public class NodePool implements Describable<NodePool> {

    private static final Logger LOG = Logger.getLogger(NodePool.class.getName());

    /**
     * Create a curator managed connection to ZooKeeper
     *
     * @param connectionString  ZooKeeper connection string
     * @param zkRoot  root path to prefix onto all Curator (ZK) requests
     * @return CuratorFramework connection wrapper instance
     */
    private static CuratorFramework createZKConnection(String connectionString,
            String zkRoot) {
        final CuratorFramework conn = CuratorFrameworkFactory.builder()
                .connectString(connectionString)
                .namespace(zkRoot)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        conn.start();
        return conn;
    }

    private final Charset charset = Charset.forName("UTF-8");

    /**
     * List of Jenkins Computers that are associated with this NodePool cluster.
     */
    private List<NodePoolComputer> computers;

    @XStreamOmitField
    private CuratorFramework conn;

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
    @XStreamOmitField
    private final Gson gson = new Gson();

    /**
     * Prefix used to associate select Jenkins labels with this NodePool cluster.
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
     * List of node requests submitted to this NodePool cluster
     */
    private List<NodeRequest> requests;

    /**
     * Prefix used to prepend to all NodePool related ZNodes
     */
    private String zooKeeperRoot;

    /**
     * Constructor invoked by Jenkins's Stapler library.
     *
     * @param connectionString  ZooKeeper connection string
     * @param credentialsId  Credential information identifier
     * @param labelPrefix  Prefix for labels served by this NodePool cluster
     * @param requestRoot  Prefix of node requests
     * @param priority  Priority value of node requests
     * @param requestor  Name of process making node requests
     * @param zooKeeperRoot  Prefix of all NodePool-related ZNodes
     * @param nodeRoot  Prefix of nodes
     */
    @DataBoundConstructor
    public NodePool(String connectionString,
            String credentialsId, String labelPrefix, String requestRoot,
            String priority, String requestor, String zooKeeperRoot,
            String nodeRoot) {
        this.connectionString = connectionString;
        this.credentialsId = credentialsId;
        this.requestRoot = requestRoot;
        this.priority = priority;
        this.requestor = requestor;
        this.labelPrefix = labelPrefix;
        this.zooKeeperRoot = zooKeeperRoot;
        this.nodeRoot = nodeRoot;
        initTrackers();
    }
    /**
     * Accept the node that was created to satisfy the given request.
     *
     * @param request  node request
     * @return node name as a String
     * @throws java.lang.Exception on ZooKeeper error
     */
    public List<NodePoolNode> acceptNodes(NodeRequest request) throws Exception {

        // refer to the request "nodeset" to know which nodes to lock.
        final List<NodePoolNode> allocatedNodes = request.getAllocatedNodes();
        final List<NodePoolNode> acceptedNodes = new ArrayList<>();

        try {
            for (NodePoolNode node : allocatedNodes) {
                LOG.log(Level.FINE, "Accepting node {0} on behalf of request {1}", new Object[]{node, request.getZKID()});

                node.setInUse(); // TODO: debug making sure this lock stuff actually works

                acceptedNodes.add(node);

            }
        } catch (Exception e) {
            // (if we hit this, then the request will get re-created on the next isDone() poll.)
            LOG.log(Level.WARNING, "Failed to lock node" + e.getMessage(), e);

            // roll back acceptance on any nodes we managed to successfully accept
            for (NodePoolNode acceptedNode : acceptedNodes) {
                try {
                    acceptedNode.release();

                } catch (Exception lockException) {
                    LOG.log(Level.WARNING, "Failed to release lock on node " + acceptedNode.getName() + ": "
                            + lockException.getMessage(), lockException);
                }
            }

        } finally {
            // regardless of success locking node, delete the request.
            requests.remove(request);
            request.delete();
        }

        return acceptedNodes;
    }

    public Charset getCharset() {
        return charset;
    }

    public CuratorFramework getConn() {
        if (conn == null) {
            conn = NodePool.createZKConnection(connectionString, zooKeeperRoot);
        }
        return conn;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getCredentialsId() {
        return credentialsId;
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
    public List<NodePoolComputer> getNodes() {
        return computers;
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
        return requests;
    }

    public final String getZooKeeperRoot() {
        return zooKeeperRoot;
    }
    //TODO: figure out how to reuse the form validation functions or dedupe this somehow.
    public final Boolean isConfigured() {
        if (connectionString == null || !connectionString.contains(":")) {
            return false;
        }
        if (credentialsId == null || "".equals(credentialsId)) {
            return false;
        }
        if (labelPrefix == null || "".equals(labelPrefix)) {
            return false;
        }
        return true;
    }

    /**
     * Convert the given jenkins label into its NodePool equivalent
     *
     * @param jenkinsLabel  jenkins label
     * @return  NodePool label without the prefix
     */
    public String nodePoolLabelFromJenkinsLabel(String jenkinsLabel) {
        return jenkinsLabel.substring(getLabelPrefix().length());
    }

    /**
     * Remove a computer from the tracking list
     * @param c  computer
     */
    public void removeComputer(NodePoolComputer c) {
        computers.remove(c);
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

    public void setNodes(List<NodePoolComputer> nodes) {
        this.computers = nodes;
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
    }

    public void setZooKeeperRoot(String zooKeeperRoot) {
        this.zooKeeperRoot = zooKeeperRoot;
    }

    private void initTrackers() {
        if (requests == null) {
            requests = new ArrayList();
        }
        if (computers == null) {
            this.computers = new ArrayList();
        }
    }

    /**
     * Extract the request id from the given path
     *
     * @param path  path to node request
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
     * Submit request for node(s) required to execute the given task
     *
     * @param label  Jenkins label
     * @param task  task/build being executed
     * @throws Exception on ZooKeeper error
     */
    void provisionNode(Label label, Task task) throws Exception {

        // *** Request Node ***
        //TODO: store prefix in config and pass in.
        final NodeRequest request = new NodeRequest(this, task);
        requests.add(request);

        // *** Poll request status and wait for fulfillment
        //TODO: store timeout in config
        final Integer timeout = 1200; //timeout in seconds
        List<NodePoolNode> allocatedNodes = null;

        // Let's create a node pool watcher and wait until the request is in the desired state (or until we're timed out
        final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                conn.getZookeeperClient().getZooKeeper(), request.getPath(), RequestState.fulfilled);
        if (watcher.waitUntilDone(timeout, TimeUnit.SECONDS)) {
            try {
                // accept here so that if any error conditions occur, the above update logic will automatically re-submit
                // the node request:
                allocatedNodes = acceptNodes(request);
                computers.addAll(allocatedNodes
                        .stream()
                        .map(NodePoolNode::getComputer)
                        .collect(Collectors.toList()));
            } catch (Exception ex) {
                throw new Exception(ex.getClass().getCanonicalName() + " occurred while adding nodes. Message: " + ex.getLocalizedMessage());
            }

        } else {
            // TODO: daviddeal - Should we retry if failed - maybe look at the state again??
            //if (requestState == RequestState.failed) {
            throw new Exception( "NodePool watcher expired waiting for request state to be: " + RequestState.fulfilled +
                    ". Provisioning failed for node label: " + task.getAssignedLabel().getName());
        }

        // *** Get allocated nodes from the request and add to Jenkins
        final NodePoolNode node = allocatedNodes.get(0);
        final NodePoolSlave nps = new NodePoolSlave(node, getCredentialsId());
        final Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(SlaveComputer.CREATE);
        jenkins.addNode(nps);
        LOG.log(Level.INFO, "Added NodePool slave to Jenkins: {0}", nps);
    }

    /**
     * Descriptor class to support configuration of a NodePool instance in the Jenkins UI
     *
     */

    @Extension
    public static class NodePoolDescriptor extends Descriptor<NodePool> {

        public void doTestZooKeeperConnection(StaplerRequest req,
                StaplerResponse resp, @QueryParameter final String connectionString, @QueryParameter final String zooKeeperRoot) throws IOException, ServletException {
            new FormFieldValidator(req, resp, true) {
                @Override
                protected void check() throws IOException, ServletException {
                    try {
                        CuratorFramework testConn = NodePool.createZKConnection(connectionString, zooKeeperRoot);
                        String node = testConn.create()
                                .creatingParentsIfNeeded()
                                .withMode(CreateMode.EPHEMERAL)
                                .forPath(MessageFormat.format("/testing/{0}", req.getSession().getId()));
                        testConn.close();
                        ok(MessageFormat.format("Successfully connected to Zookeeper at {0}", connectionString));
                    } catch (Exception e) {
                        error(MessageFormat.format("Failed to connecto to ZooKeeper :( {0}", e.getMessage()));
                    }
                }
            }.process();
        }

        public FormValidation doCheckConnectionString(@QueryParameter String connectionString) {
            LOG.log(Level.INFO, "DoCheckConnectionString: {0}", connectionString);
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
            LOG.log(Level.INFO, "doCheckLabelPrefix: {0}", labelPrefix);
            if ("".equals(labelPrefix)) {
                return FormValidation.error("label prefix must not be blank, that would cause this plugin to request a node for every label.");
            } else {
                return FormValidation.ok();
            }
        }

        /**
         * Shamelessly stolen from
         * https://github.com/jenkinsci/ssh-slaves-plugin/blob/master/src/main/java/hudson/plugins/sshslaves/SSHConnector.java#L314
         *
         * @param context  item grouping context
         * @param credentialsId  Jenkins credential identifier
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

}
