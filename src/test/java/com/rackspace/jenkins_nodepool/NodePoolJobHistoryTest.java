package com.rackspace.jenkins_nodepool;

import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class NodePoolJobHistoryTest {

    private static final int maxHistoryLength = 2;
    private NodePoolJobHistory nodePoolJobHistory;

    @Before
    public void setUp() {
        nodePoolJobHistory = new NodePoolJobHistory(maxHistoryLength);
    }

    @Test
    public void testAddJob() {
        final NodePoolJob job = new NodePoolJob(null, null, 1);
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
                    new NodePoolJob(null, null, i)
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
                new NodePoolJob(null, null, 1)
        );

        nodePoolJobHistory.add(
                new NodePoolJob(null, null, 42)
        );

        final NodePoolJob job = nodePoolJobHistory.getJob(42);
        assertNotNull(job);
    }

    @Test
    public void testGetJobNonExistent() {
        nodePoolJobHistory.add(
                new NodePoolJob(null, null, 1)
        );

        assertNull(nodePoolJobHistory.getJob(2));
    }
}