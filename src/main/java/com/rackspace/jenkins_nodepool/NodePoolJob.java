package com.rackspace.jenkins_nodepool;

import hudson.model.*;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wrap a Jenkins task so we can track some information over the NodePool processing cycle.
 */
public class NodePoolJob {

    private Label label; // jenkins label
    private final Queue.Task task;
    private long taskId;
    private Integer buildNumber;

    private List<Attempt> attempts = new ArrayList<Attempt>();  // attempts to provision

    NodePoolJob(Label label, Queue.Task task, long taskId) {
        this.label = label;
        this.task = task;
        this.taskId = taskId;
        this.buildNumber = null;
    }

    @Override
    public String toString() {
        return "NodePoolJob[taskId=" + taskId + ", task=" + task.getFullDisplayName() +
                ", label=" + label +"]";

    }

    public Label getLabel() {
        return this.label;
    }

    public Queue.Task getTask() {
        return this.task;
    }

    public long getTaskId() {
        return this.taskId;
    }

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

    public boolean isDone() {
        return getCurrentAttempt().isDone();
    }

    public boolean isSuccess() {
        return getCurrentAttempt().isSuccess();
    }

    public String getDurationSeconds() {
        try {
            final long start = attempts.get(0).startTime;

            long end;
            if (isDone()) {
                end = getCurrentAttempt().finishTime;
            } else {
                end = System.currentTimeMillis();
            }

            return Long.toString((end - start)/ 1000L);

        } catch (IndexOutOfBoundsException e) {
            // no attempts made yet
            return null;
        }

    }

    public NodePool getNodePool() {
        try {
            return getCurrentAttempt().request.nodePool;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Get the jenkins build number for the project, if it's known.
     *
     * Builds that are not yet "running" do not have a build number.
     *
     * @return build number of Jenkins project
     */
    public String getBuildNumber() {
        if (buildNumber == null) {
            return "";
        } else {
            return "#" + String.valueOf(buildNumber);
        }
    }

    public Status getResult() {
        return getCurrentAttempt().getResult();
    }

    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    public enum Status {
        INPROGRESS,
        SUCCESS,
        FAILURE
    }

    public static class Attempt {
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
            long end = finishTime;
            if (end == 0) {
                end = System.currentTimeMillis();
            }

            final long millis = end - startTime;
            return millis / 1000L;
        }

        public String getError() {
            if (e == null) {
                return null;
            } else {
                return ExceptionUtils.getStackTrace(e);
            }
        }

        public boolean isDone() {
            return finishTime != 0L;
        }

        public boolean isFailure() {
            return e != null;
        }

        public boolean isSuccess() {
            return isDone() && e == null;
        }

        public Status getResult() {
            if (!isDone()) {
                return Status.INPROGRESS;
            } else if (isSuccess()){
                return Status.SUCCESS;
            } else {
                return Status.FAILURE;
            }
        }

        /**
         * Get nodes allocated from a node request
         * @return list of node names
         */
        public List<String> getNodes() {
            return request.getAllocatedNodeNames();
        }

        @Override
        public String toString() {
            return "[result="+getResult() + ", duration=" + getDurationSeconds() + " secs, nodes="
                    + getNodes() + "]";
        }
    }
}
