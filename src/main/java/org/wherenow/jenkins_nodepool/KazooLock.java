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

import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;

/**
 * Partial Java implementation of the python module kazoo.recipe.lock
 * @author hughsaunders
 */
public class KazooLock {

    private static final Logger LOG = Logger.getLogger(KazooLock.class.getName());
    
    private CuratorFramework conn;
    private final String path;
    private final String identifier;
    private final String node_name = "__lock__";
    private final String prefix;
    private final String create_path;
    //this is set to create_path+index number, when the node is created
    private String node;
    private Charset utf8;
    private Integer sequence;
    
    public KazooLock(CuratorFramework conn, String path){
        this(conn, path, "jenkins");
    }
    
    public KazooLock(CuratorFramework conn, 
            String path, String identifier){
        this.conn = conn;
        this.path = path;
        this.identifier = identifier;
        this.prefix = UUID.randomUUID().toString() + node_name; //type 4
        this.create_path = this.path + "/" + this.prefix;
        this.utf8 = Charset.forName("UTF-8");
    }
    
    static Integer sequenceNumberForPath(String path) throws KazooLockException{
        Pattern p = Pattern.compile(".*-([0-9]+)$");
        Matcher m = p.matcher(path);
        boolean matches = m.find();
        if (matches){
            return new Integer(m.group(1));
        } else {
            throw new KazooLockException("Found non sequential node: "+path);
        }
    }

    private class KazooLockWatcher<T extends WatchedEvent>
            extends LinkedBlockingQueue<T> implements CuratorWatcher {

        @Override
        public void process(WatchedEvent we) throws Exception {
            add((T)we);
        }
    }

    void waitForOtherContenders() throws Exception{
         List<String> contenders = conn.getChildren().forPath(path);
        for (String contender : contenders){
            LOG.fine("Found contender for lock:" + contender);
            Integer contenderSequence = sequenceNumberForPath(contender);
            if (contenderSequence < this.sequence){
                // This contender is ahead of us in the queue,
                // watch and wait
                waitForNodeRemoval(path);
            }
        }
    }
    void waitForNodeRemoval(String path)
            throws Exception{
        KazooLockWatcher klw = new KazooLockWatcher();
        while (conn.checkExists().usingWatcher(klw).forPath(path) != null ){
            klw.take();
        }
    }
    
    public void acquire() throws Exception{
       
        /**
         * /path <-- ensure this
         * /path/prefix <-- lock path?
         * create path
         * self.node is creat_path+index number
         *
         * delete
         * self.node = prefix
        
         *  - Ensure path exists
         *  - Check for child nodes with lower sequence numbers
         *    - For any lower seq children, create watch and wait
         *  - Create create_path ephemeral and sequential
         */
        
        
        
        // 1. Ensure path exists
        conn.create().forPath(path, identifier.getBytes(utf8));
        
        // 2. Create create path and determine our sequence
        this.node = conn.create().forPath(create_path, identifier.getBytes(utf8));
        this.sequence = sequenceNumberForPath(this.node);
        
        // 3. Wait for any child nodes with lower seq numbers
    }
    
}
