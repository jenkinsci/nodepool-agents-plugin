package com.rackspace.jenkins_nodepool;

import hudson.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Wrap a Jenkins task so we can track some information over the NodePool processing cycle.
 */
public class NodePoolJob {

    private Label label; // jenkins label
    private final Queue.Task task;
    private long taskId;
    private Integer buildNumber;

    /**
     * A list of Attempt objects to hold the metadata associated with each provision attempt.
     */
    private List<Attempt> attempts = new ArrayList<>();

    NodePoolJob(Label label, Queue.Task task, long taskId) {
        this.label = label;
        this.task = task;
        this.taskId = taskId;
        this.buildNumber = null;
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
        return attempts.get(sz - 1);
    }

    public List<Attempt> getAttempts() {
        return attempts;
    }


    public boolean isDone() {
        return getCurrentAttempt().isDone();
    }

    public boolean isSuccess() {
        return getCurrentAttempt().isSuccess();
    }

    public boolean isFailure() {
        return getCurrentAttempt().isFailure();
    }

    /**
     * Returns the total duration in seconds from all the attempts.
     *
     * @return the total duration in seconds from all the attempts.
     */
    public long getDurationSeconds() {
        // Sum up all the attempt durations
        return attempts.stream().map(Attempt::getDurationSeconds).reduce(0L, (x, y) -> x + y);
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
     * Returns the underlying NodePool object assisted with the current request attempt.
     *
     * @return the underlying NodePool object assisted with the current request attempt.
     */
    public NodePool getNodePool() {
        return getCurrentAttempt().getRequest().nodePool;
    }

    /**
     * Get the jenkins build number for the project, if it's known.
     * <p>
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

    /**
     * Returns the result associated with the current attempt.
     *
     * @return the result associated with the current attempt.
     */
    public Status getResult() {
        return getCurrentAttempt().getResult();
    }

    /**
     * Sets the build number value.
     *
     * @param buildNumber the build number value
     * @throws IllegalArgumentException if the buildNumber is negative.
     */
    public void setBuildNumber(int buildNumber) {
        if (buildNumber < 0) {
            throw new IllegalArgumentException("Build number was negative: " + buildNumber);
        }

        this.buildNumber = buildNumber;
    }

    /**
     * A status enumeration to hold the attempt status.
     */
    public enum Status {
        INPROGRESS,
        SUCCESS,
        FAILURE
    }

    /**
     * Returns true if the specified object is equal to this object, false otherwise.
     *
     * @param o the object to test for equality.
     * @return true if the specified object is equal to this object, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final NodePoolJob that = (NodePoolJob) o;
        return taskId == that.taskId &&
                Objects.equals(label, that.label) &&
                Objects.equals(task, that.task) &&
                Objects.equals(buildNumber, that.buildNumber);
    }

    /**
     * Returns the hash code associated with this object.
     *
     * @return the hash code associated with this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(label, task, taskId, buildNumber);
    }

    /**
     * Returns the string representation of this object.
     *
     * @return the string representation of this object.
     */
    @Override
    public String toString() {
        return "NodePoolJob[taskId=" + taskId + ", task=" + task.getFullDisplayName() + ", label=" + label + "]";
    }

}
