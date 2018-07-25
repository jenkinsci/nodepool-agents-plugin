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

import org.junit.*;

import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 *
 * @author hughsaunders
 */
public class NodePoolNodeTest {

    private NodePoolNode npn;
    private Mocks m;
    private String nodePath;
    private String hostKey;
    private List<String> hostKeys;

    public NodePoolNodeTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        try {
            m = new Mocks();
            hostKeys = new ArrayList();
            hostKey = "hostKey";
            hostKeys.add(hostKey);
            nodePath = MessageFormat.format("/{0}/{1}", m.nodeRoot, m.npID);
            m.conn.create()
                    .creatingParentsIfNeeded()
                    .forPath(nodePath, m.jsonString.getBytes(m.charset));
            npn = new NodePoolNode(m.np, m.npID);
            // Type field now holds a list of string values
            final List<String> types = new ArrayList<>();
            types.add(m.npLabel);
            npn.data.put("type", types);
            npn.data.put("interface_ip", m.host);
            npn.data.put("connection_port", m.port);
            npn.data.put("host_keys", hostKeys);
        } catch (Exception ex) {
            Logger.getLogger(NodePoolNodeTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getNPType method, of class NodePoolNode.
     */
    @Test
    public void testGetNPType() {
        final List<String> npTypes = npn.getNPTypes();
        assertTrue("NodePool Types contains Label", npTypes.contains(m.npLabel));
    }

    /**
     * Test of getLockPath method, of class NodePoolNode.
     */
    @Test
    public void testGetLockPath() {
        String lockPath = MessageFormat.format("{0}/lock", nodePath);
        assertEquals(lockPath, npn.getLockPath());
    }

    /**
     * Test of getJenkinsLabel method, of class NodePoolNode.
     */
    @Test
    public void testGetJenkinsLabel() {
        assertEquals("Display Name in Jenkins Label", npn.getJenkinsLabel(), m.label.getDisplayName());
    }

    /**
     * Test of getName method, of class NodePoolNode.
     */
    @Test
    public void testGetName() {
        assertEquals(
                MessageFormat.format("{0}-{1}", m.label.getDisplayName(), m.npID),
                 npn.getName());
    }

    /**
     * Test of getHost method, of class NodePoolNode.
     */
    @Test
    public void testGetHost() {
        assertEquals(m.host, npn.getHost());
    }

    /**
     * Test of getPort method, of class NodePoolNode.
     */
    @Test
    public void testGetPort() {
        assertEquals((Integer) m.port.intValue(), npn.getPort());
    }

    /**
     * Test backward compatibility with older NodePool clusters
     */
    @Test
    public void testGetPortBackwardCompat() {
        npn.data.remove("connection_port");
        npn.data.put("ssh_port", 2222.0);

        assertEquals(new Integer(2222), npn.getPort());
    }

    /**
     * Test the default to 22 if no port is provided.
     */
    @Test
    public void testDefaultPort() {
        npn.data.remove("connection_port");
        assertEquals(new Integer(22), npn.getPort());
    }

    /**
     * Test of getHostKey method, of class NodePoolNode.
     */
    @Test
    public void testGetHostKey() {
        assertEquals(hostKey, npn.getHostKey());
    }

    /**
     * Test of getHostKeys method, of class NodePoolNode.
     */
    @Test
    public void testGetHostKeys() {
        assertTrue(hostKeys.containsAll(npn.getHostKeys()));
    }


    /**
     * Test of setInUse method, of class NodePoolNode.
     */
    @Test
    public void testSetInUse() throws Exception {
        npn.setInUse();
        Map data = m.getNodeData(nodePath);
        assertSame("NodePoolState is IN-USE", NodePoolState.IN_USE, NodePoolState.fromString((String)data.get("state")));
        assertEquals(KazooLock.State.LOCKED, npn.lock.getState());
    }

    /**
     * Test of release method, of class NodePoolNode.
     */
    @Test
    public void testRelease() throws Exception {
        npn.setInUse();
        npn.release();
        Map data = m.getNodeData(nodePath);
        assertSame("NodePoolState is USED", NodePoolState.USED, NodePoolState.fromString((String)data.get("state")));
        assertEquals(KazooLock.State.UNLOCKED, npn.lock.getState());
    }
}
