package com.rackspace.jenkins_nodepool;

import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Representation of a node from NodePool (not necessarily a Jenkins slave)
 *
 */

public class NodePoolNode extends ZooKeeperObject {

    private static final Logger LOG = Logger.getLogger(NodePoolNode.class.getName());

    /**
     * The lock on the node ZNode
     */
    final KazooLock lock;

    /**
     * @param nodePool  NodePool
     * @param id  id of node as represented in ZooKeeper
     * @throws Exception on ZooKeeper error
     */
    public NodePoolNode(NodePool nodePool, String id) throws Exception {
        super(nodePool);
        super.setPath(MessageFormat.format("/{0}/{1}",
                new Object[]{nodePool.getNodeRoot(), id}));
        super.setZKID(id);
        super.updateFromZK();
        this.lock = new KazooLock(getLockPath(), nodePool);
    }

    /**
     * Get labeled type of node, according to NodePool
     *
     * @return NodePool label
     */
    public String getNPType() {
        return (String) data.get("type");
    }

    /**
     * Get path to lock object used to lock this node
     *
     * @return lock path
     */
    final String getLockPath() {
        return MessageFormat.format("/{0}/{1}/lock",
                new Object[]{nodePool.getNodeRoot(), zKID});
    }

    /**
     * Get Jenkins's version of the node label
     *
     * @return Jenkins label
     */
    public String getJenkinsLabel() {
        return MessageFormat.format("{0}{1}",
                new Object[]{nodePool.getLabelPrefix(), getNPType()});
    }

    public String getName() {
        return MessageFormat.format("{0}-{1}", getJenkinsLabel(), getZKID());
    }

    public String getHost() {
        String key = nodePool.getIpVersion();
        String host = (String) data.get(key);
        if (host == null) {
            LOG.log(Level.WARNING, "Requested IP family {0} not available for node {1}.", new Object[]{key, this.toString()});
            if (!key.equals("interface_ip")) {
                host = (String) data.get("interface_ip");
                LOG.log(Level.WARNING, "Falling back to interface_ip:{0}", host);
            }
        }
        if (host == null) {
            throw new IllegalStateException(MessageFormat.format("Failed to find an IP address to connect to NodePool Node {0}", this));
        }
        return host;
    }

    public Integer getPort() {
        Double port = (Double)data.get("connection_port");
        if (port == null) {
            // fall back to the field name used on older NodePool clusters.
            port = (Double)data.getOrDefault("ssh_port", 22.0);
        }
        return port.intValue();
    }

    public String getHostKey() {
        List<String> hostKeys = (List) data.get("host_keys");
        return hostKeys.get(0);
    }

    public List<String> getHostKeys() {
        return (List) data.get("host_keys");
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Update the state of the node according to  NodePool
     *
     * @param state  NodePool node state
     * @throws Exception on ZooKeeper error
     */
    private void setState(String state) throws Exception {
        // get up to date info, so we are less likely to lose data
        // when writing back.
        updateFromZK();
        data.put("state", state);
        writeToZK();
    }

    public void setInUse() throws Exception {
        setState("in-use");
        lock.acquire();
    }

    /**
     * Mark the node as being used and release it.  It will be destroyed by NodePool.
     *
     * @throws Exception on ZooKeeper error
     */
    public void release() throws Exception {
        setState("used");
        lock.release();
    }

}
