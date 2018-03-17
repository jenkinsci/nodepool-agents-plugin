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
import jenkins.model.GlobalConfiguration;

//TODO: Scan build queue on startup for pipelines that persist across restarts as the
// queue entry event won't refire.

@Extension
public class NodePoolQueueListener extends QueueListener {

    private static final Logger LOG = Logger.getLogger(NodePoolQueueListener.class.getName());

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        final Label label = wi.getAssignedLabel();
        LOG.log(Level.FINE, "NodePoolQueueListener received queue notification for label {0}.", new Object[]{label});

        final NodePoolGlobalConfiguration config = GlobalConfiguration.all().get(NodePoolGlobalConfiguration.class);
        if (!config.isConfigured()) {
            // can't do anything if we aren't configured.
            LOG.log(Level.INFO, "NodePool plugin unconfigured, ignoring events.", new Object[]{label});
            return;
        }

        if (label == null || !label.getName().startsWith("nodepool-")) {
            // skip events for builds that aren't nodepool related
            return;
        }
        Computer.threadPoolForRemoting.submit(() -> {
            try {

                String cs = config.getConnectionString();
                String cid = config.getCredentialsId();
                LOG.log(Level.FINEST, "QueueLauncher thread starting , Label: {0}, Connection String: {1}, Creds ID: {2}", new Object[]{label, cs, cid});
                NodePoolClient npc = new NodePoolClient(cs, cid);
                npc.provisionNode(label);
                LOG.log(Level.FINEST, "QueueLauncher thread done, Label: {0}, Connection String: {1}, Creds ID: {2}", new Object[]{label, cs, cid});
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        });
    }
}
