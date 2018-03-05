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

import hudson.model.Node;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
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
public class NodePoolClient {

    private static final Logger LOG = Logger.getLogger(NodePoolClient.class.getName());

    private CuratorFramework conn;

    // these roots are relative to /nodepool which is the namespace
    // set on the curator framework connection
    private String requestRoot = "requests";
    private String requestLockRoot = "requests-lock";
    private String nodeRoot = "nodes";
    private Integer priority;

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
        NodeRequest request = new NodeRequest(conn, label);
        String createPath = MessageFormat.format("/{0}/{1}-", this.requestRoot, priority.toString());
        LOG.info(MessageFormat.format("Creating request node: {0}", createPath));
        String requestPath = conn.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(createPath, request.toString().getBytes());

        // get nodepool request id
        String id = NodePoolClient.idForPath(requestPath);
        request.setNodePoolID(id);
        request.setNodePath(requestPath);

        // set watch so the request gets updated
        conn.getData().usingWatcher(request).forPath(requestPath);

        return request;

    }

//    	public NodeRequest requestNode(Integer priority, byte[] data) throws Exception{
//		String path = "{0}/{1}-".format(this.requestRoot, priority.toString());
//		path = conn.create()
//			.withProtection()
//			.withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
//			.forPath(path, data);
//		//TODO:create proper constructor for node request and pass it some useful information
//		return new NodeRequest("testlabel");
//	}
    public void provisionNode(String label) {
        //
    }

}
