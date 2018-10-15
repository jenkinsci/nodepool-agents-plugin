package com.rackspace.jenkins_nodepool;

import com.google.gson.Gson;
import com.rackspace.jenkins_nodepool.models.ZKNodeModel;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.apache.curator.framework.CuratorFramework;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Logger;

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
    private static final String SLEEP_SECS_DEFAULT = "60";

    /**
     * JSON reader/writer helper
     */
    private Gson gson = new Gson();

    private long sleepMilliseconds;

    /**
     * A reference to the Zookeeper connection framework
     */
    private CuratorFramework conn;

    Janitor() {
        // by default, sleep for 60 seconds between cleaning attempts.
        final String sleepSeconds = System.getProperty(Janitor.class.getName() + ".sleep_seconds",
                SLEEP_SECS_DEFAULT);
        sleepMilliseconds = Long.parseLong(sleepSeconds) * 1000L;
    }

    @Override
    public void run() {

        LOG.log(INFO, "Janitor thread running...");

        try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
            // Grab a reference to the NodePool ZK connection
            conn = NodePools.get().getNodePools().get(0).getConn();

            runAsSystem();
        } catch (Exception e) {
            LOG.log(SEVERE, "Caught exception while escalating privileges for the Janitor thread:" + e.getMessage());
        }
        LOG.log(SEVERE, "Janitor Thread Exited - this shouldn't happen, resources may leak.");
    }

    /**
     * Run with system permissions already set.
     */
    private void runAsSystem() {
        //noinspection InfiniteLoopStatement - disable lint warning for infinite loop
        while (true) {
            try {
                Thread.sleep(sleepMilliseconds);
                clean();
                cleanStaleLocks();
            } catch (Exception e) {
                LOG.log(WARNING, "Cleanup failed: " + e.getMessage(), e);
            }
        }
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
    private void clean() {

        final Jenkins jenkins = Jenkins.getInstance();

        for (Node node : jenkins.getNodes()) {
            if (!(node instanceof NodePoolSlave))
                continue;

            final NodePoolSlave nodePoolSlave = (NodePoolSlave) node;
            NodePoolJob nodePoolJob = nodePoolSlave.getJob();

            LOG.log(FINE, "Evaluating NodePool Node: " + nodePoolSlave);
            WorkflowRun run = (WorkflowRun) nodePoolSlave.getJob().getRun();

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

    /**
     * Mark the given slave node as offline and then remove it from Jenkins.
     * <p>
     * If an error occurs, swallow it and assume the cleanup will be tried again later.
     *
     * @param nodePoolSlave the node to remove
     * @param reason        the reason why the node is being removed
     */
    private void cleanNode(NodePoolSlave nodePoolSlave, String reason) {
        try {
            NodePoolComputer c = (NodePoolComputer) nodePoolSlave.toComputer();
            NodePoolNode npn = nodePoolSlave.getNodePoolNode();
            if (c == null) {
                if (npn == null) {
                    LOG.log(WARNING, format("Can't cleanup nodePoolSlave that has neither a computer nor node associated with it %s", nodePoolSlave));
                } else {
                    LOG.log(WARNING, format("Releasing NodePoolNode with no associated computer %s", npn));
                    npn.release();
                }
            } else {
                c.doToggleOffline(reason);
                c.doDoDelete();
            }
        } catch (Exception e) {
            LOG.log(WARNING, format("%s while attempting to clean node %s. Message: %s",
                    e.getClass().getSimpleName(), nodePoolSlave, e.getLocalizedMessage()));
        }
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
     * A routine to review and release any stale locks that Jenkins may have.
     */
    private void cleanStaleLocks() {
        LOG.log(FINE, "--------------------- Janitor scanning -------------------");

        // Only interested in NP nodes
        if (NodePools.get().getNodePools().isEmpty()) {
            LOG.log(WARNING, "Empty NodePool list - unable to query for requests and nodes.");
            return;
        }

        try {
            LOG.log(FINE, "Looking for nodes...");
            final String zkNodesRootPath = format("/%s", NodePools.get().getNodePools().get(0).getNodeRoot());
            final List<String> nodesPaths = conn.getChildren().forPath(zkNodesRootPath);
            if (nodesPaths.isEmpty()) {
                LOG.log(FINE, "No NodePool nodes registered. Expecting reserved nodes (min-ready).");
            } else {
                for (String nodePath : nodesPaths) {
                    // Read and parse the data into a POJO model
                    final String path = format("%s/%s", zkNodesRootPath, nodePath);
                    final byte[] data = conn.getData().forPath(path);
                    final ZKNodeModel model = gson.fromJson(new String(data, StandardCharsets.UTF_8), ZKNodeModel.class);

                    // If we have a build id, then we should get a Run object - will be null if node isn't allocated
                    // to a job yet (on standby/min-ready)
                    Run run = null;
                    if (model.getBuild_id() != null) {
                        run = Run.fromExternalizableId(model.getBuild_id());
                    }

                    if (model.getAllocated_to() == null) {
                        LOG.log(FINE, format("Reserved Node: %s, region: %s, held: %b, allocated to: %s",
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
                        LOG.log(FINE, format("Retired Node (held): %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s.",
                                nodePath,
                                model.getRegion(),
                                (model.getHold_job() != null),
                                model.getAllocated_to(),
                                model.getBuild_id(),
                                run.isBuilding()));
                    } else if (run != null && model.getBuild_id() != null && !run.isBuilding() && model.getHold_job() == null) {
                        LOG.log(FINE, format("Retired Node (not held): %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s - should REMOVE this node.",
                                nodePath,
                                model.getRegion(),
                                (model.getHold_job() != null),
                                model.getAllocated_to(),
                                model.getBuild_id(),
                                run.isBuilding()));

                        // Toggle the state to used
                        model.setState(NodePoolState.USED.getStateString());
                        // Save the changes back to ZK
                        final String jsonStringModel = gson.toJson(model, ZKNodeModel.class);
                        conn.setData().forPath(path, jsonStringModel.getBytes(StandardCharsets.UTF_8));
                        LOG.log(FINE, format("Retired Node (not held): %s, region: %s, held: %b, allocated to: %s, build id: %s, is running: %s - changed state to USED - launcher should remove.",
                                nodePath,
                                model.getRegion(),
                                (model.getHold_job() != null),
                                model.getAllocated_to(),
                                model.getBuild_id(),
                                run.isBuilding()));
                        // TODO: DAD - Is this enough to trigger the NP Launcher to clean up the node??
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
            LOG.log(FINE, "--------------------- Janitor scanning done --------------");
        } catch (RuntimeException e) {
            // Explicitly catch Runtime to resolve Findbugs REC_CATCH_EXCEPTION
            throw e;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s error while querying for NodePool requests and nodes. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
        }
    }
}
