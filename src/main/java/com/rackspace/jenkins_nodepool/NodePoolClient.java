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

import com.google.gson.Gson;
import hudson.model.Label;
import hudson.slaves.SlaveComputer;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

/**
 * Notes on what needs to be implemented form the Jenkins side.. Cloud:
 * provision: NodeProvisioner.PlannedNode Slave: createComputer (Pass in *
 * enough information about node to free it, must be serialisable so it can be
 * stored in global config incase a user manually reconfigures the node)
 * Computer onRemoved: Release the node here. RetentionStrategy
 * (CloudRetentionStrategy is based on idle minutes which doesn't work for our
 * use case as we want to ensure that each node is only used for one job ) Maybe
 * use cloudretention strategy and a runlistener to listen to job completion
 * events and offline the slave so it isn't reused before its removed.
 *
 *
 *
 * Single use node life cycle: request wait for provisioning accept lock use
 *
 */
public class NodePoolClient {

    private static final Logger LOGGER = Logger.getLogger(NodePoolClient.class.getName());

    private String connectionString;

    // these roots are relative to /nodepool which is the namespace
    // set on the curator framework connection
    private final String requestRoot = "requests";
    private final String requestLockRoot = "requests-lock";
    private final String nodeRoot = "nodes";
    private Integer priority;
    private String credentialsId;

    private static final Gson GSON = new Gson();
    private static final Charset CHARSET = Charset.forName("UTF-8");

    public NodePoolClient(String connectionString, String credentialsId) {
        this(connectionString, 100, credentialsId);
    }

    public NodePoolClient(String connectionString, Integer priority, String credentialsId) {
        this.connectionString = connectionString;
        this.priority = priority;
        this.credentialsId = credentialsId;
    }

    public String getRequestRoot() {
        return requestRoot;
    }

    public String getRequestLockRoot() {
        return requestLockRoot;
    }

    public String getNodeRoot() {
        return nodeRoot;
    }

    public Integer getPriority() {
        return priority;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public Future<NodePoolSlave> request() {
        return null;
    }

    static String idForPath(String path) throws NodePoolException {
        if (path.contains("-")) {
            List<String> parts = Arrays.asList(path.split("-"));
            return parts.get(parts.size() - 1);

        } else {
            throw new NodePoolException("Invalid node path while looking for request id: " + path);
        }
    }

    CuratorFramework getConnection() {
        return ZooKeeperClient.getConnection(connectionString);
    }

    // TODO: similar with nodeSet for multiple nodes.
    // or just create multiple requests?
    public NodeRequest requestNode(String nPLabel, String jenkinsLabel) throws Exception {
        final NodeRequest request = new NodeRequest(connectionString, nPLabel, jenkinsLabel);
        final String createPath = MessageFormat.format("/{0}/{1}-", this.requestRoot, priority.toString());
        LOGGER.finest(MessageFormat.format("Creating request node: {0}", createPath));
        String requestPath = getConnection().create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(createPath, request.toString().getBytes());

        LOGGER.log(Level.FINEST, "Requeste created at path: {0}", requestPath);

        // get NodePool request id
        final String id = idForPath(requestPath);
        request.setZKID(id);
        request.setPath(requestPath);

        return request;

    }

    /**
     * Get data for a node
     *
     * @param path path to query
     * @return Map representing the json data stored on the node.
     * @throws Exception barf
     */
    public Map getZNode(String path) throws Exception {
        byte[] jsonBytes = getConnection().getData().forPath(path);
        String jsonString = new String(jsonBytes, CHARSET);
        final Map data = GSON.fromJson(jsonString, HashMap.class);
        return data;
    }

    public boolean nodeExists(String path) throws Exception {
        // check if the ZNode at the given path exists
        return getConnection().checkExists().forPath(path) != null;
    }

    /**
     * Accept the node that was created to satisfy the given request.
     *
     * @param request node request
     * @return node name as a String
     * @throws java.lang.Exception
     */
    public List<NodePoolNode> acceptNodes(NodeRequest request) throws Exception {

        // refer to the request "nodeset" to know which nodes to lock.
        final List<NodePoolNode> nodes = request.getAllocatedNodes();
        final List<NodePoolNode> acceptedNodes = new ArrayList<>();

        try {
            for (NodePoolNode node : nodes) {
                LOGGER.log(Level.FINE, "Accepting node {0} on behalf of request {1}", new Object[]{node, request.getZKID()});

                node.setInUse(); // TODO: debug making sure this lock stuff actually works

                acceptedNodes.add(node);

            }
        } catch (Exception e) {
            // (if we hit this, then the request will get re-created on the next isDone() poll.)
            LOGGER.log(Level.WARNING, "Failed to lock node" + e.getMessage(), e);

            // roll back acceptance on any nodes we managed to successfully accept
            for (NodePoolNode acceptedNode : acceptedNodes) {
                try {
                    acceptedNode.release();

                } catch (Exception lockException) {
                    LOGGER.log(Level.WARNING, "Failed to release lock on node " + acceptedNode.getName() + ": "
                            + lockException.getMessage(), lockException);
                }
            }

        } finally {
            // regardless of success locking node, delete the request.
            deleteNode(request.getPath());
        }

        return acceptedNodes;
    }

    public void deleteNode(String path) {
        try {
            getConnection().delete().forPath(path);
        } catch (Exception e) {
            // not sure what else we can do at this point.
            LOGGER.log(Level.WARNING, "Failed to delete node at path: " + path + ": " + e.getMessage(), e);
        }
    }

    void provisionNode(Label assignedLabel) throws Exception {

        // *** Request Node ***
        //TODO: store prefix in config and pass in.
        String npLabel = assignedLabel.getName().substring("nodepool-".length());
        NodeRequest request = requestNode(npLabel, assignedLabel.getName());

        // *** Poll request status and wait for fulfillment
        //TODO: store timeout in config
        Integer timeout = 1200 * 1000; //timeout in milliseconds
        Long startTime = System.currentTimeMillis();
        RequestState requestState = RequestState.requested;
        List<NodePoolNode> allocatedNodes = null;
        while (System.currentTimeMillis() < startTime + timeout) {

            try {
                request.updateFromZK();

            } catch (KeeperException e) {
                // connectivity issue with ZK - it should auto-reconnect and we can re-create the request then
                LOGGER.log(Level.WARNING, e.getMessage(), e);
            } catch (Exception e) {
                // let other exceptions bubble through
                throw new RuntimeException(e);
            }

            // node is updated now, check it's state:
            requestState = request.getState();

            LOGGER.log(Level.FINEST, "Current state of request {0} is: {1}", new Object[]{request.getZKID(), requestState});

            if (requestState == RequestState.failed) {
                // TODO switch this logic to re-submit the NodeRequest?
                LOGGER.log(Level.WARNING, "Request {0} failed.", request.getZKID());
                break;
            }

            boolean done = requestState == RequestState.fulfilled;
            if (done) {
                try {
                    // accept here so that if any error conditions occur, the above update logic will automatically re-submit
                    // the node request:
                    allocatedNodes = acceptNodes(request);
                    break;
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            Thread.sleep(5000);
            //TODO: Configurable poll interval for instance ACTIVE
        }
        if (requestState != RequestState.fulfilled || allocatedNodes == null) {
            throw new Exception(MessageFormat.format("Failed to provision node for label {0}", assignedLabel.getName()));
        }

        // *** Get allocated nodes from the request and add to Jenkins
        NodePoolNode node = allocatedNodes.get(0);
        NodePoolSlave nps = new NodePoolSlave(node, getCredentialsId());
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(SlaveComputer.CREATE);
        jenkins.addNode(nps);
        LOGGER.log(Level.INFO, "Added slave to Jenkins: {0}", nps);
    }
}
