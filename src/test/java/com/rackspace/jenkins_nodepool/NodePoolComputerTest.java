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

import static org.junit.Assert.assertEquals;

import hudson.model.Executor;
import hudson.model.Queue;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 *
 * @author hughsaunders
 */
public class NodePoolComputerTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    Mocks m;
    NodePoolComputer npc;

    @Before
    public void setUp() {
        m = new Mocks();
        npc = new NodePoolComputer(m.nps, m.npn);
    }

    /**
     * Test of doDoDelete method, of class NodePoolComputer.
     */
    @Test
    public void testDoDoDelete() throws Exception {
        npc.doDoDelete();
        verify(m.npn).release();
    }

    /**
     * Test of toString method, of class NodePoolComputer.
     */
    @Test
    public void testToString() {
        assertEquals(m.npcName, m.npn.getName());
    }

    /**
     * Test the default case where after a build executes, the computer is disabled and deleted.
     */
    @Test
    public void testCleanupDelete() {

        npc = spy(npc);
        when(npc.getNode()).thenReturn(m.nps);

        final Executor executor = mock(Executor.class);
        when(executor.getOwner()).thenReturn(npc);

        final Queue.Executable executable = mock(Queue.Executable.class);
        when(executor.getCurrentExecutable()).thenReturn(executable);
        when(executable.toString()).thenReturn("dummy_task #1");

        when(m.nps.isHeld()).thenReturn(false);  // !held means computer should be deleted

        final Queue.Task task = mock(Queue.Task.class);
        npc.taskCompleted(executor, task, 0);

        // confirm executor gets disabled
        verify(npc).setAcceptingTasks(false);

        // confirm the delete path was taken
        verify(npc).deleteNodePoolComputer(npc, task);
    }

    @Test
    public void testCleanupHeld() {
        final Executor executor = mock(Executor.class);
        when(executor.getOwner()).thenReturn(m.npc);

        final Queue.Executable executable = mock(Queue.Executable.class);
        when(executor.getCurrentExecutable()).thenReturn(executable);
        when(executable.toString()).thenReturn("dummy_task #1");

        when(m.nps.isHeld()).thenReturn(true); // the mock slave node is being "held"
        when(m.npc.getNode()).thenReturn(m.nps);
        final Queue.Task task = mock(Queue.Task.class);
        when(task.getFullDisplayName()).thenReturn("dummy_task");

        when(m.npc.getNodePoolNode()).thenReturn(m.npn);

        npc.taskCompleted(executor, task, 0);

        // confirm executor gets disabled
        verify(m.npc).setAcceptingTasks(false);

        try {
            verify(m.npc.getNodePoolNode()).hold("dummy_task #1");
        } catch (Exception e) {
            // signature of hold() forces this catch, but we want to see any exceptions.
            throw new RuntimeException(e);
        }
    }

}