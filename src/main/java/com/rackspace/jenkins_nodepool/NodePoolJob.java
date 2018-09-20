package com.rackspace.jenkins_nodepool;

import hudson.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Wrap a Jenkins task so we can track some information over the NodePool processing cycle.
 */
public class NodePoolJob {

    private Label label; // jenkins label
    private final Queue.Task task;
    private final long queueID;
    private final WorkflowRun run;
    private final Job job;
    private NodePoolNode nodePoolNode = null;
    private NodePoolSlave nodePoolSlave = null;

    /**
     * A list of Attempt objects to hold the metadata associated with each provision attempt.
     */
    private List<Attempt> attempts = new ArrayList<>();

    NodePoolJob(Label label, Queue.Task task, long queueID) {
        this.label = label;
        this.queueID = queueID;
        this.task = task;
        this.run = NodePoolUtils.getRunForQueueTask(task);
        this.job = run.getParent();
        logToBoth("NodepoolJob "+this.toString()+" tracking node usage with label: "+this.label.getDisplayName());
    }


    /**
     * Get string identifying the build that created this task
     * @return jobName#buildNum
     */
    public String getBuildId(){
        return run.getExternalizableId();
    }

    public String getOverviewString(){
        return String.format("Queue Item: %s, %s Build: %s-%s, %s",
            queueID,// Queue ID
            task.toString(),// Queue Status
            job.getDisplayName(),// Job Name
            run.getNumber(), // Build Number
            run.getBuildStatusSummary().message// Build Status
        );
    }

    public NodePoolSlave getNodePoolSlave(){
        return nodePoolSlave;
    }

    public NodePoolNode getNodePoolNode() {
        return nodePoolNode;
    }

    public void setNodePoolNode(NodePoolNode nodePoolNode) {
        this.nodePoolNode = nodePoolNode;
    }

    public void setNodePoolSlave(NodePoolSlave nodePoolSlave) {
        this.nodePoolSlave = nodePoolSlave;
    }

    public Run getRun(){
        return run;
    }

    public List<NodeRequest> getRequests(){
        return attempts
                .stream()
                .map(a -> a.getRequest())
                .collect(Collectors.toList());
    }

    public Boolean hasRequest(NodeRequest request){
        return getRequests()
                .stream()
                .anyMatch(r -> r.equals(request));
    }

    public Label getLabel() {
        return this.label;
    }

    public Queue.Task getTask() {
        return this.task;
    }

    public long getTaskId() {
        return this.queueID;
    }

    /**
     * Write message to system log and build log with level INFO.
     * @param msg the message to log
     */
    public void logToBoth(String msg){
        logToBoth(msg, Level.INFO);
    }

    /**
     * Writes log message to the Jenkins System log and the relevant build log
     * @param msg the message to log
     * @param level the log level to use for the system log
     */
    public void logToBoth(String msg, Level level){
        Logger.getLogger(NodePoolJob.class.getName()).log(
                level,
                msg);
        if (run != null){
            WorkflowRun wr = (WorkflowRun) run;
            try {
                FlowExecutionOwner feo = wr.asFlowExecutionOwner();
                // findbugs
                if (feo == null){
                    return;
                }
                TaskListener tl = feo.getListener();
                tl.getLogger().println(msg);
            } catch (Exception ex) {
                //Deliberately wide catch, so NPEs are included.
                Logger.getLogger(NodePoolJob.class.getName()).log(Level.SEVERE,
                        "Failed to log to build console.");
            }
        }
    }

    void addAttempt(NodeRequest request) {
        attempts.add(new Attempt(request));
        logToBoth("Nodepool Node Requested: "+request.toString());
    }

    void failAttempt(Exception e) {
        logToBoth("Nodepool Node Requested Failed: "+e.toString());
        // mark current attempt as a failure
        getCurrentAttempt().fail(e);
    }

    void succeed() {
        logToBoth("Nodepool Node Requested Succeded: "
                +getCurrentAttempt().getRequest().toString());
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
        if (run ==  null){
            return "";
        } else {
            return "#" + String.valueOf(run.number);
        }
    }

    /**
     * Returns the result associated with the current attempt.
     *
     * @return the result associated with the current attempt.
     */
    public NodeRequestAttemptState getResult() {
        return getCurrentAttempt().getResult();
    }

    /**
     * A status enumeration to hold the attempt status.
     */
    public enum NodeRequestAttemptState {
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
        return getTaskId() == that.getTaskId() &&
                Objects.equals(label, that.label) &&
                Objects.equals(task, that.task) &&
                Objects.equals(getBuildNumber(), that.getBuildNumber());
    }

    /**
     * Returns the hash code associated with this object.
     *
     * @return the hash code associated with this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(label, task, getTaskId(), getBuildNumber());
    }

    /**
     * Returns the string representation of this object.
     *
     * @return the string representation of this object.
     */
    @Override
    public String toString() {
        return "NodePoolJob[taskId=" + getTaskId() + ", task=" + task.getFullDisplayName() + ", label=" + label + "]";
    }

}
