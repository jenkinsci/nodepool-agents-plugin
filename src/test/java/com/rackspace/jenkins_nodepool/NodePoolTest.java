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

import hudson.model.Descriptor;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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
public class NodePoolTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    Mocks m;
    NodePool np;

    public NodePoolTest() {
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
        np = new NodePool(
                m.connectionString,
                m.credentialsID,
                m.labelPrefix,
                m.requestRoot,
                m.priority,
                m.requestor,
                m.zkNamespace,
                m.nodeRoot
        );
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of createZKConnection method, of class NodePool.
     */
    @Test
    public void testCreateZKConnection() {
        CuratorFramework c = NodePool.createZKConnection(m.connectionString, m.zkNamespace);
        assertEquals(CuratorFrameworkState.STARTED, c.getState());
        c.close();
    }

    /**
     * Test of acceptNodes method, of class NodePool.
     */
    @Test
    public void testAcceptNodes() throws Exception {
        np.requests.add(m.nr);
        np.acceptNodes(m.nr);
        verify(m.nr).delete();
        verify(m.npn).setInUse();
    }


    /**
     * Test of getDescriptor method, of class NodePool.
     */
    @Test
    public void testGetDescriptor() {
        assertTrue(np.getDescriptor() instanceof Descriptor);
    }

    /**
     * Test of nodePoolLabelFromJenkinsLabel method, of class NodePool.
     */
    @Test
    public void testNodePoolLabelFromJenkinsLabel() {
        assertEquals(m.npLabel, np.nodePoolLabelFromJenkinsLabel(m.label.getDisplayName()));
    }

    /**
     * Test of setConnectionString method, of class NodePool.
     */
    @Test
    public void testSetConnectionString() {
        CuratorFramework c = np.getConn();
        String connectionString = np.getConnectionString();
        np.setConnectionString(connectionString);
        assertSame(c, np.getConn());
    }

    /**
     * Test of idForPath method, of class NodePool.
     */
    @Test
    public void testIdForPath() throws Exception {
        String path = MessageFormat.format("/foo/bah/wib-{0}", m.npID);
        assertEquals(m.npID, np.idForPath(path));
    }

    /**
     * Test of provisionNode method, of class NodePool. This method checks that
     * The provision process correctly responds to a node request being
     * fulfilled. Its not exhaustive as no nodes are actually created.
     */
    @Test
    public void testProvisionNode() throws Exception {
        new Thread(() -> {
            try {

                // This thread pretends to be nodepool, it will set the
                // state of the pending request to fulfilled.
                m.conn.create().creatingParentsIfNeeded().forPath("/" + m.requestRoot);

                // find request znode
                String child = null;
                Integer attempts = 10;
                while (attempts-- > 0) {
                    List<String> Children = m.conn.getChildren().forPath("/" + m.requestRoot);
                    if (Children.isEmpty()) {
                        Thread.sleep(500);
                    } else {
                        child = Children.get(0);
                        break;
                    }
                }
                assertNotNull(child);
                String path = MessageFormat.format("/{0}/{1}", m.requestRoot, child);
                Map rdata = m.getNodeData(path);
                List<String> nodes = Arrays.asList(new String[]{});
                rdata.put("state", RequestState.fulfilled);
                rdata.put("nodes", nodes);
                m.writeNodeData(path, rdata);

            } catch (Exception ex) {
                Logger.getLogger(NodePoolTest.class.getName()).log(Level.SEVERE, null, ex);
            }

        }).start();
        np.provisionNode(m.label, m.task, 10);

        // this test will timeout on failure
    }

}
