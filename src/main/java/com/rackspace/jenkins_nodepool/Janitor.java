package com.rackspace.jenkins_nodepool;

import com.google.gson.Gson;
import com.rackspace.jenkins_nodepool.models.NodeModel;
import com.rackspace.jenkins_nodepool.models.NodeRequestModel;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.security.ACLContext;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.apache.curator.framework.CuratorFramework;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import static java.lang.String.format;
import static java.util.logging.Level.*;

/**
 * This class implements the logic for the background janitor thread.
 */

class Janitor implements Runnable {

    /**
     * Our class logger.
     */
    private static final Logger LOG = Logger.getLogger(JanitorialListener.class.getName());

    /**
     * Janitor check interval in seconds.
     */
    private static final Long SLEEP_SECS_DEFAULT = 60L;

    private long sleepMilliseconds;

    /**
     * JSON reader/writer helper
     */
    private Gson gson = new Gson();

    /**
     * A reference to the Zookeeper connection framework
     */
    private CuratorFramework conn;

    Janitor() {
        // by default, sleep for 60 seconds between cleaning attempts.
        final String propertyKey = Janitor.class.getName() + ".sleep_seconds";
        String sleepSeconds = System.getProperty(propertyKey, SLEEP_SECS_DEFAULT.toString());

        // Convert to a long value and milliseconds - use default if a format error
        try {
            sleepMilliseconds = Long.parseLong(sleepSeconds) * 1000L;
        } catch (NumberFormatException nfe) {
            LOG.log(Level.WARNING, format("Unable to convert system property '%s' with value '%s' to milliseconds. " +
                    "Using default value: %d ms.", propertyKey, sleepSeconds, SLEEP_SECS_DEFAULT * 1000L));
            sleepMilliseconds = SLEEP_SECS_DEFAULT * 1000L;
        }
    }

    @Override
    public void run() {

        LOG.log(INFO, "Janitor thread running...");

        try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
            runAsSystem();
        } catch (Exception e) {
            LOG.log(SEVERE, format("Caught exception while escalating privileges for the Janitor thread. Message: %s",
                    e.getLocalizedMessage()));
        }
        LOG.log(SEVERE, "Janitor Thread Exited - this shouldn't happen, resources may leak.");
    }

    private CuratorFramework getConn() throws NodePoolException{
        // Grab a reference to the NodePool ZK connection
        CuratorFramework conn;
        if (NodePools.get().getNodePools().isEmpty()) {
            throw new NodePoolException("No Nodepools Configured, can't obtain zookeeper connection.");
        } else {
            conn = NodePools.get().getNodePools().get(0).getConn();
        }
        return conn;
    }

    /**
     * Run with system permissions already set.
     */
    private void runAsSystem() {
        while (true) {
            try {
                Thread.sleep(sleepMilliseconds);
                clean();
            } catch (Exception e) {
                LOG.log(WARNING, "Cleanup failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Examines the nodes defined in both Jenkins and the NodePool service.
     * <p>
     * Nodes are removed under these conditions:
     * <p><ul>
     * <li>The node in question is no longer in NodePool/ZooKeeper</li>
     * <li>The node has a invalid label</li>
     * <li>The node was previously used for a build</li>
     * <li>The node build is complete and is not held</li>
     * </ul>
     */
    private void clean() throws NodePoolException {
        LOG.log(FINEST, "--------------------- Janitor scanning -------------------");
        // This cleanup doesn't require a zookeeper connection, so run before
        // getConn() which may throw NodePoolException. If it does, it will
        // be handled by runAsSystem.
        cleanJenkinsNodes();
        conn = getConn();
        showZookeeperRequests();
        cleanZookeeperNodes();
        LOG.log(FINEST, "--------------------- Janitor scanning done --------------");
    }

    /**
     * Examines all the nodes Jenkins currently has, and looks for NodePool nodes that should be removed from
     * Jenkins.
     * <p>
     * Nodes are removed under these conditions:
     * <p><ul>
     * <li>The node in question is no longer in NodePool/ZooKeeper</li>
     * <li>The node has a invalid label</li>
     * <li>The node was previously used for a build</li>
     * </ul>
     */
    private void cleanJenkinsNodes() {
        try {

            final Jenkins jenkins = Jenkins.getInstanceOrNull();
            // This should never happen, but would return null if the service has not been started, or was already shut down,
            // or we are running on an unrelated JVM, typically an agent.
            if (jenkins == null) {
                LOG.log(WARNING, "Error - unable to fetch a reference to a Jenkins instance - we should be running on the master. Unable to run Janitor cleanup.");
                return;
            }

            LOG.log(FINEST, "Reviewing Jenkins nodes...");
            if (jenkins.getNodes().isEmpty()) {
                LOG.log(FINEST, "No Jenkins nodes registered.");
            } else {
                for (Node node : jenkins.getNodes()) {
                    if (!(node instanceof NodePoolSlave)) {
                        continue;
                    }

                    final NodePoolSlave nodePoolSlave = (NodePoolSlave) node;
                    NodePoolJob nodePoolJob = nodePoolSlave.getJob();

                    LOG.log(FINEST, "Evaluating NodePool Node: " + nodePoolSlave);
                    if (nodePoolJob == null) {
                        LOG.log(FINE, format("NodePool Node: %s does not have a job. Cleaning node...", nodePoolSlave));
                        cleanNode(nodePoolSlave, "Job Reference is null");
                        continue;
                    }

                    WorkflowRun run = (WorkflowRun) nodePoolJob.getRun();
                    if (run == null) {
                        LOG.log(WARNING, format("NodePool Node: %s does not have a workflow run object associated with the job. Skipping.", nodePoolSlave));
                        continue;
                    }

                    if (run.isBuilding()) {
                        // The associated build is still executing,
                        // don't remove the node under any circumstances
                        // Timeouts are handled in the jobs, not here.
                        LOG.log(FINEST, format("Node %s is still building a job. Skipping cleanup.", nodePoolSlave));
                    } else if (nodePoolSlave.isHeld()) {
                        // Build has ended, but node could be held.
                        // Grab the hold until time - need to compare it to current time to determine if our hold has expired
                        final long holdUtilEpochMs = ((NodePoolSlave) node).getHoldUnitEpochMs();
                        long now = System.currentTimeMillis();
                        if (now > holdUtilEpochMs) {
                            // Build complete and hold expired, clean node
                            LOG.log(INFO, format(
                                    "Removing held node: %s - job is done and hold has expired - hold until time: %s, current time: %s",
                                    nodePoolSlave,
                                    NodePoolUtils.getFormattedDateTime(holdUtilEpochMs, ZoneOffset.UTC),
                                    NodePoolUtils.getFormattedDateTime(now, ZoneOffset.UTC)));
                            ((NodePoolSlave) node).setHeld(false);
                            cleanNode(nodePoolSlave, "Hold expired");
                        } else {
                            // Build complete, but hold has not expired so retain node
                            LOG.log(FINE, format(
                                    "Skipping held node: %s - job is running: %b, held: %b - hold until time: %s, current time: %s",
                                    nodePoolSlave, nodePoolJob.getRun().isBuilding(), nodePoolSlave.isHeld(),
                                    NodePoolUtils.getFormattedDateTime(holdUtilEpochMs, ZoneOffset.UTC),
                                    NodePoolUtils.getFormattedDateTime(System.currentTimeMillis(), ZoneOffset.UTC)));
                        }
                    } else {
                        // Build complete and node isn't held, clean it.
                        LOG.log(INFO, format("Removing node %s because the build it was created for (%s) is no longer running.",
                                nodePoolSlave, run.getExternalizableId()));
                        cleanNode(nodePoolSlave, "Build Complete");
                    }
                }
            }
        } catch (RuntimeException e) {
            // Explicitly catch Runtime to resolve Findbugs REC_CATCH_EXCEPTION
            throw e;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s error while querying for Jenkins nodes. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
        }
    }

    /**
     * Mark the given slave node as offline and then remove it from Jenkins.
     * <p>
     * If an error occurs, swallow it and assume the cleanup will be tried again later.
     *
     * @param nodePoolSlave the node to remove
     * @param reason        the reason why the node is being removed
     */
    private void cleanNode(NodePoolSlave nodePoolSlave, String reason) {
        // Executed from a thread pool so that
        // the 30s waits don't block the Janitor.
        Computer.threadPoolForRemoting.submit(() -> {
            try {
                // We may select a node for cleanup immediately after a build
                // has completed. In this case there may still be processes
                // communicating with the node. Give those 30s to terminate
                // to prevent IOExceptions in the log.
                // This is run from a background thread, so the wait
                // won't block anything.
                Thread.sleep(30000L);
            } catch (InterruptedException ex) {
                //meh we woke up.
            }

            try {
                NodePoolComputer c = (NodePoolComputer) nodePoolSlave.toComputer();
                NodePoolNode npn = nodePoolSlave.getNodePoolNode();
                if (c != null) {
                    c.doToggleOffline(reason);
                    c.doDoDelete();
                } else {
                    LOG.log(FINE, format("Releasing NodePoolNode with no associated computer %s", npn));
                    // inner try to ensure we attempt removeNode
                    try {
                        npn.release();
                    } catch (Exception e) {
                        LOG.log(FINE, format("%s while attempting to release node %s. Message: %s",
                                e.getClass().getSimpleName(), npn, e.getLocalizedMessage()));
                    }

                    final Jenkins jenkinsInstance = Jenkins.getInstanceOrNull();
                    if (jenkinsInstance != null) {
                        jenkinsInstance.removeNode(nodePoolSlave);
                    } else {
                        LOG.log(WARNING, "Error - unable to fetch a reference to a Jenkins instance - unable to remove node");
                    }
                }
            } catch (Exception e) {
                LOG.log(FINE, format("%s while attempting to clean node %s. Message: %s",
                        e.getClass().getSimpleName(), nodePoolSlave, e.getLocalizedMessage()));
            }
        });
    }

    /**
     * Check if the given slave node has a label that doesn't match any configured NodePool clusters.
     *
     * @param nodePoolSlave the node to check
     * @return true if the label is invalid
     */
    boolean hasInvalidLabel(NodePoolSlave nodePoolSlave) {
        final String nodeLabel = nodePoolSlave.getLabelString();
        final List<NodePool> nodePools = NodePools.get().nodePoolsForLabel(new LabelAtom(nodeLabel));
        return (nodePools == null || nodePools.isEmpty());
    }

    /**
     * Shows the current requests in Zookeeper
     */
    private void showZookeeperRequests() {
        LOG.log(FINEST, "Looking for requests...");

        try {
            if (NodePools.get().getNodePools().isEmpty()) {
                LOG.log(FINEST, "No NodePools configured - unable to query for requests.");
            } else {
                final String zkNodesRequestRootPath = format("/%s", NodePools.get().getNodePools().get(0).getRequestRoot());
                final List<String> nodesRequestPaths = conn.getChildren().forPath(zkNodesRequestRootPath);
                if (nodesRequestPaths.isEmpty()) {
                    LOG.log(FINEST, "No Node Requests registered.");
                } else {
                    for (String nodeRequestPath : nodesRequestPaths) {
                        // Read and parse the data into a POJO model
                        final String path = format("%s/%s", zkNodesRequestRootPath, nodeRequestPath);
                        final byte[] data = conn.getData().forPath(path);
                        // Check to see if we were able to load any data
                        if (data == null || data.length == 0) {
                            LOG.log(WARNING, format("Unable to load data at node: %s. Data is is null or empty.", path));
                        } else {
                            final NodeRequestModel model = gson.fromJson(new String(data, StandardCharsets.UTF_8), NodeRequestModel.class);
                            if (model == null) {
                                LOG.log(WARNING, format("Unable to load data model at node: %s. Model is null or empty.", path));
                            } else {
                                LOG.log(FINEST, format("Node Request, path: %s, state: %s, requestor: %s, build id: %s",
                                        path,
                                        model.getState(),
                                        model.getRequestor(),
                                        model.getBuild_id()));
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // Explicitly catch Runtime to resolve Findbugs REC_CATCH_EXCEPTION
            throw e;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s error while querying for NodePool requests and nodes. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
        }
    }

    /**
     * Cleans any stale zookeeper nodes that have been used and are currently not running.
     */
    private void cleanZookeeperNodes() {
        LOG.log(FINEST, "Looking for Zookeeper nodes...");

        try {
            if (NodePools.get().getNodePools().isEmpty()) {
                LOG.log(FINEST, "No nodepools configured - unable to query for zookeeper nodes.");
            } else {
                // Scan for nodes listed in Zookeeper (not in Jenkins)
                final String zkNodesRootPath = format("/%s", NodePools.get().getNodePools().get(0).getNodeRoot());
                final List<String> nodesPaths = conn.getChildren().forPath(zkNodesRootPath);
                if (nodesPaths.isEmpty()) {
                    LOG.log(FINEST, "No NodePool nodes registered in Zookeeper. Expecting reserved nodes (min-ready).");
                } else {
                    for (String nodePath : nodesPaths) {
                        // Read and parse the data into a data model
                        final String path = format("%s/%s", zkNodesRootPath, nodePath);
                        final byte[] data = conn.getData().forPath(path);
                        // Check to see if we were able to load any data
                        if (data == null || data.length == 0) {
                            LOG.log(WARNING, format("Unable to load data at node: %s. Data is is null or empty.", nodePath));
                            // Let's skip for now - not much we can do other than delete the znode. It could be in the
                            // process of being created and the data hasn't been written/saved yet...
                            continue;
                        }

                        // Convert the JSON data to a POJO model
                        final NodeModel model = gson.fromJson(new String(data, StandardCharsets.UTF_8), NodeModel.class);
                        if (model == null) {
                            LOG.log(WARNING, format("Unable to load data model at node: %s. Model is null or empty.", nodePath));
                            continue;
                        }

                        // If we have a build id, then we should get a Run object - will be null if node isn't allocated
                        // to a job yet (on standby/min-ready)
                        Run run = null;
                        if (model.getBuild_id() != null) {
                            run = Run.fromExternalizableId(model.getBuild_id());
                        }

                        if (model.getAllocated_to() == null) {
                            LOG.log(FINEST, format("Reserved Node: %s, region: %s, held: %b, allocated to: %s",
                                    nodePath,
                                    model.getRegion(),
                                    (model.getHold_job() != null),
                                    model.getAllocated_to()));
                        } else if (run != null && model.getBuild_id() != null && run.isBuilding()) {
                            // Node is being used: we have an external id, a build id, and it's currently building
                            LOG.log(FINE, format("In-Use Node: %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s",
                                    nodePath,
                                    model.getRegion(),
                                    (model.getHold_job() != null),
                                    model.getAllocated_to(),
                                    model.getBuild_id(),
                                    run.isBuilding()));
                        } else if (run != null && model.getBuild_id() != null && !run.isBuilding() && model.getHold_job() != null) {
                            LOG.log(FINEST, format("Retired Node (held): %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s.",
                                    nodePath,
                                    model.getRegion(),
                                    (model.getHold_job() != null),
                                    model.getAllocated_to(),
                                    model.getBuild_id(),
                                    run.isBuilding()));
                        } else if (run != null && model.getBuild_id() != null && !run.isBuilding() && model.getHold_job() == null) {
                            LOG.log(FINEST, format("Retired Node (not held): %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s - should REMOVE this node.",
                                    nodePath,
                                    model.getRegion(),
                                    (model.getHold_job() != null),
                                    model.getAllocated_to(),
                                    model.getBuild_id(),
                                    run.isBuilding()));

                            // Toggle the state to used
                            model.setState(NodePoolState.USED);

                            // Save the changes back to ZK
                            final String jsonStringModel = gson.toJson(model, NodeModel.class);
                            conn.setData().forPath(path, jsonStringModel.getBytes(StandardCharsets.UTF_8));
                            LOG.log(FINEST, format("Retired Node (not held): %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s - changed state to USED - launcher should remove.",
                                    nodePath,
                                    model.getRegion(),
                                    (model.getHold_job() != null),
                                    model.getAllocated_to(),
                                    model.getBuild_id(),
                                    run.isBuilding()));
                        } else {
                            LOG.log(WARNING, format("Unknown/Unsupported State of Node: %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s",
                                    nodePath,
                                    model.getRegion(),
                                    (model.getHold_job() != null),
                                    model.getAllocated_to(),
                                    model.getBuild_id(),
                                    (run == null ? "null" : run.isBuilding())));
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // Explicitly catch Runtime to resolve Findbugs REC_CATCH_EXCEPTION
            throw e;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s error while querying for Zookeeper nodes. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
        }
    }
}
