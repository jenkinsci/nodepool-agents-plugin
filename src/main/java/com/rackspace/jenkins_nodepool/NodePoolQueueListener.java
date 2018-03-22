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

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import java.util.logging.Level;
import java.util.logging.Logger;

//TODO: Scan build queue on startup for pipelines that persist across restarts as the
// queue entry event won't refire.

/**
 * Listener to capture the start of new builds and provision NodePool nodes
 */

@Extension
public class NodePoolQueueListener extends QueueListener {

    private static final Logger LOG = Logger.getLogger(NodePoolQueueListener.class.getName());

    private final NodePools nodePools = NodePools.get();

    /**
     * When a build is started, kick off a task to create any required NodePool nodes to service it.
     *
     * @param wi  item waiting in build queue
     */
    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        final Label label = wi.getAssignedLabel();
        LOG.log(Level.FINE, "NodePoolQueueListener received queue notification for label {0}.", new Object[]{label});

        if (label == null) {
            return;
        }

        Computer.threadPoolForRemoting.submit(() -> {
            try {
                nodePools.provisionNode(label, wi.task);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        });
    }

}
