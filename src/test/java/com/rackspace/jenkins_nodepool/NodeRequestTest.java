/*
 * The MIT License
 *
 * Copyright 2018 Rackspace.
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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.zookeeper.data.Stat;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Rackspace
 */
public class NodeRequestTest {

    private static final Logger LOG = Logger.getLogger(NodeRequestTest.class.getName());
    static Gson gson;
    private final String label = "testlabel";
    Mocks m;
    NodeRequest nr;

    @BeforeClass
    public static void setUpClass() {
        gson = new Gson();

    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        m = new Mocks();
        nr = new NodeRequest(m.np, m.task);
    }

    @After
    public void tearDown() {
        m.cleanup();
    }

    @Test
    public void TestSerialisation() {
        try {
            NodeRequest nr = new NodeRequest(m.np, m.task);
            String json = nr.toString();

            LOG.fine("TestSerialisation json string: " + json);

            // ensure the json is valid by deserialising it
            Map data = gson.fromJson(json, HashMap.class);

            // Check a couple of key value pairs are as expected
            assertEquals((String) data.get("state"), "requested");
            assertEquals(((List) data.get("node_types")).get(0), label);
        } catch (Exception ex) {
            Logger.getLogger(NodeRequestTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void TestUpdateFromMap() {
        try {
            Map updateData = new HashMap();
            updateData.put("state_time", 1);
            updateData.put("state", "pending");
            nr.updateFromMap(updateData);
            assertEquals(nr.getState(), RequestState.pending);
        } catch (Exception ex) {
            Logger.getLogger(NodeRequestTest.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * Test of createZNode method, of class NodeRequest.
     */
    @Test
    public void testCreateZNode() throws Exception {

        // createZNode is called on construction
        // so remove the created node in order to
        // fully test this invocation of createZNode
        String rpath = nr.getPath();
        m.conn.delete().forPath(rpath);
        Stat exists = m.conn.checkExists().forPath(rpath);
        assertNull(exists);
        assertFalse(nr.exists());

        nr.createZNode();
        rpath = nr.getPath();
        exists = m.conn.checkExists().forPath(rpath);
        assertNotNull(exists);
        assertTrue(nr.exists());
    }

    /**
     * Test of getState method, of class NodeRequest.
     */
    @Test
    public void testGetState() {
        assertTrue(nr.getState() instanceof RequestState);
    }

    /**
     * Test of getAllocatedNodes method, of class NodeRequest.
     */
    @Test
    public void testGetAllocatedNodes() throws Exception {
        try {
            nr.getAllocatedNodes();
            fail("Exception should have been thrown");
        } catch (IllegalStateException e) {
            // pass
        }

        nr.data.put("state", RequestState.fulfilled);
        List<String> nodeIds = new ArrayList();
        nodeIds.add(m.npID);
        nr.data.put("nodes", nodeIds);
        List<NodePoolNode> nodes = (List) nr.data.get("nodes");
        assertNotNull(nodes);
        assertTrue(nodes.size() == 1);
    }

    /**
     * Test of getNodePoolLabel method, of class NodeRequest.
     */
    @Test
    public void testGetNodePoolLabel() {
        String rLabel = nr.getNodePoolLabel();
        assertEquals(m.npLabel, rLabel);
    }

    /**
     * Test of getJenkinsLabel method, of class NodeRequest.
     */
    @Test
    public void testGetJenkinsLabel() {
        assertEquals(m.label, nr.getJenkinsLabel());
    }

    /**
     * Test of getAge method, of class NodeRequest.
     */
    @Test
    public void testGetAge() {
        String age = nr.getAge();
        assertTrue(Pattern.matches("[0-9]+[sm]", age));
    }


}
