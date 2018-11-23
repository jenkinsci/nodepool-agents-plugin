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

import com.rackspace.jenkins_nodepool.models.NodeRequestModel;
import org.apache.zookeeper.data.Stat;
import org.junit.*;

import static java.lang.String.format;
import static java.util.logging.Logger.getLogger;
import static org.junit.Assert.*;

/**
 *
 * @author Rackspace
 */
public class NodeRequestTest {

    /**
     * Logger for this class.
     */
    private static final Logger LOG = getLogger(NodeRequestTest.class.getName());

    private static Gson gson;
    private final String label = "testlabel";
    private Mocks m;
    private NodeRequest nr;

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
        nr = new NodeRequest(m.np, m.priority, m.npj);
    }

    @After
    public void tearDown() {
        m.cleanup();
    }

    @Test
    public void TestSerialisation() {
        try {
            final NodeRequest nr = new NodeRequest(m.np, m.priority, m.npj);
            final List<String> allocatedNodesList = new ArrayList<>();
            allocatedNodesList.add(label);
            nr.setAllocatedNodes(allocatedNodesList);

            final String json = nr.getModelAsJSON();

            LOG.fine("TestSerialisation json string: " + json);

            // ensure the json is valid by deserialising it
            final NodeRequestModel nodeRequestModel = gson.fromJson(json, NodeRequestModel.class);

            // Check a couple of key value pairs are as expected
            assertEquals("Request State is REQUESTED", NodePoolState.REQUESTED, nodeRequestModel.getState());
            assertEquals("Node Request Type Label Matches", nodeRequestModel.getNode_types().get(0), label);
        } catch (Exception ex) {
            fail(format("%s while testing serialization. Message: %s",
                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
        }
    }

    /**
     * Test of getState method, of class NodeRequest.
     */
    @Test
    public void testGetState() {
        assertTrue(nr.getState() instanceof NodePoolState);
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
