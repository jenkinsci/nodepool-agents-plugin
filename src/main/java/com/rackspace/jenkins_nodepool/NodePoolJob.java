package com.rackspace.jenkins_nodepool;

import hudson.model.Label;
import hudson.model.Queue;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrap a Jenkins task so we can track some information over the NodePool processing cycle.
 */
public class NodePoolJob {

    private Label label; // jenkins label
    private final Queue.Task task;
    private long taskId;

    private List<Attempt> attempts = new ArrayList<Attempt>();  // attempts to provision

    NodePoolJob(Label label, Queue.Task task, long taskId) {
        this.label = label;
        this.task = task;
    }

    Label getLabel() {
        return this.label;
    }

    Queue.Task getTask() {
        return this.task;
    }

    long getTaskId() { return this.taskId; }

    void addAttempt(NodeRequest request) {
        attempts.add(new Attempt(request));
    }

    void failAttempt(Exception e) {
        // mark current attempt as a failure
        getCurrentAttempt().fail(e);
    }

    void succeed() {
        getCurrentAttempt().succeed();
    }

    private Attempt getCurrentAttempt() {
        final int sz = attempts.size();
        return attempts.get(sz-1);
    }

    public List<Attempt> getAttempts() { return attempts; }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof NodePoolJob)) {
            return false;
        }

        final NodePoolJob job = (NodePoolJob) obj;
        return job.getTaskId() == taskId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(taskId);
    }


    static class Attempt {
        final NodeRequest request;
        Exception e;

        long startTime;
        long finishTime;

        Attempt(NodeRequest request) {
            this.request = request;

            this.startTime = System.currentTimeMillis();
        }

        void fail(Exception e) {
            this.e = e;
            setFinishTime();
        }

        void succeed() {
            setFinishTime();
        }

        void setFinishTime() {
            this.finishTime = System.currentTimeMillis();
        }

        long getDurationSeconds() {
            return finishTime - startTime;
        }

        public boolean isDone() {
            return finishTime != 0L;
        }

        public boolean isSuccess() {
            return isDone() && e == null;
        }
    }
}
