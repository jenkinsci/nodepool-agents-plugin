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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

/**
 *
 * @author hughsaunders
 */
public class NodeRequestTest {
    private static final Logger LOG = Logger.getLogger(NodeRequestTest.class.getName());
    static Gson gson;
    private String label = "testlabel";
    private CuratorFramework conn;
    private ZooKeeperClient zkc;
    
    @ClassRule
    public static NodePoolRule npr = new NodePoolRule();
    
    @BeforeClass
    public static void setUpClass() {
        gson = new Gson();
      
        
    }
    
    @Before
    public void setUp() throws Exception{
        conn = npr.getCuratorConnection();
    }
    
    @Test
    public void TestSerialisation(){
        NodeRequest nr = new NodeRequest(conn, label);	
        String json = nr.toString();
        
        LOG.fine("TestSerialisation json string: "+json);
        
        // ensure the json is valid by deserialising it
        Map data = gson.fromJson(json, HashMap.class);
        
        // Check a couple of key value pairs are as expected
        assertEquals((String)data.get("state"), "requested");
        assertEquals(((List)data.get("node_types")).get(0), label);
    }
    
    @Test
    public void TestDeserialisation(){
        String[] keys = {"node_types", "requestor", "state", "state_time"};
        NodeRequest nr = new NodeRequest(conn, label);	
        String json = nr.toString();
        NodeRequest nr2 = NodeRequest.fromJson(conn, json);
        LOG.info("nr: "+nr);
        LOG.info("nr2: "+nr2);
        for (String key : keys){
            LOG.info("key compare: "+key);
            assertEquals(nr.get(key), nr2.get(key));
        }
        assertEquals(nr, nr2);
        assertTrue(nr.equals(nr2));
    }

}
