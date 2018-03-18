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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class NodePools implements Describable<NodePools>, Iterable<NodePool> {

    private static final Logger LOG = Logger.getLogger(NodePools.class.getName());
    public static NodePools instance;

    private final List<NodePool> nodePools;

    // scan queue for builds waiting for instance.
    // TODO: call this on startup
    public void scanQueue() {

        List<NodeRequest> activeRequests = NodeRequest.getActiveRequests();
        Jenkins jenkins = Jenkins.getInstance();
        List<Queue.Item> queueItems = Arrays.asList(jenkins.getQueue().getItems());

        List<String> queueLabels = queueItems
                .stream()
                .map(i -> i.getAssignedLabel().getName())
                .collect(Collectors.toList());

        List<String> requestLabels = activeRequests
                .stream()
                .map(NodeRequest::getJenkinsLabel)
                .collect(Collectors.toList());

        requestLabels.forEach((label) -> queueLabels.remove(label));

        // queueLabels now contains the labels for queue nodes that don't have a
        // corresponding request.
        // If multiple nodepools match the prefix, use the first one we
        // come across, if that throws an exception the next will be tried.
        queueLabels.forEach((label) -> provisionNode(label));
    }

    public void provisionNode(String label) {
        for (NodePool np : nodePoolsForLabel(label)) {
            try {
                np.provisionNode(new LabelAtom(label));
                break;
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        };
    }

    public List<NodePool> nodePoolsForLabel(String label) {
        return stream()
                .filter((NodePool np) -> label.startsWith(np.getLabelPrefix()))
                .collect(Collectors.toList());
    }

    @DataBoundConstructor
    public NodePools(List<NodePool> nodePools) {
        this.nodePools = nodePools;
        NodePools.instance = this;
    }

    private void initNPList() {
        if (nodePools == null) {
            //nodePools = new ArrayList<>();
        }
    }

    @Override
    public Descriptor<NodePools> getDescriptor() {
        return new NodePoolsDescriptor();
    }

    @Extension
    public static class NodePoolsDescriptor extends Descriptor<NodePools> {

        @Override
        public String getDisplayName() {
            return "NodePools";
        }

        public List<NodePool> getNodePools() {
            return NodePools.instance.getNodePools();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            super.configure(req, json);
            save();
            return true;
        }

    }


    public List<NodePool> getNodePools() {
        initNPList();
        return nodePools;
    }

    //public void setNodePools(List<NodePool> nodePools) {
        //this.nodePools = nodePools;
        //save();
    //}

    public void add(NodePool np) {
        initNPList();
        nodePools.add(np);
    }

    @Override
    public Iterator<NodePool> iterator() {
        initNPList();
        return nodePools.iterator();
    }

    public Stream<NodePool> stream() {
        initNPList();
        return nodePools.stream();
    }

}
