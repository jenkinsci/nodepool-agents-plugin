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

import com.rackspace.jenkins_nodepool.models.NodeRequestModel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Label;
import hudson.model.Queue.Task;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;

/**
 * Represents a nodepool node request. Data format is JSON dump of following
 * dict structure: --- node_types: - label1 - label2 requestor: string id (eg
 * hostname) state: string requested|pending|fulfilled|failed state_time: float
 * seconds since epoch
 *
 * @author hughsaunders
 */
public class NodeRequest {

    /**
     * Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(NodeRequest.class.getName());

    /**
     * Request start time.
     */
    private final Long startTime;

    /**
     * Associated Jenkins task that triggered this request
     */
    private final Task task;

    /**
     * The nodepool associated with this task
     */
    private final NodePool nodePool;

    /**
     * The job associated with this task
     */
    private final NodePoolJob nodePoolJob;

    /**
     * A handler for reading and writing the data model to/from Zookeeper
     */
    private final ZooKeeperObject<NodeRequestModel> zkWrapper;

    /**
     * Create new request
     *
     * @param nodePool NodePool cluster to use
     * @param baseId   the base id string for the request - used to generate the full id which will be a combination of the base ID + a dash + a sequential value, such as 100-0001334443
     * @param npj      Associated NodePoolJob which contains the task
     * @throws Exception on ZooKeeper error
     */
    @SuppressFBWarnings
    public NodeRequest(NodePool nodePool, String baseId, NodePoolJob npj) throws Exception {
        this.nodePool = nodePool;
        // Create an instance of the ZK object wrapper for the Node Request Model - path is relative to the ZK connection namespace (typically: /nodepool)
        final Class<NodeRequestModel> modelClazz = NodeRequestModel.class;
        this.zkWrapper = new ZooKeeperObject<>(
                format("/%s/%s-", this.nodePool.getRequestRoot(), baseId),
                baseId, this.nodePool.getConn(), modelClazz);
        LOG.log(FINEST, format("Creating node request with path prefix: %s", zkWrapper.getPath()));

        this.nodePoolJob = npj;
        this.task = npj.getTask();

        // Build up the ZK request model
        final String jenkinsLabel = task.getAssignedLabel().getDisplayName();
        final List<String> nodeTypes = new ArrayList<>();
        nodeTypes.add(nodePool.nodePoolLabelFromJenkinsLabel(jenkinsLabel));
        final NodeRequestModel model = new NodeRequestModel(
                nodeTypes,
                Collections.emptyList(), // declined by
                System.currentTimeMillis() / 1000d,
                true, // enable reuse, without this ready nodes won't be used and a new node will be built.
                this.nodePool.getRequestor(),
                NodePoolState.REQUESTED,
                Collections.emptyList(), // nodes
                jenkinsLabel,
                npj.getBuildId());
        // Save the model to ZK
        final String generatedPath = this.zkWrapper.save(model, CreateMode.EPHEMERAL_SEQUENTIAL);

        LOG.log(FINEST, format("Created new node request, path: %s (generated path: %s), id: %s", getPath(), generatedPath, getZKID()));

        startTime = System.currentTimeMillis();
    }

    /**
     * Returns the requested state value from the data model.
     *
     * @return the requested state value from the data model.
     */
    public NodePoolState getState() {
        try {
            final NodeRequestModel model = zkWrapper.load();
            return model.getState();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'state' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Updates the node request state to the specified value.
     *
     * @param state the state value to set in the node request.
     * @return true if successful, false otherwise
     */
    public boolean updateState(final NodePoolState state) {
        try {
            final NodeRequestModel model = zkWrapper.load();
            model.setState(state);
            zkWrapper.save(model);
            return true;
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while setting ZK node %s 'state' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return false;
        }
    }

    /**
     * Returns the NodePoolJob associated with this request
     *
     * @return NodePoolJob
     */
    public NodePoolJob getJob() {
        return this.nodePoolJob;
    }

    /**
     * Get node names only from the local cache of the ZNode
     *
     * @return list of node names
     */
    public List<String> getAllocatedNodeNames() {
        try {
            final NodeRequestModel model = zkWrapper.load();
            return model.getNodes();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node request %s 'nodes' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return Collections.emptyList();
        }
    }

    /**
     * Get a string representation of this object, in JSON.
     *
     * @return JSON representation of this object, same as is  stored in zookeeper.
     */
    public String toString() {
        return "NodePool Node Request[" + this.getModelAsJSON() + "]";
    }

    /**
     * Get list of NodePool nodes that have been allocated to fulfill this request
     *
     * @return list of nodes
     * @throws Exception on ZooKeeper error
     */
    public List<NodePoolNode> getAllocatedNodes() throws Exception {
        // Example fulfilled request
        // {"nodes": ["0000000000"], "node_types": ["debian"], "state": "fulfilled", "declined_by": [], "state_time": 1520849225.4513698, "reuse": false, "requestor": "NodePool:min-ready"}

        try {
            final NodeRequestModel model = zkWrapper.load();
            if (model.getState() != NodePoolState.FULFILLED) {
                throw new IllegalStateException("Attempt to get allocated nodes from a node request before it has been fulfilled.");
            }
            final List<NodePoolNode> nodeObjects = new ArrayList<>();
            for (String node : model.getNodes()) {
                //this is a list but, there should only be one node as we only ever request one node.
                NodePoolNode npn = new NodePoolNode(nodePool, node, nodePoolJob);
                nodeObjects.add(npn);
                nodePoolJob.setNodePoolNode(npn);

            }
            return nodeObjects;
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'type' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Sets the allocated nodes to the specified value.
     *
     * @param nodes a list of node values as a string
     * @return true if successful, false otherwise
     */
    public boolean setAllocatedNodes(final List<String> nodes) {
        try {
            final NodeRequestModel model = zkWrapper.load();
            model.setNode_types(nodes);
            zkWrapper.save(model);
            return true;
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while updating ZK node %s 'node_types' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return false;
        }
    }

    /**
     * Adds the specified allocated nodes to the existing list of nodes.
     *
     * @param nodes a list of node values as a string
     * @return true if successful, false otherwise
     */
    public boolean addAllocatedNodes(final List<String> nodes) {
        try {
            final NodeRequestModel model = zkWrapper.load();
            model.getNodes().addAll(nodes);
            zkWrapper.save(model);
            return true;
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while updating ZK node %s 'node_types' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return false;
        }
    }

    /**
     * Returns the node pool label for the node request.
     *
     * @return the node pool label
     */
    public String getNodePoolLabel() {
        try {
            final NodeRequestModel model = zkWrapper.load();
            final List<String> labels = model.getNode_types();
            return labels.get(0);
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'type' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Returns the jenkins label for this node request.
     *
     * @return the jenkins label
     */
    public Label getJenkinsLabel() {
        return task.getAssignedLabel();
    }

    /**
     * Returns the age of this request.
     *
     * @return the age of this request.
     */
    public String getAge() {
        final Duration d = Duration.ofMillis(System.currentTimeMillis() - startTime);
        long s = d.getSeconds();
        if (s < 60) {
            return MessageFormat.format("{0}s", d.getSeconds());
        } else {
            return MessageFormat.format("{0}m", d.getSeconds() / 60);
        }
    }

    /**
     * Returns the task associated with this request.
     *
     * @return the task associated with this request
     */
    public Task getTask() {
        return task;
    }

    /**
     * Returns the ID associated with this request.
     *
     * @return the ID associated with this request
     */
    public String getZKID() {
        return zkWrapper.getZKID();
    }

    /**
     * Returns the path associated with this request.
     *
     * @return the path associated with this request
     */
    public String getPath() {
        return zkWrapper.getPath();
    }

    /**
     * Deletes the Node Request.
     */
    public void delete() {
        zkWrapper.delete();
    }

    /**
     * Returns the node request model as JSON.
     *
     * @return the node request model as JSON.
     */
    public String getModelAsJSON() {
        try {
            return zkWrapper.asJSON();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node values from path: %s. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }
}
