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

import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.HttpResponse;

/**
 *
 * @author hughsaunders
 */
public class NodePoolComputer extends SlaveComputer {

    private static final Logger LOG = Logger.getLogger(NodePoolComputer.class.getName());

    private NodePoolNode nodePoolNode;

    public NodePoolComputer(Slave slave) {
        super(slave);

        // required by superclass but shouldn't be used.
        throw new IllegalStateException("Attempting to initialise NodePoolComputer without supplying a NodePoolNode.");
    }

    public NodePoolComputer(NodePoolSlave nps, NodePoolNode npn) {
        super(nps);
        setNodePoolNode(npn);
    }

    public final void setNodePoolNode(NodePoolNode npn) {
        this.nodePoolNode = npn;
    }

    public NodePoolNode getNodePoolNode() {
        return nodePoolNode;
    }

    @Override
    public HttpResponse doDoDelete() throws IOException {
        try {
            nodePoolNode.release();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return super.doDoDelete();
    }

    @Override
    public String toString() {
        if (nodePoolNode == null) {
            return super.toString();
        } else {
            return nodePoolNode.getName();
        }
    }

    @Override
    public String getDisplayName() {
        return toString();
    }
}
