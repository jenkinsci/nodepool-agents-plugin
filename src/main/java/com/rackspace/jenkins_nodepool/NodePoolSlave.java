/*
 * The MIT License
 *
 * Copyright 2018 hughsaunders.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.rackspace.jenkins_nodepool;

import hudson.Extension;
import hudson.model.*;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.ManuallyProvidedKeyVerificationStrategy;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.RunList;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Representation of a Jenkins slave sourced from NodePool.
 *
 * @author hughsaunders
 */
public class NodePoolSlave extends Slave {

    /**
     * Class logger
     */
    private static final Logger LOG = Logger.getLogger(NodePoolSlave.class.getName());
    private static final String FORCE_HOLD_PROPERTY = "nodepool.slave.forcehold";

    /**
     * The node from the associated NodePool cluster.
     */
    private transient final NodePoolNode nodePoolNode;

    /**
     * Whether to continue to hold this node after the job is completed.
     */
    private boolean held = false;

    /**
     * Increment this when modifying this class.
     */
    static final long serialVersionUID = 1L;

    /**
     * Create a new slave
     *
     * @param nodePoolNode  the node from NodePool
     * @param credentialsId the Jenkins credential identifier
     * @throws Descriptor.FormException on configuration exception
     * @throws IOException              on configuration exception
     */
    @DataBoundConstructor  // not used, but it makes stapler happy if you click "Save" while editing a Node.
    public NodePoolSlave(NodePoolNode nodePoolNode, String credentialsId) throws Descriptor.FormException, IOException {

        super(
                nodePoolNode.getName(), // name
                "Nodepool Node", // description
                "/var/lib/jenkins", // TODO this should be the path to the root of the workspace on the slave
                "1", // num executors
                Mode.EXCLUSIVE,
                nodePoolNode.getJenkinsLabel(),
                new SSHLauncher(
                        nodePoolNode.getHost(),
                        nodePoolNode.getPort(),
                        credentialsId,
                        "", //jvmoptions
                        null, // javapath
                        null, //jdkInstaller
                        "", //prefixStartSlaveCmd
                        "", //suffixStartSlaveCmd
                        300, //launchTimeoutSeconds
                        30, //maxNumRetries
                        10, //retryWaitTime
                        new ManuallyProvidedKeyVerificationStrategy(nodePoolNode.getHostKey())
                        //new NonVerifyingKeyVerificationStrategy()
                        //TODO: go back to verifying host key strategy
                ),
                new RetentionStrategy.Always(), //retentionStrategy
                new ArrayList() //nodeProperties
        );
        this.nodePoolNode = nodePoolNode;
    }

    public NodePoolNode getNodePoolNode() {
        return nodePoolNode;
    }

    // Called for deserialisation
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject(); // call default deserializer
        LOG.log(Level.WARNING, "Removing NodePool Slave {0} on startup as its a nodepool node that will have been destroyed", this.toString());
        Jenkins.getInstance().removeNode(this);
    }

    @Override
    public Computer createComputer() {
        NodePoolComputer npc = new NodePoolComputer(this, nodePoolNode);
        return npc;
    }

    /**
     * This gets invoked when the user clicks "Save" while editing the "Node" in the UI.
     * @param req  stapler request
     * @param form form data to update slave with
     * @return new slave object
     * @throws Descriptor.FormException  if things go sideways
     */
    @Override
    public Node reconfigure(final StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        // update `this` with submitted data from the form and return it.  the superclass version in Node creates a new
        // slave object, which we don't need.
        if (form==null) {
            return null;
        }

        final boolean held = form.getBoolean("held");
        setHeld(held);

        return this;
    }

    /**
     * If true, hold onto the node after the associated build is completed.  Do not release it to NodePool to get
     * cleaned up.
     *
     * @return true if node should be held.
     */
    public boolean isHeld() {
        // check for optional "force hold" property, primarily useful for debugging.
        final String forceHoldStr = System.getProperty(FORCE_HOLD_PROPERTY, "false");
        final boolean forceHold = Boolean.valueOf(forceHoldStr);

        return held || forceHold;
    }

    /**
     * Set to true in order to hold the node after the build is completed.
     *
     * @param held whether to hold the node after the build completes
     */
    public void setHeld(boolean held) {
        this.held = held;
    }

    @Override
    public SlaveDescriptor getDescriptor() {
        final NodePoolSlaveDescriptor descriptor = new NodePoolSlaveDescriptor();
        descriptor.setNodePoolSlave(this);
        return descriptor;
    }

    /**
     * Test if the slave's build is done.
     *
     * @return true if the build associated with this slave has completed.
     */
    boolean isBuildComplete() {

        final NodePoolComputer computer = (NodePoolComputer) toComputer();
        if (computer == null) {
            return false;
        }
        final RunList builds = computer.getBuilds();
        if (builds == null || !builds.iterator().hasNext()) {
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
                LOG.log(Level.FINE, "Executor " + executor + " of slave " + this
                        + " has been used before.");
                return true;
            }
        }

        // if we reach this point, no executor has returned to idle status (yet).
        LOG.log(Level.FINE, "Slave " + this + " does not yet have an idle executor.");
        return false;
    }


    /**
     * It makes jelly rendering on the node configuration page happy to have this defined.
     *
     * One thing this is used for is locating the help file associated with the form field(s).
     */
    @Extension
    public static final class NodePoolSlaveDescriptor extends SlaveDescriptor {

        private NodePoolSlave nodePoolSlave;

        public void setNodePoolSlave(NodePoolSlave slave) {
            this.nodePoolSlave = slave;
        }

        @Override
        public String getDisplayName() {
            return "NodePool Agent";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

        /**
         * If the build associated with the node/slave has completed, we don't
         * allow toggling of the hold state.
         *
         * @return true if the hold checkbox on the config screen should be read-only.
         */
        public boolean getHoldReadOnly() {
            return nodePoolSlave.isBuildComplete();
        }
    }


}