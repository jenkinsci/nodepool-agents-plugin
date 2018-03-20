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

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;

/**
 * SingleUseRetentionStrategy This strategy will delete a NodePoolComputer as
 * soon as its single executor has completed a task.
 *
 * Extends always to ensure nodes are launched successfully, then uses
 * ExecutorListener to kill off each node as soon as it finishes a task.
 *
 * @author Rackspace
 */
public class SingleUseRetentionStrategy extends RetentionStrategy.Always implements ExecutorListener {

    private static final Logger LOG = Logger.getLogger(SingleUseRetentionStrategy.class.getName());

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        NodePoolComputer c = (NodePoolComputer) executor.getOwner();
        LOG.log(Level.FINE, "Starting task {0} on NodePoolComputer {1}", new Object[]{task.getFullDisplayName(), c});
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        LOG.log(Level.FINE, "Task " + task.getFullDisplayName() + " completed normally");
        deleteNodePoolComputer(executor, task);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        LOG.log(Level.FINE, "Task " + task.getFullDisplayName() + " completed with problems", problems);

        deleteNodePoolComputer(executor, task);
    }

    private void deleteNodePoolComputer(Executor executor, Queue.Task task) {
        try {
            // When an executor finishes a task, there is a race between
            // the scheduler reusing it and this listener killing the computer.
            // In order to give ourselves the best chance in this race,
            // the computer is marked as offline in the listener thread
            // before the deleter thread is kicked off to actually go and
            // talk to nodepool to release the node.
            final NodePoolComputer c = (NodePoolComputer) executor.getOwner();
            c.doToggleOffline("Disconnecting");
            LOG.log(Level.INFO, "Deleting NodePoolNode {0} after task {1}", new Object[]{c, task.getFullDisplayName()});

            Computer.threadPoolForRemoting.submit(() -> {
                try {
                    c.doDoDelete();
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            });
        } catch (IOException | ServletException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }
}
