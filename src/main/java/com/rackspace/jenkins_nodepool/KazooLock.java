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

import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;


/**
 * Partial Java implementation of the Python module kazoo.recipe.lock
 *
 * @author hughsaunders
 */
public class KazooLock {

    static enum State {
        UNLOCKED, LOCKING, LOCKED
    }

    private static final Logger LOG = Logger.getLogger(KazooLock.class.getName());

    /**
     * Path to the lock ZNode to be created on acquisition
     */
    private final String path;


    private final String node_name = "__lock__";
    private final String prefix;

    /**
     * Lock acquisition timeout value.
     */
    private final Long timeout;

    /**
     * Time measurement unit of lock acquisition timeout value.
     */
    private final TimeUnit unit;

    /**
     * Path to a temporary node to create in order to "queue" with other processes attempting to acquire the same lock.
     * <p>
     * The actual node name will have a sequence number appended by
     * ZooKeeper. The full node path is stored in self.node
     */
    private final String create_path;

    /**
     * Path to actual ZNodecreated with the create_path.
     */
    private String node;

    /**
     * A number indicating the current process's "place in line" waiting to acquire the lock
     */
    private Integer sequence;

    /**
     * Lock status value
     */
    private State state = State.UNLOCKED;

    /**
     * Reference to information about the NodePool/ZooKeeper cluster being used.
     */
    private NodePool nodePool;

    /**
     * Create a new lock object.
     *
     * @param path  the path to the lock ZNode that will be created upon acquiring the lock
     * @param nodePool  node pool object containing ZooKeeper connection information
     */
    public KazooLock(String path, NodePool nodePool) {
        this(path, 600, TimeUnit.SECONDS, nodePool);
    }

    /**
     * Create a new lock object.
     *
     * @param path  the path to the lock ZNode that will be created upon acquiring the lock
     * @param timeout  time to wait until lock acquisition is deemed a failure
     * @param unit  unit of time for the timeout
     * @param nodePool  node pool object containing ZooKeeper connection information
     */
    public KazooLock(String path, long timeout, TimeUnit unit, NodePool nodePool) {
        this.nodePool = nodePool;
        this.path = path;
        this.timeout = timeout;
        this.unit = unit;
        this.prefix = UUID.randomUUID().toString() + node_name; //type 4
        this.create_path = this.path + "/" + this.prefix;
    }

    private static class KazooLockWatcher<T extends WatchedEvent>
            extends LinkedBlockingQueue<T> implements CuratorWatcher {

        @Override
        public void process(WatchedEvent we) throws Exception {
            add((T)we);
        }
    }

    /**
     * Given the path to the temporary ZNode created to queue for locking, extract the number at the end of the
     * path.  This represents a sequence number for queueing for the lock.
     *
     * @param path  path to temporary lock acquisition ZNode
     * @return integer sequence number
     * @throws KazooLockException  if there is an issue with the node path not conforming to the expected format
     */
    static Integer sequenceNumberForPath(String path) throws KazooLockException {
        Pattern p = Pattern.compile("_([0-9]+)$");
        Matcher m = p.matcher(path);
        boolean matches = m.find();
        if (matches){
            return Integer.valueOf(m.group(1));
        } else {
            throw new KazooLockException("Found non sequential node: "+path);
        }
    }

    /**
     * Poll for the given contender ZNode to disappear
     *
     * @param path  path to the lock contender ZNode
     * @throws Exception  on lock acquisition timeout or ZooKeeper error
     */
    private void waitForNodeRemoval(String path) throws Exception {
        final KazooLockWatcher klw = new KazooLockWatcher();
        while (nodePool.getConn().checkExists().usingWatcher(klw).forPath(path) != null) {
            final WatchedEvent we = (WatchedEvent)klw.poll(timeout, unit);
            if (we == null){
                throw new KazooLockException("Timeout Acquiring Lock for node: "+this.path);
            }
        }
    }

    /**
     * Acquire the lock for the current process
     *
     * @throws Exception if any ZooKeeper error occurs
     */
    public void acquire() throws Exception {
        state = State.LOCKING;
        LOG.log(Level.FINEST, "KazooLock.acquire");
        final byte[] requestor = nodePool.getRequestor().getBytes(nodePool.getCharset());
        // 1. Ensure path to be locked exists
        try {
            nodePool.getConn().create()
                    .creatingParentsIfNeeded()
                    .forPath(path, requestor);
        } catch (NodeExistsException ex) {
            // node already exists, nothing to do.
        }

        // 2. Create create path and determine our sequence
        node = nodePool.getConn().create()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(create_path, requestor);
        LOG.log(Level.FINEST, "Lock contender created:" + node);
        sequence = sequenceNumberForPath(node);

        // 3. Wait for any child nodes with lower seq numbers
        final List<String> contenders = nodePool.getConn().getChildren().forPath(path);
        for (final String contender : contenders){
            LOG.log(Level.FINEST, "Found contender for lock:{0}", contender);
            final Integer contenderSequence = sequenceNumberForPath(contender);
            if (contenderSequence < sequence){
                // This contender is ahead of us in the queue,
                // watch and wait
                waitForNodeRemoval(path+"/"+contender);
                /**
                 * Waiting for node removal may take a long time
                 * during which other contenders may be added to
                 * the list. However any contenders added during
                 * our wait will have a higher sequence number
                 * than us, so must wait for us before acquiring.
                 */
            }
        }
        LOG.log(Level.FINE, "Lock Acquired {0}", path);
        state = State.LOCKED;

    }

    /**
     * Release the lock
     *
     * @throws Exception on ZooKeeper error or lock state error.
     */
    public void release() throws Exception {
        LOG.log(Level.FINEST, "Releasing Lock {0}", path);
        if (state != State.LOCKED) {
            throw new IllegalStateException(MessageFormat.format("Cannot unlock from state: {0}, Path: {1}", state, node));
        }
        if (node == null) {
            throw new IllegalStateException(MessageFormat.format("Trying to unlock before lock has been locked. State:{0}, Path:{1}", state, node));
        }
        nodePool.getConn().delete().forPath(node);
        state = State.UNLOCKED;
        LOG.log(Level.FINE, "Released Lock {0}", path);
    }

    KazooLock.State getState() {
        return state;
    }

}
