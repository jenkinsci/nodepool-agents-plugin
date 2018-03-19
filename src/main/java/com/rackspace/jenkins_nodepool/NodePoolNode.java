package com.rackspace.jenkins_nodepool;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import java.text.MessageFormat;
import java.util.List;


/**
 * Representation of a node from NodePool (not necessarily a Jenkins slave)
 */
public class NodePoolNode extends ZooKeeperObject {

    private final KazooLock lock;
    private final NodePool nodePool;
    @XStreamOmitField
    private NodePoolComputer computer;

    public NodePoolNode(NodePool nodePool, String id) throws Exception {
        super(nodePool);
        this.nodePool = nodePool;
        super.setPath(MessageFormat.format("/{0}/{1}",
                new Object[]{nodePool.getNodeRoot(), id}));
        super.setZKID(id);
        super.updateFromZK();
        this.lock = new KazooLock(getLockPath(), nodePool);
    }

    public String getNPType() {
        return (String) data.get("type");
    }

    final String getLockPath() {
        return MessageFormat.format("/{0}/{1}/lock",
                new Object[]{nodePool.getNodeRoot(), zKID});
    }

    public String getJenkinsLabel() {
        return MessageFormat.format("{0}{1}",
                new Object[]{nodePool.getLabelPrefix(), getNPType()});
    }

    public String getName() {
        return MessageFormat.format("{0}-{1}", getJenkinsLabel(), getZKID());
    }

    public String getHost() {
        return (String) data.get("interface_ip");
    }

    public Integer getPort() {
        return ((Double) data.get("connection_port")).intValue();
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

    public void release() throws Exception {
        setState("used");
        lock.release();
        nodePool.removeComputer(computer);
    }

    void setComputer(NodePoolComputer npc) {
        computer = npc;
    }

    public NodePoolComputer getComputer() {
        return computer;
    }

}
