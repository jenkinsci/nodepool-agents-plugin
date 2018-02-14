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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.WatchedEvent;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;



/**
 * This class mostly serves as my notes on how to use the
 * curator framework.
 * @author hughsaunders
 */
public class ZooKeeperClientTest {
	
	private TestingServer zkTestServer;
	
	public ZooKeeperClientTest() {
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
        
        private ZooKeeperClient getClient(){
            ZooKeeperClient zk = new ZooKeeperClient(
                "localhost:" + Integer.toString(zkTestServer.getPort())
            );
            zk.connect();
            return zk;
        }

	@Test
	public void nodeCRUD() throws Exception{
            String path = "/crudTest";
            String payload = "payload";
            String payloadUpdate = "payload update";
            Charset utf8 = Charset.forName("UTF-8");
            
            //Connect
            ZooKeeperClient zk = getClient();
            CuratorFramework conn = zk.getConnection();
           
            // Assert path doesn't exist before creation
            assertEquals(null, conn.checkExists().forPath(path));
            
            // Create
            conn.create().forPath(path, payload.getBytes(utf8));
            
            // Read
            byte[] recievedPayload = conn.getData().forPath(path);
            String recievedPayloadString = new String(recievedPayload, utf8);
            assertEquals(payload, recievedPayloadString);
            
            // Update
            conn.setData().forPath(path, payloadUpdate.getBytes(utf8));
            
            // Read again to check update
            recievedPayload = conn.getData().forPath(path);
            recievedPayloadString = new String(recievedPayload, utf8);
            assertEquals(payloadUpdate, recievedPayloadString);
            
            // Delete
            conn.delete().forPath(path);
            
            // Assert nonexistent post delete
            assertEquals(null, conn.checkExists().forPath(path));
            
        }
        
        
        private class ZkWatcher<T> extends LinkedBlockingQueue<T> implements CuratorWatcher {
            @Override
            public void process(WatchedEvent we) {
                add((T)we);
            }
        }
        
        @Test
        public void nodeWatch() throws Exception{
            String path = "/watchTest";
            String payload = "payload";
            Charset utf8 = Charset.forName("UTF-8");
            ZooKeeperClient zk = getClient();
            CuratorFramework conn = zk.getConnection();
            ZkWatcher<WatchedEvent> watcher = new ZkWatcher();
            
            // set watch
            conn.checkExists().usingWatcher(watcher).forPath(path);
            
            // create node
            conn.create().forPath(path, payload.getBytes(utf8));
           
            // ensure creation triggers watch
            // If poll times out, exception will be thrown and test fails
            WatchedEvent we1 = watcher.poll(10, TimeUnit.SECONDS);
            
            // assert path recieved with event matches path node was created with
            String receivedPath = we1.getPath();
            assertEquals(path, receivedPath);
            
            // assert payload received is the same as payload that was sent
            String receivedPayload = new String(conn.getData().forPath(path), utf8);
            assertEquals(payload, receivedPayload);

        }
                
        @Test
        public void testChildren() throws Exception{
            ZooKeeperClient zk = getClient();
            CuratorFramework conn = zk.getConnection();
            
            List<String> children = new ArrayList<String>();
            children.add("c1");
            children.add("c2");
                    
            for (String child : children){
                conn.create().creatingParentsIfNeeded().forPath("/parent/"+child);
            }
            
            List<String> receivedChildren = conn.getChildren().forPath("/parent");
            assertEquals(children, receivedChildren);
            
            
            
        }
        
}
