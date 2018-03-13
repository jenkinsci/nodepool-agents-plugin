package com.rackspace.jenkins_nodepool;

/**
 * Representation of a node from NodePool (not necessarily a Jenkins slave)
 */
public class NodePoolNode {

    private final String name; // name according to nodepool
    private final KazooLock lock;

    public NodePoolNode(String name, KazooLock lock) {
        this.name = name;
        this.lock = lock;
    }

    public KazooLock getLock() {
        return lock;
    }

    public String getName() {
        return name;
    }
}
