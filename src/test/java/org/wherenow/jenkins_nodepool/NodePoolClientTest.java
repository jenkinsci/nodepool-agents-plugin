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

import com.arakelian.docker.junit.Container;
import com.arakelian.docker.junit.Container.Binding;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.CuratorFramework;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;

/**
 *
 * @author hughsaunders
 */
public class NodePoolClientTest {
    
    @ClassRule
    public static NodePoolRule npr = new NodePoolRule();
    
    private static ZooKeeperClient zkc;
    private static CuratorFramework conn;
    private static Integer zkPort;

    
   
    public NodePoolClientTest() {
        
    }
    
    @BeforeClass
    public static void setUpClass() throws InterruptedException {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of idForPath method, of class NodePoolClient.
     */
    @Test
    public void testIdForPath() throws Exception {
        System.out.println("idForPath");
        String path = "foo/bah-123";
        String expResult = "123";
        String result = NodePoolClient.idForPath(path);
        assertEquals(expResult, result);
    }
    
    @Test
    public void ruleTest() throws Exception {
        npr.getCuratorConnection().create().forPath("/testnode");
    }
    
}
