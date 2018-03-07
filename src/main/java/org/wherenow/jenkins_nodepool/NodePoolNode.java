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

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author hughsaunders
 */
public class NodePoolNode extends Slave implements Future<Node>{

    private static final Logger LOGGER = Logger.getLogger(NodePoolNode.class.getName());

    private NodePoolClient client;
    NodeRequest request;


    public NodePoolNode(NodePoolClient client, NodeRequest request) throws IOException, Descriptor.FormException {

        super(
                request.getNodePoolID(), // name
                "/var/lib/jenkins",  // TODO this should be the path to the root of the workspace on the slave
                null // TODO ComputerLauncher instance goes here
        );
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

        LOGGER.log(Level.INFO, "isDone() polling to see if node is ready");

        // NOTE: the node request should be getting asynchronously updated via a curator watcher attached to it,
        // so we can just test if the state is fulfilled.
        final State requestState = request.getState();

        LOGGER.log(Level.INFO, "Current state of request " + request.getNodePoolID() + " is: " + requestState);

        if (requestState == State.failed) {
            // TODO switch this logic to re-submit the NodeRequest?
            LOGGER.log(Level.WARNING, "Request " + request.getNodePoolID() + " failed.");
        }

        if (requestState != State.requested) {
            LOGGER.log(Level.INFO, "Current state is now: " + requestState);
        }

        return requestState == State.fulfilled;
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
     * Called once a NodeRequest has been "fulfilled", meaning there is now a Node up and waiting for us to use.
     *
     * In Zuul terms, we now "accept" the node.
     * @return Node
     */
    private Node getNode() throws ExecutionException {
        try {
            LOGGER.log(Level.INFO, "Nodes to accept:" + request.get("nodes"));
            final List<String> nodes = client.acceptNodes(request);
            LOGGER.log(Level.INFO, "Accepted nodes: " + nodes);

            // ok we know the identity of the nodes to use
            // TODO do whatever stuff neeeds to happen to actually provision a Node now.
            return this;
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }
}
