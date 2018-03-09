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
package org.wherenow.jenkins_nodepool;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.model.Node;
import java.util.HashMap;
import java.util.Map;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

/**
 * Notes on what needs to be implemented form the Jenkins side.. Cloud:
 * provision --> NodeProvisioner.PlannedNode Slave: createComputer (Pass in
 * enough information about node to free it, must be serialisable so it can be
 * stored in global config incase a user manually reconfigures the node)
 * Computer onRemoved: Release the node here. RetentionStrategy
 * (CloudRetentionStrategy is based on idle minutes which doesn't work for our
 * use case as we want to ensure that each node is only used for one job ) Maybe
 * use cloudretention strategy and a runlistener to listen to job completion
 * events and offline the slave so it isn't reused before its removed.
 *
 */
/**
 *
 * Single use node life cycle: request wait for provisioning accept lock use
 * return unlock
 *
 * lock (kazoo.recipe.lock) noderoot/nodeid/lock
 *
 * Zuul hierachy NodeRequest Job nodeset node
 *
 *
 * @author hughsaunders
 */
import com.google.gson.Gson;
import java.nio.charset.Charset;
public class NodePoolClient {

    private static final Logger LOGGER = Logger.getLogger(NodePoolClient.class.getName());

    private CuratorFramework conn;

    // these roots are relative to /nodepool which is the namespace
    // set on the curator framework connection
    private String requestRoot = "requests";
    private String requestLockRoot = "requests-lock";
    private String nodeRoot = "nodes";
    private Integer priority;
    
    private static final Gson gson = new Gson();
    private static final Charset charset = Charset.forName("UTF-8");

    public NodePoolClient(String connectionString) {
        this(connectionString, 100);
    }

    public NodePoolClient(String connectionString, Integer priority) {
        this(ZooKeeperClient.createConnection(connectionString), priority);
    }

    public NodePoolClient(CuratorFramework conn) {
        this(conn, 100);
    }

    public NodePoolClient(ZooKeeperClient zkc, Integer priority) {
        this(zkc.getConnection(), priority);
    }

    // all constructors lead here
    public NodePoolClient(CuratorFramework conn, Integer priority) {
        this.conn = conn;
        this.requestRoot = requestRoot;
        this.priority = priority;

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

    public Future<NodePoolNode> request() {
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

    // TODO: similar with nodeSet for multiple nodes.
    // or just create multiple requests?
    public NodeRequest requestNode(String label) throws Exception {
        final NodeRequest request = new NodeRequest(conn, label);
        final String createPath = MessageFormat.format("/{0}/{1}-", this.requestRoot, priority.toString());
        LOGGER.info(MessageFormat.format("Creating request node: {0}", createPath));
        String requestPath = conn.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(createPath, request.toString().getBytes());

        // get NodePool request id
        final String id = NodePoolClient.idForPath(requestPath);
        request.setNodePoolID(id);
        request.setNodePath(requestPath);

        // set watch so the request gets updated
        conn.getData().usingWatcher(request).forPath(requestPath);

        return request;

    }
    
    /**
     * Get data for a node
     * @param nodeName the name of the node to query usually priority-id format.
     * @return Map representing the json data stored on the node.
     * @throws Exception 
     */
    public Map<String, Object> getNode(String nodeName) throws Exception{
        String nodePath = MessageFormat.format("/{0}/{1}", this.nodeRoot, nodeName);
        byte[] jsonBytes = conn.getData().forPath(nodePath);
        String jsonString = new String(jsonBytes, charset);
        final Map data = gson.fromJson(jsonString, HashMap.class);
        return data;
    }

    /**
     * Accept the node that was created to satisfy the given request.
     *
     * @return node name as a String
     */
    public List<String> acceptNodes(NodeRequest request) throws Exception {

        // refer to the request "nodeset" to know which nodes to lock.
        final List<String> nodes = (List<String>)request.get("nodes");
        final List<String> acceptedNodes = new ArrayList<String>();

        for (String node : nodes) {
            LOGGER.log(Level.INFO, "Accepting node " + node + " on behalf of request " + request.getNodePoolID());

            final String nodePath = "/nodepool/nodes/" + node;
            final KazooLock lock = new KazooLock(conn, nodePath);
            lock.acquire();  // TODO debug making sure this lock stuff actually works

            // TODO get details about the node from ZK?
            acceptedNodes.add(node);

            // TODO delete node request now that we've got the node locked.
        }

        return acceptedNodes;
    }
}
