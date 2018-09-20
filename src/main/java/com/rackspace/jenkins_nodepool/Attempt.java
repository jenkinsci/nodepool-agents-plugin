package com.rackspace.jenkins_nodepool;

import org.apache.commons.lang.exception.ExceptionUtils;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.List;

public final class Attempt {
    /**
     * The node request object instance associated with this attempt.
     */
    private final NodeRequest request;

    private Exception e;

    private final long startTime;
    private long finishTime = 0L;

    /**
     * Creates a new NodeRequest attempt object.
     *
     * @param request the NodePool request object
     */
    public Attempt(final NodeRequest request) {
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Returns a reference to the node request object for this attempt.
     *
     * @return a reference to the node request object for this attempt.
     */
    public NodeRequest getRequest() {
        return request;
    }

    public void fail(final Exception e) {
        this.e = e;
        setFinishTime();
    }

    public void succeed() {
        setFinishTime();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime() {
        this.finishTime = System.currentTimeMillis();
    }

    public long getDurationSeconds() {
        if (finishTime == 0) {
            return (System.currentTimeMillis() - startTime) / 1000L;
        } else {
            return (finishTime - startTime) / 1000L;
        }
    }

    /**
     * Returns the duration as a formatted string as: hh:mm:ss.
     *
     * @return the duration as a formatted string as: hh:mm:ss
     */
    public String getDurationFormatted() {
        final long seconds = getDurationSeconds();
        return String.format("%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60);
    }

    /**
     * If set, returns the attempt error string formatted stack trace, otherwise returns null if no error.
     *
     * @return the attempt error string formatted stack trace, otherwise returns null if no error.
     */
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

    public NodePoolJob.NodeRequestAttemptState getResult() {
        if (!isDone()) {
            return NodePoolJob.NodeRequestAttemptState.INPROGRESS;
        } else if (isSuccess()) {
            return NodePoolJob.NodeRequestAttemptState.SUCCESS;
        } else {
            return NodePoolJob.NodeRequestAttemptState.FAILURE;
        }
    }

    /**
     * Get nodes allocated from a node request
     *
     * @return list of node names
     */
    public List<String> getNodes() {
        return request.getAllocatedNodeNames();
    }

    /**
     * Returns the requested nodes as a formatted string.
     *
     * @return the requested nodes as a formatted string.
     */
    public String getNodesAsFormattedString() {
        return String.join(",", request.getAllocatedNodeNames());
    }

    @Override
    public String toString() {
        return "[result=" + getResult() + ", duration=" + getDurationSeconds() + " secs, nodes="
                + getNodes() + "]";
    }

}
