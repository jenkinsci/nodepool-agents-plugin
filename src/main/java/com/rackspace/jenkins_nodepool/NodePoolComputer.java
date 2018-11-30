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

import hudson.model.*;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;

/**
 * @author hughsaunders
 */
public class NodePoolComputer extends SlaveComputer {

    private static final Logger LOG = Logger.getLogger(NodePoolComputer.class.getName());
    /**
     * We can get the name from the nodePoolNode object but we store name
     * separately incase nodePoolNode is null after deserialisation then we can
     * use a sensible name in logs.
     */
    private String name;
    private NodePoolNode nodePoolNode;
    private NodePoolJob nodePoolJob;

    public NodePoolComputer(Slave slave) {
        super(slave);
        // required by superclass but shouldn't be used.
        throw new IllegalStateException("Attempting to initialise NodePoolComputer without supplying a NodePoolNode.");
    }

    public NodePoolComputer(final NodePoolSlave nps, final NodePoolNode npn, final NodePoolJob npj) {
        super(nps);
        this.nodePoolJob = npj;
        if (npj == null){
            LOG.warning("NodePoolJob null in NodePoolComputerConstructor");
        } else {
            this.nodePoolJob.logToBoth(("NodePoolComputer created: "+this.nodeName));
        }

        setNodePoolNode(npn);
        if (npn != null) {
            name = npn.getName();
        } else {
            Computer.threadPoolForRemoting.submit(() -> {
                try {
                    LOG.log(Level.WARNING, "Removing NodePool Computer {0} on startup as its a nodepool node that will have been destroyed", this.toString());
                    doDoDelete();
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Failed to remove NodePool Computer {0}.", this.toString());
                    LOG.log(Level.SEVERE, null, ex);
                }
            });
        }
    }

    /**
     * Override so that lock can be used for cleaning up
     * @param cause: reason for disconnecting
     * @return Future representing disconnect progress
     */
    @Override
    public Future<?> disconnect(OfflineCause cause) {
        Lock cleanupLock = NodePools.get().getCleanupLock();
        Future<?> result = null;
        cleanupLock.lock();
        try{
            result = super.disconnect(cause); //To change body of generated methods, choose Tools | Templates.
            // Call get to block until disconnection
            // has been executed, to ensure execution
            // happens while cleanupLock is held.
            // Timeout added so that the cleanupLock is not held indefinitely.
            result.get(2, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            Logger.getLogger(NodePoolComputer.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            cleanupLock.unlock();
        }
        return result;
    }


    /**
     * Get the Job associated with this computer
     * @return NodePoolJob object
     */
    public NodePoolJob getJob(){
        return this.nodePoolJob;
    }

    public final void setNodePoolNode(NodePoolNode npn) {
        this.nodePoolNode = npn;
    }

    public NodePoolNode getNodePoolNode() {
        return nodePoolNode;
    }

    @Override
    public HttpResponse doDoDelete() throws IOException {
        try {
            if (nodePoolNode == null) {
                LOG.log(Level.WARNING, "Nodepool reference is null - unable to release nodepool reference.");
            }
            else {
                nodePoolNode.release();
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, ex.getClass().getSimpleName() + " exception while releasing nodepool node: " +
                    (nodePoolNode == null ? "nodepool node name is null" : nodePoolNode.getName()) + "." +
                    " Message: " + ex.getLocalizedMessage());
        }

        return super.doDoDelete();
    }

    @Override
    public String toString() {
        if (nodePoolNode != null) {
            return nodePoolNode.getName();
        } else if (name != null) {
            return name;
        } else {
            return super.toString();
        }
    }

    @Override
    public String getDisplayName() {
        return toString();
    }

    private NodePoolJob getNodePoolJob(){
        return nodePoolJob;
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        NodePoolComputer c = (NodePoolComputer) executor.getOwner();
        LOG.log(Level.FINE, "Starting task {0} on NodePoolComputer {1}", new Object[]{task.getFullDisplayName(), c});
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);

        LOG.log(Level.FINE, "Task " + task.getFullDisplayName() + " completed normally");
        postBuildCleanup(executor, task);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);

        LOG.log(Level.WARNING, "Task " + task.getFullDisplayName() + " completed with problems: ", problems);
        postBuildCleanup(executor, task);
    }

    private void postBuildCleanup(Executor executor, Queue.Task task) {
        final NodePoolComputer c = (NodePoolComputer) executor.getOwner();
        final NodePoolSlave slave = (NodePoolSlave) c.getNode();

        if (slave == null) {
            LOG.log(Level.WARNING, "The computer : " + c + " has a null slave associated with it.");
            return;
        }

        // indicate that the agent has been programmatically suspended.  this will safely mark the computer as
        // unavailable for tasks before the executor is freed up and Jenkins attempts to send it another task.
        c.setAcceptingTasks(false);

        if (slave.isHeld()) {
            // instead of deleting the slave node, put it into a "hold" state for human examination
            // it will need to be manually deleted later

            final Queue.Executable executable = executor.getCurrentExecutable();
            String jobIdentifier;

            if (executable == null) {
                LOG.log(Level.WARNING, "No executable associated with executor: " + executor
                        + " on computer " + c);
                jobIdentifier = "no executable?";
            } else {
                jobIdentifier = executable.toString();
            }

            try {
                if (nodePoolNode != null) {
                    // this can be null because stale computers are cleaned up asynchronously on jenkins restart and
                    // the node object is not serialized.
                    LOG.log(Level.INFO, "Holding node " + slave.getDisplayName() + " (" + jobIdentifier + ")");
                    nodePoolNode.hold(jobIdentifier);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to hold node: " + slave.getDisplayName()
                        + ".  NodePool may delete it.", e);
            }
        } else {
            LOG.log(Level.FINE, "postBuildCleanup invoked on node " + slave.getDisplayName() +
                    ". Calling delete node...");
            deleteNodePoolComputer(c, task);
        }

    }

    void deleteNodePoolComputer(NodePoolComputer computer, Queue.Task task) {

        LOG.log(Level.INFO, "Deleting NodePoolNode: {0} after task: {1}", new Object[]{computer,
                task.getFullDisplayName()});
        try{
            Computer.threadPoolForRemoting.submit(() -> {
                // Wait for other processes that may still be cleaning up
                // as its likely a build has just finished. Not waiting
                // here leads to IOExceptions in the log.
                // This time is essentially free as its in a background
                // thread.
                try {
                    Thread.sleep(30000L);
                } catch (InterruptedException ex) {
                    // meh we woke up
                }

                Jenkins jenkins = Jenkins.getInstance();
                // Lock to prevent cleanup races with the Janitor thread
                Lock cleanupLock = NodePools.get().getCleanupLock();

                // semantics are such that unlock fails
                // if lock is not held
                cleanupLock.lock();
                try {
                    // Check that the computer is still registered with Jenkins
                    // If it's already been removed by the Janitor then npc
                    // will be null.
                    NodePoolComputer npc = (NodePoolComputer) jenkins.getComputer(this.name);
                    if (npc == null) {
                        LOG.log(Level.INFO, "Computer already unregistered, skipping end of build cleanup");
                    } else if (computer == null) {
                        LOG.info("Not cleaning null computer");
                    } else {
                        computer.doDoDelete();
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING,
                            String.format("%s error while deleting node (in threadPoolForRemtoing). Message: %s. Was it already deleted?",
                                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
                }finally{
                    cleanupLock.unlock();
                }
            });
        } catch (Exception ex){
             LOG.log(Level.WARNING,
                     String.format("%s error while deleting node (in taskCompleted handler). Message: %s. Was it already deleted?",
                                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
        }
    }
}
