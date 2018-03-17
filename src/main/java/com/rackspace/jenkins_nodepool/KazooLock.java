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
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;


/**
 * Partial Java implementation of the python module kazoo.recipe.lock
 * @author hughsaunders
 */
public class KazooLock {

    private enum State {
        UNLOCKED, LOCKING, LOCKED
    }

    private static final Logger LOG = Logger.getLogger(KazooLock.class.getName());

    private String connectionString;
    // Path is the path to the node to be locked
    private final String path;
    private final String identifier;
    private final String node_name = "__lock__";
    private final String prefix;
    private final Long timeout;
    private final TimeUnit unit;
    /** create_path is the path we create to represent our lock or place in the
     * queue. The actual node name will have a sequence number appended by
     * zookeeper. The full node path is stored in self.node
     */
    private final String create_path;
    //this is set to create_path+index number, when the node is created
    private String node;
    private Integer sequence;
    private State state = State.UNLOCKED;
    private NodePoolGlobalConfiguration config;

    public KazooLock(String connectionString, String path) {
        this(connectionString, path, 5, TimeUnit.SECONDS);
    }

    public KazooLock(String connectionString,
            String path, long timeout, TimeUnit unit) {
        this.config = NodePoolGlobalConfiguration.getInstance();
        this.connectionString = connectionString;
        this.path = path;
        this.identifier = config.getRequestor();
        this.timeout = timeout;
        this.unit = unit;
        this.prefix = UUID.randomUUID().toString() + node_name; //type 4
        this.create_path = this.path + "/" + this.prefix;
    }

    private class KazooLockWatcher<T extends WatchedEvent>
            extends LinkedBlockingQueue<T> implements CuratorWatcher {

        @Override
        public void process(WatchedEvent we) throws Exception {
            add((T)we);
        }
    }

    static Integer sequenceNumberForPath(String path) throws KazooLockException {
        Pattern p = Pattern.compile("_([0-9]+)$");
        Matcher m = p.matcher(path);
        boolean matches = m.find();
        if (matches){
            return new Integer(m.group(1));
        } else {
            throw new KazooLockException("Found non sequential node: "+path);
        }
    }

    CuratorFramework getConnection() {
        return ZooKeeperClient.getConnection(connectionString);
    }

    void waitForNodeRemoval(String path)
            throws Exception{
        KazooLockWatcher klw = new KazooLockWatcher();
        while (getConnection().checkExists().usingWatcher(klw).forPath(path) != null) {
            WatchedEvent we = (WatchedEvent)klw.poll(timeout, unit);
            if (we == null){
                throw new KazooLockException("Timeout Acquiring Lock for node: "+this.path);
            }

        }
    }

    public void acquire() throws Exception {
        state = State.LOCKING;
        LOG.log(Level.FINEST, "KazooLock.acquire");
        // 1. Ensure path to be locked exists
        try {
            getConnection().create()
                    .creatingParentsIfNeeded()
                    .forPath(path, identifier.getBytes(NodePoolGlobalConfiguration.CHARSET));
        } catch (NodeExistsException ex) {
            // node already exists, nothing to do.
        }

        // 2. Create create path and determine our sequence
        node = getConnection().create()                   .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(create_path, identifier.getBytes(NodePoolGlobalConfiguration.CHARSET));
        LOG.log(Level.FINEST, "Lock contender created:" + node);
        sequence = sequenceNumberForPath(node);

        // 3. Wait for any child nodes with lower seq numbers
        List<String> contenders = getConnection().getChildren().forPath(path);
        for (String contender : contenders){
            LOG.log(Level.FINEST, "Found contender for lock:{0}", contender);
            Integer contenderSequence = sequenceNumberForPath(contender);
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
    public void release() throws Exception {
        LOG.log(Level.FINEST, "Releasing Lock {0}", path);
        if (state != State.LOCKED) {
            throw new IllegalStateException(MessageFormat.format("Cannot unlock from state: {0}, Path: {1}", state, node));
        }
        if (node == null) {
            throw new IllegalStateException(MessageFormat.format("Trying to unlock before lock has been locked. State:{0}, Path:{1}", state, node));
        }
        getConnection().delete().forPath(node);
        state = State.UNLOCKED;
        LOG.log(Level.FINE, "Released Lock {0}", path);
    }

}
