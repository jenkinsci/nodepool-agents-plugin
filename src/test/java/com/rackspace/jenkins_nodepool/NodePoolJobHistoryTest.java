package com.rackspace.jenkins_nodepool;

import java.util.Iterator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

public class NodePoolJobHistoryTest {

    private static final int maxHistoryLength = 2;
    private NodePoolJobHistory nodePoolJobHistory;
    private Mocks m;

    @Before
    public void setUp() {
        nodePoolJobHistory = new NodePoolJobHistory(maxHistoryLength);
        m = new Mocks();
    }

    @Test
    public void testAddJob() {
        final NodePoolJob job = new NodePoolJob(m.label, m.task, m.qID);
        nodePoolJobHistory.add(job);

        int sz = 0;
        final Iterator<NodePoolJob> iter = nodePoolJobHistory.iterator();

        NodePoolJob j = null;
        while (iter.hasNext()) {
            j = iter.next();
            sz += 1;
        }

        assertEquals(1, sz);
        assertEquals(job, j);
    }

    @Test
    public void testAddJobPruning() {
        // test pruning of excess jobs.
        for (int i = 0; i < maxHistoryLength + 1; i++ ) {
            nodePoolJobHistory.add(
                    new NodePoolJob(m.label, m.task, m.qID)
            );
        }

        final Iterator<NodePoolJob> iter = nodePoolJobHistory.iterator();
        int sz = 0;

        while (iter.hasNext()) {
            iter.next();
            sz += 1;
        }

        assertEquals(maxHistoryLength, sz);
    }

    @Test
    public void testGetJob() {

        nodePoolJobHistory.add(
            new NodePoolJob(m.label, m.task, m.qID)
        );

        final NodePoolJob job = nodePoolJobHistory.getJob(m.qID);
        assertNotNull(job);
    }

    @Test
    public void testGetJobNonExistent() {
        nodePoolJobHistory.add(
            new NodePoolJob(m.label, m.task, m.qID)
        );

        assertNull(nodePoolJobHistory.getJob(2));
    }
}