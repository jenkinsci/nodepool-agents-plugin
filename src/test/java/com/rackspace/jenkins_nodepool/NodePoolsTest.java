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

import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Mockito.verify;

/**
 *
 * @author hughsaunders
 */
public class NodePoolsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public Mocks m;
    NodePools nps;

    public NodePoolsTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        m = new Mocks();
        nps = new NodePools();
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of get method, of class NodePools.
     */
    @Test
    public void testGet() {
        NodePools result = NodePools.get();
        assertTrue(result instanceof NodePools);
    }

// disabled due to problems with serialisation as configure
// calls save();
    /**
     * Test of configure method, of class NodePools.
     */
//    @Test
//    public void testConfigure() throws Exception {
//
//        // An empty JSONObject should cause configure to clear
//        // its internal list of NodePools.
//        nps.getNodePools().add(m.np);
//        JSONObject jo = new JSONObject();
//        StaplerRequest req = mock(StaplerRequest.class);
//        nps.configure(req, jo);
//        assertTrue(nps.getNodePools().isEmpty());
//
//        // A JSONObject that contains the key NodePools should
//        // not cause configure to clear the internal list of
//        //  nodepools
//        nps.getNodePools().add(m.np);
//        jo = new JSONObject();
//        jo.put("nodePools", "value");
//        req = mock(StaplerRequest.class);
//        nps.configure(req, jo);
//        assertTrue(nps.getNodePools().isEmpty());
//    }
    /**
     * Test of iterator method, of class NodePools.
     */
    @Test
    public void testIterator() {
        assertTrue(nps.iterator() instanceof Iterator);
    }

    /**
     * Test of nodePoolsForLabel method, of class NodePools.
     */
    @Test
    public void testNodePoolsForLabel() {
        nps.getNodePools().add(m.np);

        Label label = new LabelAtom("foo");
        nps.nodePoolsForLabel(label);
        List<NodePool> results = nps.nodePoolsForLabel(label);
        assertTrue(results.isEmpty());

        label = new LabelAtom("nodepool-debian");
        results = nps.nodePoolsForLabel(label);
        assertFalse(results.isEmpty());
        NodePool rnp = results.get(0);
        assertEquals(rnp, m.np);
    }

    /**
     * Test of provisionNode method, of class NodePools.
     */
    @Test
    public void testProvisionNode() throws Exception {
        nps.getNodePools().add(m.np);
        nps.provisionNode(m.label, m.task, m.qID);

        final NodePoolJob job = new NodePoolJob(m.label, m.task, m.qID);
        verify(m.np).provisionNode(job);
    }

    /**
     * Test of stream method, of class NodePools.
     */
    @Test
    public void testStream() {
        Stream s = nps.stream();
        assertTrue(s instanceof Stream);
    }

}
