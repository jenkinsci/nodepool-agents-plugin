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
import java.util.ArrayList;
import java.util.List;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;



/**
 *
 * @author hughsaunders
 */
public class NodepoolClientTest {
	
	private TestingServer zkTestServer;
	
	public NodepoolClientTest() {
	}
	
	@BeforeClass
	public static void setUpClass() {
	}
	
	@AfterClass
	public static void tearDownClass() {
	}
	
	@Before
	public void setUp() throws Exception {
                // setup zk test server
		zkTestServer = new TestingServer();
	}
	
	@After
	public void tearDown() {
	}
        
        private NodepoolClient getClient(){
            NodepoolClient npc = new NodepoolClient(
                "localhost:" + Integer.toString(zkTestServer.getPort())
            );
            npc.connect();
            return npc;
        }

	@Test
	public void nodeCRUD() throws Exception{
            String path = "/crudTest";
            String payload = "payload";
            Charset utf8 = Charset.forName("UTF-8");
            NodepoolClient npc = getClient();
            CuratorFramework conn = npc.getConnection();
           
            conn.create().forPath(path, payload.getBytes(utf8));
            byte[] recievedPayload = conn.getData().forPath(path);
            String recievedPayloadString = new String(recievedPayload, utf8);
            assertEquals(payload, recievedPayloadString);
        }
        
        
        private class ZkWatcher implements Watcher {
            
            private List<WatchedEvent> events;

            public ZkWatcher() {
                this.events = new ArrayList();
            }

            @Override
            public void process(WatchedEvent we) {
                events.add(we);
            }
            
            public List<WatchedEvent> getEvents(){
                return events;
            }
            
        }
        @Test
        public void nodeWatch() throws Exception{
            String path = "/watchTest";
            String payload = "payload";
            Charset utf8 = Charset.forName("UTF-8");
            NodepoolClient npc = getClient();
            CuratorFramework conn = npc.getConnection();
            Watcher watcher = new ZkWatcher();
            
            // set watch
            conn.getData().usingWatcher(watcher);
            
            // create node
            conn.create().forPath(path, payload.getBytes(utf8));
            // ensure creation triggers watch
            
            while (true){
                // use a blocking queue to retrieve the event rather than a polling loop
            }
           
            
            byte[] recievedPayload = conn.getData().forPath(path);
            String recievedPayloadString = new String(recievedPayload, utf8);
            assertEquals(payload, recievedPayloadString);
        }
                
        
        
}
