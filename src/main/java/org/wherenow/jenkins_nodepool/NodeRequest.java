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

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;

enum State{
	requested, pending, fulfilled, failed
}

/**
 * Represents a nodepool node request. Data format is JSON dump of following dict structure:
 * 	---
 * 	node_types:
 * 		- label1
 * 		- label2
 *	requestor: string id (eg hostname)
 * 	state: string requested|pending|fulfilled|failed
 * 	state_time: float seconds since epoch 
 * 
 * @author hughsaunders
 */
public class NodeRequest extends HashMap implements CuratorWatcher{
    
    // fields
    
    static Charset charset = Charset.forName("UTF-8");
    private Gson gson;
    private String[] requiredKeys = {"node_types", "requestor", "state", "state_time"};
    private String nodePoolID;
    private String nodePath;
    private CuratorFramework conn;
    private CountDownLatch latch;
    private KazooLock lock;
    

    // Static initialisers
        
    public static NodeRequest fromJsonBytes(CuratorFramework conn, byte[] bytes){
        return NodeRequest.fromJson(conn, new String(bytes, charset));
    }

    public static NodeRequest fromJson(CuratorFramework conn, String json){
        Gson gson = new Gson();
        Map data = gson.fromJson(json, HashMap.class);
        return new NodeRequest(conn, data);
    }

    // constructors
    
    // package private constructor
    NodeRequest(CuratorFramework conn, Map data){
        this.gson = new Gson();
        this.conn = conn;
        this.latch = new CountDownLatch(1);
        updateFromMap(data);
    }


    public NodeRequest(CuratorFramework conn, String label)	{
        this(conn, "jenkins", Arrays.asList( new String[] { label }));
    }

    public NodeRequest(CuratorFramework conn, List labels)	{
        this(conn, "jenkins", labels);
    }
    @SuppressFBWarnings
    public NodeRequest(CuratorFramework conn, String requestor, List<String> labels) {
        this.gson = new Gson();
        this.conn = conn;
         this.latch = new CountDownLatch(1);
        put("node_types", new ArrayList(labels));
        put("requestor", requestor);
        put("state", State.requested);
        put("state_time", new Double(System.currentTimeMillis()/1000));
    }
    
    // public methods

    public String getNodePath() {
        return nodePath;
    }

    public void setNodePath(String nodePath) {
        this.nodePath = nodePath;
    }

    public String getNodePoolID() {
        return nodePoolID;
    }

    public void setNodePoolID(String nodePoolID) {
        this.nodePoolID = nodePoolID;
    }

    @Override
    public String toString(){
        String jsonStr = gson.toJson(this);
        return jsonStr;
    }

    public byte[] getBytes(){
        return toString().getBytes(charset);
    }
    
    @Override
    public void process(WatchedEvent we) throws Exception {
        // we don't care what the event is, but something changed, so refresh
        // local data from zookeeper.
        updateFromZooKeeper();
    }
    
    public void waitForFulfillment() throws Exception {
        while(true){
            State state = (State)get("state");
            switch(state){
                case requested:
                case pending:
                    latch.await();
                    break;
                case fulfilled:
                    return;
                case failed:
                    throw new NodePoolException("Nodepool node Request failed :("+this.toString());
            }
        }
    }
    
    private void lock() throws Exception{
        if (lock == null){
            lock = new KazooLock(conn, nodePath);
        }
        lock.acquire();
    }

    
    public void accept() throws Exception {
        lock();
        
    }
    
    private void unblockWaiters(){
        CountDownLatch old = latch; 
        latch = new CountDownLatch(1);// do the switch before countdown, 
                                      // to ensure the next await() call happens on the new latch.
        old.countDown(); // this will unblock threads that have called latch.await()
    }
    
    void updateFromZooKeeper() throws Exception{
        if (nodePath == null){
            throw new NodePoolException("Attempt to update request from zookeeper before path has been set");
        }
        byte[] bytes = conn.getData().forPath(nodePath);
        NodeRequest remoteState = NodeRequest.fromJsonBytes(conn, bytes);
        updateFromMap(remoteState);
        unblockWaiters();
    }
    
    private void updateFromMap(Map data){
        put("state_time", (Double)data.get("state_time"));
        put("node_types", data.get("node_types"));
        put("state", State.valueOf((String)data.get("state")));
        put("requestor", data.get("requestor"));
    }
}
