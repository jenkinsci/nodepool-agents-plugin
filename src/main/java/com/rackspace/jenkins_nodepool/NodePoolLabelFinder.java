/*
 * The MIT License
 *
 * Copyright 2018 Rackspace.
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
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


/**
 * Add provider label to Node Pool Slaves
 * @author Rackspace
 */
@Extension
public class NodePoolLabelFinder extends LabelFinder {

    @Override
    public Collection<LabelAtom> findLabels(Node node) {
        Set<LabelAtom> labels = new HashSet<>();
        return labels;

        // RE-2230. This has been disabled, because the ZK exists call can block
        // The main jenkins Queue lock is acquired before calling this function
        // when a node is deleted, so if the ZK call blocks, the Queue lock
        // is held, and new builds can't proceed out of the queue.

        /*
        if(node instanceof NodePoolSlave){
            NodePoolSlave nps = (NodePoolSlave) node;
            NodePoolNode npn = nps.getNodePoolNode();
            if(npn != null){
                String provider = npn.getProvider();
                if (provider != null){
                    labels.add(new LabelAtom(provider));
                }
            }
        }

        return labels;
        */
    }

}
