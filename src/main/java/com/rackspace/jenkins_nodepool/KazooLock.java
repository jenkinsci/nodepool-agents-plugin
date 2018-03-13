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

import java.nio.charset.Charset;
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

    private static final Logger LOG = Logger.getLogger(KazooLock.class.getName());

    private CuratorFramework conn;
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
    private Charset utf8;
    private Integer sequence;

    public KazooLock(CuratorFramework conn, String path){
        this(conn, path, "jenkins", 5, TimeUnit.SECONDS);
    }

    public KazooLock(CuratorFramework conn,
            String path, String identifier, long timeout, TimeUnit unit){
        this.conn = conn;
        this.path = path;
        this.identifier = identifier;
        this.timeout = timeout;
        this.unit = unit;
        this.prefix = UUID.randomUUID().toString() + node_name; //type 4
        this.create_path = this.path + "/" + this.prefix;
        this.utf8 = Charset.forName("UTF-8");
    }

    private class KazooLockWatcher<T extends WatchedEvent>
            extends LinkedBlockingQueue<T> implements CuratorWatcher {

        @Override
        public void process(WatchedEvent we) throws Exception {
            add((T)we);
        }
    }

    static Integer sequenceNumberForPath(String path) throws KazooLockException{
        Pattern p = Pattern.compile("_([0-9]+)$");
        Matcher m = p.matcher(path);
        boolean matches = m.find();
        if (matches){
            return new Integer(m.group(1));
        } else {
            throw new KazooLockException("Found non sequential node: "+path);
        }
    }

    void waitForNodeRemoval(String path)
            throws Exception{
        KazooLockWatcher klw = new KazooLockWatcher();
        while (conn.checkExists().usingWatcher(klw).forPath(path) != null ){
            WatchedEvent we = (WatchedEvent)klw.poll(timeout, unit);
            if (we == null){
                throw new KazooLockException("Timeout Acquiring Lock for node: "+this.path);
            }

        }
    }

    public void acquire() throws Exception {
        LOG.log(Level.INFO, "KazooLock.acquire");
        // 1. Ensure path to be locked exists
        try {
            conn.create()
                .creatingParentsIfNeeded()
                .forPath(path, identifier.getBytes(utf8));
        } catch (NodeExistsException ex) {
            // node already exists, nothing to do.
        }

        // 2. Create create path and determine our sequence
        node = conn.create()
                   .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(create_path, identifier.getBytes(utf8));
        LOG.log(Level.INFO, "Lock contender created:" + node);
        sequence = sequenceNumberForPath(node);

        // 3. Wait for any child nodes with lower seq numbers
        List<String> contenders = conn.getChildren().forPath(path);
        for (String contender : contenders){
            LOG.log(Level.INFO, "Found contender for lock:{0}", contender);
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

    }
    public void release() throws Exception{
        conn.delete().forPath(node);
    }

}
