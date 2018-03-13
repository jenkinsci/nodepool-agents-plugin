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

import hudson.model.Descriptor;
import hudson.model.Node;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.zookeeper.KeeperException;

/**
 *
 * @author hughsaunders
 */
public class NodePoolSlaveFuture implements Future<Node> {

    private static final Logger LOGGER = Logger.getLogger(NodePoolSlaveFuture.class.getName());

    private NodePoolClient client;
    NodeRequest request;

    public NodePoolSlaveFuture(NodePoolClient client, NodeRequest request) throws IOException, Descriptor.FormException {
        this.client = client;
        this.request = request;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // TODO release node & delete noderequest if it exists?
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isCancelled() {

        // TODO confirm node & noderequest are released/deleted
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isDone() {

        try {
            updateNodeRequestFromZK();

        } catch (KeeperException e) {
            // connectivity issue with ZK - it should auto-reconnect and we can re-create the request then
            LOGGER.log(Level.WARNING, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            // let other exceptions bubble through
            throw new RuntimeException(e);
        }

        // node is updated now, check it's state:
        final State requestState = request.getState();

        LOGGER.log(Level.INFO, "Current state of request " + request.getNodePoolID() + " is: " + requestState);

        if (requestState == State.failed) {
            // TODO switch this logic to re-submit the NodeRequest?
            LOGGER.log(Level.WARNING, "Request " + request.getNodePoolID() + " failed.");
        }

        if (requestState != State.requested) {
            LOGGER.log(Level.INFO, "Current state is now: " + requestState);
        }

        boolean done = requestState == State.fulfilled;
        if (done) {
            // accept here so that if any error conditions occur, the above update logic will automatically re-submit
            // the node request:
            LOGGER.log(Level.INFO, "Nodes to accept:" + request.get("nodes"));
            done = client.acceptNodes(request) != null;
        }
        return done;
    }

    private void updateNodeRequestFromZK() throws Exception {
        Map data = null;

        final boolean exists = client.nodeExists(request.getNodePath());

        if (exists) {
            // refresh request from ZK:
            data = client.getZNode(request.getNodePath());

        } else {
            // the request node is ephemeral, so we probably just re-connected to ZK.
            LOGGER.log(Level.INFO, "Node request " + request.getNodePath() + " no longer exists.  Submitting " +
                    "new request...");

            final NodeRequest newRequest = client.requestNode(
                    request.getNodePoolLabel(),
                    request.getJenkinsLabel()
            );
            request = newRequest;
        }

        request.updateFromMap(data);

    }

    @Override
    public Node get() throws InterruptedException, ExecutionException {
        return getNode();
    }

    @Override
    public Node get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // TODO do timeout stuff the interface wants.
        return getNode();
    }

    /**
     * Called once a NodeRequest has been "fulfilled", meaning there is now a
     * Node up and waiting for us to use.
     *
     * In Zuul terms, we now "accept" the node.
     *
     * @return Node
     */
    private Node getNode() throws ExecutionException {
        try {

            // ok we know the identity of the nodes to use
            // TODO do whatever stuff neeeds to happen to actually provision a Node now.
            // TODO: Get ip/host for node.
            // I suspect that this is in the zknode for the node, not the
            // zknode for the request.
            // TODO: Replace all uses of /nodes with client.getNodeRoot()
            // ZKNode that represents the newly created node.
            Map<String, String> nodeZKNodes = request.getAllocatedNodes();

            for (String node : nodeZKNodes.keySet()) {
                String type = nodeZKNodes.get(node);
                Map<String, Object> nodeData = client.getZNode("/nodes/" + node);
                String host = (String) nodeData.get("interface_ip");
                Integer port = ((Double) nodeData.get("connection_port")).intValue();
                List<String> hostKeys = (List) nodeData.get("host_keys");
                String hostKey = hostKeys.get(0);
                String credentialsId = client.getCredentialsId();

                String jenkinsLabel = (String) request.get("jenkins_label");
                // TODO: Delete node request
                LOGGER.log(Level.INFO, MessageFormat.format("Creating NodePoolSlave: Host:{0}, Port:{1}, Host Key:{2}, Creds Id:{3}, Jenkins Label: {4}", host, port, hostKey, credentialsId, jenkinsLabel));

                return new NodePoolSlave(MessageFormat.format("{0}-{1}", jenkinsLabel, request.getNodePoolID()), host, port,
                        hostKey, credentialsId, jenkinsLabel);
            }

            // TODO: Figure out how/if multiple node requests work.
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
        return null;
    }
}
