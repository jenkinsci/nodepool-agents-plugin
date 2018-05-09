package com.rackspace.jenkins_nodepool;

import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodePoolJobTest {

    private final long taskId = 42;
    private final Label label = new LabelAtom("mylabel");

    private final Queue.Task task = new FreeStyleProject((ItemGroup)null, "myproject");
    private final NodePoolJob job = new NodePoolJob(label, task, taskId);

    @Test
    public void testGetLabel() {
        assertEquals(label, job.getLabel());
    }

    @Test
    public void testGetTask() {
        assertEquals(task, job.getTask());
    }

    @Test
    public void testGetTaskId() {
        assertEquals(taskId, job.getTaskId());
    }

    @Test
    public void testSuccess() {
        job.addAttempt(null);
        job.failAttempt(new Exception("a bad thing happened."));

        job.addAttempt(null);
        job.succeed();

        assertEquals(2, job.getAttempts().size());
        assertTrue(job.isSuccess());
    }

    @Test
    public void testFailure() {
        job.addAttempt(null);
        job.failAttempt(new Exception("could be better."));

        assertTrue(job.isDone());
        assertFalse(job.isSuccess());
    }
}
