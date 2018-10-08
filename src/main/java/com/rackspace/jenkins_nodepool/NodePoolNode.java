package com.rackspace.jenkins_nodepool;

import static java.lang.String.format;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Representation of a node from NodePool (not necessarily a Jenkins slave)
 */

public class NodePoolNode extends ZooKeeperObject {

    /**
     * Logger for this class.
     */
    private static final Logger LOG = java.util.logging.Logger.getLogger(NodePoolNode.class.getName());

    /**
     * The lock on the node ZNode
     */
    final KazooLock lock;

    /**
     * The job this node was requested for
     */
    final NodePoolJob nodePoolJob;

    /**
     * Creates a new Zookeeper node for the node pool.
     *
     * @param nodePool NodePool
     * @param id       id of node as represented in ZooKeeper
     * @param npj      NodePoolJob object representing the build that this node was requested for.
     * @throws Exception on ZooKeeper error
     */
    public NodePoolNode(NodePool nodePool, String id, NodePoolJob npj) throws Exception {
        super(nodePool);
        this.nodePoolJob = npj;
        super.setPath(format("/%s/%s", nodePool.getNodeRoot(), id));
        super.setZKID(id);
        super.updateFromZK();
        data.put("build_id", npj.getBuildId());
        super.writeToZK();
        this.lock = new KazooLock(getLockPath(), nodePool, npj);

    }

    /**
     * Get the NodePoolJob associated with this node
     * @return NodePoolJob object
     */
    public NodePoolJob getJob(){
        return this.nodePoolJob;
    }

    /**
     * Returns a list of NodePool type labels.
     *
     * @return an array of NodePool labels
     */
    public List<String> getNPTypes() {
        final Object o = data.get("type");
        // A little ugly here and overly cautious, but this is the safe way to convert a generic object from a remote
        // data store to a collection of specific types.
        if (o instanceof ArrayList) {
            // Safe to cast now
            final ArrayList list = (ArrayList) o;
            // A list to hold the values - we know the size now
            final List<String> response = new ArrayList<>(list.size());
            // For each element in the list...
            for (Object o1 : list) {
                // Is the element a string? Should be...
                if (o1 instanceof String) {
                    // Safe to cast and add to our list
                    response.add((String) o1);
                } else {
                    LOG.log(Level.WARNING, format("Unable to cast list data to a string value!  Type is: %s",
                            o1.getClass().getTypeName()));
                    return new ArrayList<>();
                }
            }

            return response;
        } else {
            LOG.log(Level.WARNING, format("Unable to cast data field 'type' to an ArrayList!  Type is: %s",
                    o.getClass().getTypeName()));
            return new ArrayList<>();
        }
    }

    /**
     * Get the name of the provider that provisioned this node
     *
     * @return String name of the provider
     */
    public String getProvider() {
        return (String) data.get("provider");
    }

    /**
     * Get path to lock object used to lock this node
     *
     * @return lock path
     */
    final String getLockPath() {
        return format("/%s/%s/lock", nodePool.getNodeRoot(), zKID);
    }

    /**
     * Get Jenkins's version of the node label
     *
     * @return Jenkins label
     */
    public String getJenkinsLabel() {
        if (getNPTypes().isEmpty()) {
            LOG.log(Level.WARNING, "Unable to return a proper Jenkins Label - NP type list is empty.");
            return format("%s", nodePool.getLabelPrefix());
        } else {
            return format("%s%s", nodePool.getLabelPrefix(), getNPTypes().get(0));
        }
    }

    public String getName() {
        return format("%s-%s", getJenkinsLabel(), getZKID());
    }

    /**
     * Used to render node editing page in the UI
     *
     * @return node pool object
     */
    public NodePool getNodePool() {
        return nodePool;
    }

    /**
     * Returns the host.
     *
     * @return the host
     */
    public String getHost() {
        return (String) data.get("interface_ip");
    }

    /**
     * Returns the connection port.
     *
     * @return the connection port
     */
    public Integer getPort() {
        Double port = (Double) data.get("connection_port");
        if (port == null) {
            // fall back to the field name used on older NodePool clusters.
            port = (Double) data.getOrDefault("ssh_port", 22.0);
        }
        return port.intValue();
    }

    /**
     * Returns the first host key.
     *
     * @return the first host key
     */
    public String getHostKey() {
        List<String> hostKeys = (List) data.get("host_keys");
        return hostKeys.get(0);
    }

    /**
     * Returns the host keys.
     *
     * @return the host keys
     */
    public List<String> getHostKeys() {
        return (List) data.get("host_keys");
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
     * @throws Exception on ZooKeeper error
     */
    private void setState(NodePoolState state) throws Exception {
        setState(state, true);
    }

    /**
     * Update the state of the node according to  NodePool
     *
     * @param state NodePool node state
     * @param write if true, save updates back to ZooKeeper
     * @throws Exception on ZooKeeper error
     */
    private void setState(NodePoolState state, boolean write) throws Exception {
        // get up to date info, so we are less likely to lose data
        // when writing back.
        updateFromZK();
        data.put("state", state.getStateString());

        if (write) {
            writeToZK();
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
        setState(NodePoolState.HOLD, false);
        data.put("comment", "Jenkins hold");
        data.put("hold_job", jobIdentifier);
        writeToZK();

        unlock(); // imitate zuul and unlock here.
    }

    /**
     * Returns the hold until time (in milliseconds since epoch) for this Node.
     *
     * @return the hold until time value for this Node.
     * @throws Exception on ZK read error
     */
    public Long getHoldUntil() throws Exception {
        return (Long) data.get("hold_until");
    }

    /**
     * Sets the node hold until time (ms since epoch).
     *
     * @param holdUntilTimeEpochMillis the hold until time in milliseconds since epoch
     * @throws Exception on ZK write error
     */
    public void setHoldUntil(Long holdUntilTimeEpochMillis) throws Exception {
        data.put("hold_until", holdUntilTimeEpochMillis);
        writeToZK();
    }

    /**
     * Removes the node hold until time.
     *
     * @throws Exception on ZK write error
     */
    public void removeHoldUntil() throws Exception {
        data.remove("hold_until");
        writeToZK();
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
