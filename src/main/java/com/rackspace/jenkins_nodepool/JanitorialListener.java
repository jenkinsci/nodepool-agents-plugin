package com.rackspace.jenkins_nodepool;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.ComputerListener;
import hudson.util.RunList;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * This class listens to Jenkins "Computer" events as a means to start a background thread that will cleanup
 * used or orphaned NodePool slaves.
 */

@Extension
public class JanitorialListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(JanitorialListener.class.getName());

    private static Thread janitorThread;

    /**
     * A simple lock object used to ensure that the janitor thread is initialized only once.
     */
    private static final Object lock = new Object();

    /**
     * Start the Janitor thread when the master node comes online
     *
     * @param c Computer that is coming online
     * @param listener a task listener
     * @throws IOException if an error occurs while bringing the janitor online
     * @throws InterruptedException if an error occurs while bringing the janitor online
     */
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        if (c.getNode() == Jenkins.getInstance().getNode("master")) {
            startJanitor();
        }

    }

    /**
     * Start the Janitor Thread
     */
    public void startJanitor() {
        LOGGER.log(Level.INFO, "Initializing janitor thread");
        synchronized (lock) {
            if (janitorThread == null) {
                janitorThread = new Thread(new Janitor(), "Nodepool Janitor Thread");
                janitorThread.start();
            }
        }
    }
}

/**
 * This class implements the logic for the background janitor thread.
 */

class Janitor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(JanitorialListener.class.getName());
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

        LOGGER.log(Level.INFO, "Janitor thread running...");

        while (true) {
            try {
                clean();
                Thread.currentThread().sleep(sleepMilliseconds);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cleanup failed: " + e.getMessage(), e);
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

            final NodePoolSlave nodePoolSlave = (NodePoolSlave)node;
            LOGGER.log(Level.INFO, "NodePool Node: " + nodePoolSlave);

            // there are several scenarios where we want to scrub this node:
            if (isMissing(nodePoolSlave)) {
                // 1) The node has disappeared/failed to launch and is offline
                LOGGER.log(Level.INFO, "Removing node " + nodePoolSlave + " because it is offline or has been "
                        + "deleted/re-assigned in NodePool");
                cleanNode(nodePoolSlave, "Offline/deleted/re-assigned");

            } else if (hasInvalidLabel(nodePoolSlave)) {
                // 1) The node is for a label that we no longer have configured as a "NodePool" in Jenkins:
                LOGGER.log(Level.INFO, "Removing node " + nodePoolSlave + " because its label doesn't match any "
                        + "configured NodePool.");
                cleanNode(nodePoolSlave, "Invalid label");

            } else if (isPreviouslyUsed(nodePoolSlave)) {
                // 2) The node has previously done work and failed to have been scrubbed
                LOGGER.log(Level.INFO, "Removing node " + nodePoolSlave + " because it has already been used to "
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
     * @param nodePoolSlave  the node to remove
     * @param reason  the reason why the node is being removed
     */
    void cleanNode(NodePoolSlave nodePoolSlave, String reason) {
        try {
            NodePoolComputer c = (NodePoolComputer) nodePoolSlave.toComputer();
            NodePoolNode npn = nodePoolSlave.getNodePoolNode();
            if (c == null) {
                if (npn == null) {
                    LOGGER.log(Level.WARNING, "Can't cleanup nodePoolSlave that has neither a computer nor node associated with it {0}", nodePoolSlave);
                } else {
                    LOGGER.log(Level.WARNING, "Releasing NodePoolNode with no associated computer {0}", npn);
                    npn.release();
                }
            } else {
                c.doToggleOffline(reason);
                c.doDoDelete();
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clean node " + nodePoolSlave, e);
        }
    }

    /**
     * Check if the given slave node has a label that doesn't match any configured NodePool clusters.
     *
     * @param nodePoolSlave  the node to check
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
     * @param nodePoolSlave  the node to check
     * @return true if the node has disappeared
     */
    boolean isMissing(NodePoolSlave nodePoolSlave) {
        NodePoolComputer c = (NodePoolComputer) nodePoolSlave.toComputer();
        if (c == null || c.isOffline()) {
            // agent is offline - confirm that this node still exists in ZK/NP:
            final NodePoolNode nodePoolNode = nodePoolSlave.getNodePoolNode();

            try {
                if (!nodePoolNode.exists()) {
                    // Corresponding ZNode is gone, this is definitely an orphan record in Jenkins
                    LOGGER.log(Level.FINE, "Slave " + nodePoolSlave + " no longer exists in ZK.");
                    return true;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to check if node " + nodePoolNode + " exists.", e);
                return false;
            }

        }
        return false;
    }

    /**
     * We only want to use each node once, so check if the given node has ever *completed* any build.
     *
     * @param nodePoolSlave  the node to check
     * @return true if the node has previously completed a build
     */
    boolean isPreviouslyUsed(NodePoolSlave nodePoolSlave) {

        final NodePoolComputer computer = (NodePoolComputer) nodePoolSlave.toComputer();
        if (computer == null) {
            return false;
        }
        final RunList builds = computer.getBuilds();
        if (builds == null || builds.iterator().hasNext() == false) {
            // unused
            return false;
        }
        final Run build = (Run)builds.iterator().next();
        if (build.isBuilding()) {
            // a build is still in-progress
            return false;
        }

        // a build has completed, if any executor is idle, we can safely assume it's done and the slave should be
        // reaped
        for (final Executor executor : computer.getAllExecutors()) {

            if (executor.isIdle()) {
                LOGGER.log(Level.FINE, "Executor " + executor + " of slave " + nodePoolSlave
                        + " has been used before.");
                return true;
            }
        }

        // if we reach this point, no executor has returned to idle status (yet).
        LOGGER.log(Level.FINE, "Slave " + nodePoolSlave + " does not yet have an idle executor.");
        return false;
    }
}
