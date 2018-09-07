package com.rackspace.jenkins_nodepool;

import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Level;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * This class implements the logic for the background janitor thread.
 */

class Janitor implements Runnable {

    /**
     * Our class logger.
     */
    private static final Logger LOG = Logger.getLogger(JanitorialListener.class.getName());
    private static final String SLEEP_SECS_DEFAULT = "60";

    private long sleepMilliseconds;

    Janitor() {
        // by default, sleep for 60 seconds between cleaning attempts.
        final String sleepSeconds = System.getProperty(Janitor.class.getName() + ".sleep_seconds",
                SLEEP_SECS_DEFAULT);
        sleepMilliseconds = Integer.parseInt(sleepSeconds) * 1000;
    }

    @Override
    public void run() {

        LOG.log(Level.INFO, "Janitor thread running...");

        try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
            runAsSystem();
        } catch (Exception e){
            LOG.log(Level.SEVERE, "Caught exception while escalating privileges for the Janitor thread:"+e.getMessage());
        }
        LOG.log(Level.SEVERE, "Janitor Thread Exited - this shouldn't happen, resources may leak.");
    }

    /**
     * Run with system permissions already set.
     */
    private void runAsSystem() {
        while (true) {
            try {
                clean();
                Thread.currentThread().sleep(sleepMilliseconds);
            } catch (InterruptedException e) {
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
            LOG.log(Level.FINE, "Evaluating NodePool Node: " + nodePoolSlave);

            // there are several scenarios where we want to scrub this node:
            if (isMissing(nodePoolSlave)) {
                // The node has disappeared/failed to launch and is offline
                LOG.log(Level.INFO, "Removing node " + nodePoolSlave + " because it is offline or has been "
                        + "deleted/re-assigned in NodePool");
                cleanNode(nodePoolSlave, "Offline/deleted/re-assigned");

            } else if (hasInvalidLabel(nodePoolSlave)) {
                // The node is for a label that we no longer have configured as a "NodePool" in Jenkins:
                LOG.log(Level.INFO, "Removing node " + nodePoolSlave + " because its label doesn't match any "
                        + "configured NodePool.");
                cleanNode(nodePoolSlave, "Invalid label");

            } else if (nodePoolSlave.isHeld()) {
                // Grab the hold until time - need to compare it to current time to determine if our hold has expired
                final long holdUtilEpochMs = ((NodePoolSlave) node).getHoldUnitEpochMs();
                long now = System.currentTimeMillis();
                if (nodePoolSlave.completedABuild() && now > holdUtilEpochMs) {
                    // Hold expired
                    LOG.log(Level.INFO, String.format(
                            "Removing held node: %s - job is done and hold has expired - hold until time: %s, current time: %s",
                            nodePoolSlave,
                            NodePoolUtils.getFormattedDateTime(holdUtilEpochMs, ZoneOffset.UTC),
                            NodePoolUtils.getFormattedDateTime(now, ZoneOffset.UTC)));
                    // Update the flag and clean up the node.
                    ((NodePoolSlave) node).setHeld(false);
                    cleanNode(nodePoolSlave, "Hold expired");
                } else {
                    // Hold has not expired so skip.
                    LOG.log(Level.FINE, String.format(
                            "Skipping held node: %s - job is running: %b, held: %b - hold until time: %s, current time: %s",
                            nodePoolSlave, !nodePoolSlave.completedABuild(), nodePoolSlave.isHeld(),
                            NodePoolUtils.getFormattedDateTime(holdUtilEpochMs, ZoneOffset.UTC),
                            NodePoolUtils.getFormattedDateTime(System.currentTimeMillis(), ZoneOffset.UTC)));
                }
            } else if (nodePoolSlave.completedABuild()) {
                // The node has previously done work and failed to have been scrubbed
                LOG.log(Level.INFO, "Removing node " + nodePoolSlave + " because it has already been used to "
                        + "run jobs.");
                cleanNode(nodePoolSlave, "Already used");
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
    void cleanNode(NodePoolSlave nodePoolSlave, String reason) {
        try {
            NodePoolComputer c = (NodePoolComputer) nodePoolSlave.toComputer();
            NodePoolNode npn = nodePoolSlave.getNodePoolNode();
            if (c == null) {
                if (npn == null) {
                    LOG.log(WARNING, String.format("Can't cleanup nodePoolSlave that has neither a computer nor node associated with it %s", nodePoolSlave));
                } else {
                    LOG.log(WARNING, String.format("Releasing NodePoolNode with no associated computer %s", npn));
                    npn.release();
                }
            } else {
                c.doToggleOffline(reason);
                c.doDoDelete();
            }
        } catch (Exception e) {
            LOG.log(WARNING, String.format("%s while attempting to clean node %s. Message: %s",
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
        return (nodePools == null || nodePools.size() == 0);
    }

    /**
     * Check if the given slave node has disappeared in ZooKeeper
     *
     *
     * @param nodePoolSlave the node to check
     * @return true if the node has disappeared
     */
    private boolean isMissing(NodePoolSlave nodePoolSlave) {
        NodePoolComputer c = (NodePoolComputer) nodePoolSlave.toComputer();
        if (c == null || c.isOffline()) {
            // agent is offline - confirm that this node still exists in ZK/NP:
            final NodePoolNode nodePoolNode = nodePoolSlave.getNodePoolNode();

            try {
                if (nodePoolNode == null) {
                    // node is unknown
                    LOG.log(Level.FINE, "Slave " + nodePoolSlave + " has no associated Node record.");
                    return true;
                }
                if (!nodePoolNode.exists()) {
                    // Corresponding ZNode is gone, this is definitely an orphan record in Jenkins
                    LOG.log(Level.FINE, "Slave " + nodePoolSlave + " no longer exists in ZK.");
                    return true;
                }
            } catch (Exception e) {
                LOG.log(WARNING, "Failed to check if node " + nodePoolNode + " exists.", e);
                return false;
            }
        }
        return false;
    }

}

