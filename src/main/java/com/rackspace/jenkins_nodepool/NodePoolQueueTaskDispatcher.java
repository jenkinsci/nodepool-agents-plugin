/*
 * The MIT License
 *
 * Copyright 2018 Rackspace.
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
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Ensure that nodepool nodes only execute the one task they were
 * created for. All other tasks are rejected.
 * @author Rackspace
 */
@Extension
public class NodePoolQueueTaskDispatcher extends QueueTaskDispatcher {


    /**
     * Check if a node can take a task.
     * This is the non-deprecated signature, though its hard to
     * test, so the logic is in cantake(Node node, Queue.Task task)
     * @param node The node which might execute the task
     * @param item An item that conatins a task reference to be executed
     * @return null if the task can be allocated to the node, otherwise a CauseOfBlockage
     */
    @Override
    public CauseOfBlockage canTake(Node node, BuildableItem item){
        return canTake(node, item.task);
    }

    @Override
    public CauseOfBlockage canTake(Node node, Queue.Task task) {
        if(!(node instanceof NodePoolSlave)){
            // not a nodepool node, so we don't block any tasks
            return null;
        } else {
            // safe cast due to if
            NodePoolSlave nps = (NodePoolSlave) node;
            WorkflowRun itemRun = NodePoolUtils.getRunForQueueTask(task);
            WorkflowRun nodeRun = (WorkflowRun)nps.getNodePoolJob().getRun();

            if (itemRun.equals(nodeRun)){
                // this node was allocated for this task, approve allocation
                return null;
            } else {
                // This is a nodepool node, but it was created for a different
                // build, so block this task allocation
                return new NodeCreatedForAnotherBuildCauseOfBlockage();
            }
        }
    }

    public static class NodeCreatedForAnotherBuildCauseOfBlockage extends CauseOfBlockage{

        @Override
        public String getShortDescription() {
            return "Attempting to assign a task to a NodePool node that was created for a different build";
        }

    }


}
