package com.rackspace.jenkins_nodepool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Track historical information about Jenkins jobs handled by the plugin.
 */
public class NodePoolJobHistory implements Iterable<NodePoolJob> {

    private static final int MAX_HISTORY_LENGTH = 250;
    private final Object lock = new Object();

    private final List<NodePoolJob> jobs;
    private final int maxHistoryLength;

    public NodePoolJobHistory() {
        this(MAX_HISTORY_LENGTH);
    }

    public NodePoolJobHistory(final int maxHistoryLenth) {
        this.maxHistoryLength = maxHistoryLenth;
        jobs = new ArrayList<>(maxHistoryLenth);
    }

    /**
     * Add job and prune the historical list if necessary.
     *
     * @param job a job
     */
    public void add(NodePoolJob job) {

        synchronized(lock) {
            jobs.add(0, job);

            while (jobs.size() > maxHistoryLength) {
                final int last = jobs.size() - 1;
                jobs.remove(last);
            }
        }
    }

    @Override
    public Iterator<NodePoolJob> iterator() {
        // copy the underlying list and return -- avoid concurrent modification issues
        List<NodePoolJob> jobsCopy;
        synchronized(lock) {
            jobsCopy = new ArrayList<>(this.jobs);
        }
        return jobsCopy.iterator();
    }

    /**
     * Find job matching taskID
     * @param taskId
     * @return
     */
    NodePoolJob getJob(long taskId) {

        final Iterator<NodePoolJob> iter = iterator();
        while (iter.hasNext()) {
            final NodePoolJob job = iter.next();
            if (job.getTaskId() == taskId) {
                return job;
            }
        }
        return null;
    }

    /**
     * Find job matching request object
     * @param request
     * @return
     */
    NodePoolJob getJob(NodeRequest request){
        return jobs
            .stream()
            .filter(j -> j.hasRequest(request))
            .findFirst()
            .get();
    }

    /**
     * Find jobs matching JobName
     * @param jobName
     * @return
     */
    List<NodePoolJob> getJobs(String jobName){
        return jobs.stream()
                .filter(j -> j.getRun().getDisplayName().equals(jobName))
                .collect(Collectors.toList());
    }

    /**
     * Find jobs matching JobName and Build number
     * There could be multiple as each job represents a usage
     * of a nodepool node and a build may use multiple nodes.
     * @param jobName
     * @param buildNum
     * @return
     */
    List<NodePoolJob> getJobs(String jobName, Integer buildNum){
        return jobs.stream()
                .filter(j -> j.getRun().getDisplayName().equals(jobName) &&
                        j.getRun().getNumber() == buildNum)
                .collect(Collectors.toList());
    }


}
