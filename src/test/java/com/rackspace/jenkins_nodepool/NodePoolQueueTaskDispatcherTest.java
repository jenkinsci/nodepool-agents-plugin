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

import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.queue.CauseOfBlockage;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * @author hughsaunders
 */
public class NodePoolQueueTaskDispatcherTest {

    Mocks m;

    public NodePoolQueueTaskDispatcherTest() {
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
    }

    @After
    public void tearDown() {
    }

    /**
     * Test that when a matching item and node are given, null is returned.
     */
    @Test
    public void testCanTakeMatch() {
        NodePoolQueueTaskDispatcher instance = new NodePoolQueueTaskDispatcher();
        CauseOfBlockage expResult = null;
        CauseOfBlockage result = instance.canTake(m.nps, m.task);
        assertEquals(expResult, result);
    }

    /**
     * Test that a nodepool node can't take a task it wasn't
     * created for.
     */
    @Test
    public void testCanTakeMisMatch(){
        NodePoolQueueTaskDispatcher instance = new NodePoolQueueTaskDispatcher();
        // new run object so the tasks's run doesn't match
        // the node's run. This should cause canTake to return a CoB
        when(m.npj.getRun()).thenReturn(mock(WorkflowRun.class));
        CauseOfBlockage result = instance.canTake(m.nps, m.task);
        assertTrue(result instanceof NodePoolQueueTaskDispatcher.NodeCreatedForAnotherBuildCauseOfBlockage);
    }

    /**
     * Test that when the node supplied is not a nodepool node,
     * no blockage is returned even if the Runs don't match
     */
    @Test
    public void testCanTakeIgnoreNonNodePool(){
        NodePoolQueueTaskDispatcher instance = new NodePoolQueueTaskDispatcher();
        // Mock node doesn't have a run object, but test
        // should still pass as canTake should return null
        // after checking the class of the node object.
        Node node = mock(Slave.class);
        CauseOfBlockage result = instance.canTake(node, m.task);
        assertEquals(result, null);

    }

}
