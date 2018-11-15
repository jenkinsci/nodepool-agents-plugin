package com.rackspace.jenkins_nodepool;

import com.rackspace.jenkins_nodepool.models.NodeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;


/**
 * Representation of a node from NodePool (not necessarily a Jenkins slave)
 */

public class NodePoolNode {

    /**
     * The default connection port
     */
    private static final Integer DEFAULT_CONNECTION_PORT = 22;

    /**
     * Logger for this class.
     */
    private static final Logger LOG = Logger.getLogger(NodePoolNode.class.getName());

    /**
     * The lock on the node ZNode
     */
    final KazooLock lock;

    /**
     * The job this node was requested for
     */
    private final NodePoolJob nodePoolJob;

    /**
     * A handler for reading and writing the data model to/from Zookeeper
     */
    private final ZooKeeperObject<NodeModel> zkWrapper;

    private final String labelPrefix;

    /**
     * Creates a new Zookeeper node for the node pool.
     *
     * @param nodePool NodePool
     * @param id       id of node as represented in ZooKeeper
     * @param npj      NodePoolJob object representing the build that this node was requested for.
     * @throws ZookeeperException on ZooKeeper error
     */
    public NodePoolNode(NodePool nodePool, String id, NodePoolJob npj) throws ZookeeperException {
        this.nodePoolJob = npj;
        this.labelPrefix = nodePool.getLabelPrefix();

        // Create an instance of the ZK object wrapper - path is relative to the ZK connection namespace (typically: /nodepool)
        String path = format("/%s/%s", nodePool.getNodeRoot(), id);
        final Class<NodeModel> modelClazz = NodeModel.class;
        LOG.log(FINE, format("Creating ZK wrapper object of type: %s for path: %s", modelClazz, path));
        this.zkWrapper = new ZooKeeperObject<>(path, id, nodePool.getConn(), modelClazz);

        // Update the Build ID and save it back - use our wrapper to do the heavy lifting
        final NodeModel model = this.zkWrapper.load();
        model.setBuild_id(npj.getBuildId());
        this.zkWrapper.save(model);

        this.lock = new KazooLock(getLockPath(), nodePool, npj);
    }

    /**
     * Get the NodePoolJob associated with this node
     *
     * @return NodePoolJob object
     */
    public NodePoolJob getJob() {
        return this.nodePoolJob;
    }

    /**
     * Returns a list of NodePool type labels.
     *
     * @return an array of NodePool labels
     */
    public List<String> getNPTypes() {
        try {
            final NodeModel model = zkWrapper.load();
            return model.getType();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'type' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Get the name of the provider that provisioned this node
     *
     * @return String name of the provider, or null if not set
     */
    public String getProvider() {
        try {
            final NodeModel model = zkWrapper.load();
            return model.getProvider();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'provider' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Get path to lock object used to lock this node
     *
     * @return lock path
     */
    final String getLockPath() {
        return format("%s/lock", zkWrapper.getPath());
    }

    /**
     * Get Jenkins's version of the node label
     *
     * @return Jenkins label
     */
    public String getJenkinsLabel() {
        if (getNPTypes().isEmpty()) {
            LOG.log(WARNING, "Unable to return a proper Jenkins Label - NP type list is empty.");
            return format("%s", labelPrefix);
        } else {
            return format("%s%s", labelPrefix, getNPTypes().get(0));
        }
    }

    /**
     * Returns the name for this node.
     *
     * @return the name for this node
     */
    public String getName() {
        return format("%s-%s", getJenkinsLabel(), zkWrapper.getZKID());
    }

    /**
     * Returns the host or interface IP for the node.
     *
     * @return the host / interface IP or null if not set
     */
    public String getHost() {
        try {
            final NodeModel model = zkWrapper.load();
            return model.getInterface_ip();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'interface_ip' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Returns the connection port.
     *
     * @return the connection port
     */
    public Integer getPort() {
        try {
            final NodeModel model = zkWrapper.load();
            Integer connectionPort = model.getConnection_port();
            if (connectionPort == null) {
                // fall back to the SSH port field on older NodePool clusters.
                connectionPort = model.getSsh_port();
                if (connectionPort == null) {
                    return DEFAULT_CONNECTION_PORT;
                } else {
                    return connectionPort;
                }
            } else {
                return connectionPort;
            }
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'connection_port' or 'ssh_port' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Returns the first host key.
     *
     * @return the first host key
     */
    public String getHostKey() {
        try {
            final NodeModel model = zkWrapper.load();
            final List<String> hostKeys = model.getHost_keys();
            if (hostKeys.isEmpty()) {
                return null;
            } else {
                return hostKeys.get(0);
            }
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'host_keys' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Returns the host keys.
     *
     * @return the host keys
     */
    public List<String> getHostKeys() {
        try {
            final NodeModel model = zkWrapper.load();
            return model.getHost_keys();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'host_keys' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Returns the string representation for this object.
     *
     * @return the string representation for this object.
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Update the state of the node according to NodePool
     *
     * @param state NodePool node state
     */
    private void setState(NodePoolState state) {
        try {
            final NodeModel model = zkWrapper.load();
            model.setState(state);
            zkWrapper.save(model);
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading/writing ZK node %s 'state' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
        }
    }

    /**
     * Mark the node as being held.  A held node must be manually deleted from NodePool.
     *
     * @param jobIdentifier identifier of build/job that this node was running.
     * @throws Exception on ZK error
     */
    public void hold(String jobIdentifier) throws Exception {
        // Lock should already be held, we only hold nodes that have already been assigned to Jenkins.
        try {
            final NodeModel model = zkWrapper.load();
            model.setState(NodePoolState.HOLD);
            model.setComment("Jenkins hold");
            model.setHold_job(jobIdentifier);
            zkWrapper.save(model);
            unlock(); // imitate zuul and unlock here.
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading/writing ZK node %s 'state' and 'hold' related fields. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
        }
    }

    /**
     * Returns the hold until time (in milliseconds since epoch) for this Node.
     *
     * @return the hold until time value for this Node.
     */
    public Long getHoldUntil() {
        try {
            final NodeModel model = zkWrapper.load();
            // TODO: DAD - Reivew - model doesn't have hold_until key // return (Long) data.get("hold_until");
            return model.getHold_expiration();
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading ZK node %s 'hold_expiration' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
            return null;
        }
    }

    /**
     * Sets the node hold until time (ms since epoch).
     *
     * @param holdUntilTimeEpochMillis the hold until time in milliseconds since epoch
     */
    public void setHoldUntil(Long holdUntilTimeEpochMillis) {
        try {
            final NodeModel model = zkWrapper.load();
            // TODO: DAD - Reivew - model doesn't have hold_until key // data.put("hold_until", holdUntilTimeEpochMillis);
            model.setHold_expiration(holdUntilTimeEpochMillis);
            zkWrapper.save(model);
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading/writing ZK node %s 'hold_expiration' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
        }
    }

    /**
     * Removes the node hold until time.
     */
    public void removeHoldUntil() {
        try {
            final NodeModel model = zkWrapper.load();
            // TODO: DAD - Reivew - model doesn't have hold_until key // data.remove("hold_until");
            model.setHold_expiration(0L);
            zkWrapper.save(model);
        } catch (ZookeeperException e) {
            LOG.log(WARNING, format("%s occurred while reading/writing ZK node %s 'hold_expiration' field. Message: %s",
                    e.getClass().getSimpleName(), zkWrapper.getPath(), e.getLocalizedMessage()));
        }
    }

    /**
     * Sets the NodePool state to IN USE.
     *
     * @throws Exception if an error occurs while setting the state
     */
    public void setInUse() throws Exception {
        lock.acquire();
        setState(NodePoolState.IN_USE);
    }

    /**
     * Mark the node as being used and release it.  It will be destroyed by NodePool.
     *
     * @throws Exception on ZooKeeper error
     */
    public void release() throws Exception {
        setState(NodePoolState.USED);

        if (lock.getState() == KazooLock.State.LOCKED) {
            unlock();
        }
    }

    /**
     * Release the client's lock on the node.
     */
    private void unlock() throws Exception {
        lock.release();
    }
}
