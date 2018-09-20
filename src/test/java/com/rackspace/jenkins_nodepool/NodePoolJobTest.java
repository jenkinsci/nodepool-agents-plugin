package com.rackspace.jenkins_nodepool;

import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class NodePoolJobTest {

    private Mocks m = new Mocks();
    private final long taskId = 42;
    private final Label label = new LabelAtom("mylabel");

    //private final Queue.Task task = new FreeStyleProject((ItemGroup)null, "myproject");
    //private final Queue.Item item = new Queue.WaitingItem(new GregorianCalendar(), task, new ArrayList<>());
    private NodePoolJob job = new NodePoolJob(label, m.task, m.qID);

    @Before
    public void before(){
        m = new Mocks();
        job = new NodePoolJob(label, m.task, m.qID);
    }

    @Test
    public void testGetLabel() {
        assertEquals(label, job.getLabel());
    }

    @Test
    public void testGetTask() {
        assertEquals(m.task, job.getTask());
    }

    @Test
    public void testGetTaskId() {
        assertEquals(taskId, job.getTaskId());
    }

    @Test
    public void testSuccess() {
        job.addAttempt(m.nr);
        job.failAttempt(new Exception("a bad thing happened."));

        job.addAttempt(m.nr);
        job.succeed();

        assertEquals(2, job.getAttempts().size());
        assertTrue(job.isSuccess());
    }

    @Test
    public void testFailure() {
        job.addAttempt(m.nr);
        job.failAttempt(new Exception("could be better."));

        assertTrue(job.isDone());
        assertFalse(job.isSuccess());
    }
}
