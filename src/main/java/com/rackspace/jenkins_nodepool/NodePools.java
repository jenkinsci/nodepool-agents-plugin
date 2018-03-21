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
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Top level Jenkins configuration class to manage all NodePool configuration
 */

@Extension
public class NodePools extends GlobalConfiguration implements Iterable<NodePool> {

    private static final Logger LOG = Logger.getLogger(NodePools.class.getName());
    public static NodePools get() {
        return GlobalConfiguration.all().get(NodePools.class);
    }

    private List<NodePool> nodePools;

    public NodePools() {
        load();
        if (nodePools == null) {
            nodePools = new ArrayList();
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        super.configure(req, json);
        if (!json.containsKey("nodePools")) {
            // if the last nodepool configuration is removed
            // the nodePools field is not included in the json
            // so the field is not updated. This means the
            // last configuration will appear to be removed
            // in the ui, but will reappear on refresh. :(
            // To prevent that we clear the array if no
            // nodePools are supplied.
            nodePools.clear();
        }
        save();
        return true;
    }

    public List<NodePool> getNodePools() {
        return nodePools;
    }

    @Override
    public Iterator<NodePool> iterator() {
        return nodePools.iterator();
    }
    public List<NodePool> nodePoolsForLabel(Label label) {
        return stream()
                .filter((NodePool np) -> label.getName().startsWith(np.getLabelPrefix()))
                .collect(Collectors.toList());
    }

    public void provisionNode(Label label, Task task) {
        for (NodePool np : nodePoolsForLabel(label)) {
            try {
                np.provisionNode(label, task);
                break;
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        };
    }

    // scan queue for builds waiting for instance.
    // TODO: call this on startup
    public void scanQueue() {

        List<NodeRequest> activeRequests = nodePools.stream()
                .map(NodePool::getRequests)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Jenkins jenkins = Jenkins.getInstance();
        List<Queue.Item> queueItems = Arrays.asList(jenkins.getQueue().getItems());

        List<String> queueLabels = queueItems
                .stream()
                .map(i -> i.getAssignedLabel().getName())
                .collect(Collectors.toList());

        List<String> requestLabels = activeRequests
                .stream()
                .map(NodeRequest::getJenkinsLabel)
                .map(Label::toString)
                .collect(Collectors.toList());

        requestLabels.forEach((label) -> queueLabels.remove(label));

        // queueLabels now contains the labels for queue nodes that don't have a
        // corresponding request.
        // If multiple nodepools match the prefix, use the first one we
        // come across, if that throws an exception the next will be tried.
        Jenkins j = Jenkins.getInstance();
        Task t = new FreeStyleProject(j, "Queue Scan"); //TODO: does this actually create a project?
        queueLabels.forEach((label) -> provisionNode(j.getLabel(label), t));
    }

    @DataBoundSetter
    public void setNodePools(List<NodePool> nodePools) {
        this.nodePools = nodePools;
    }

    public Stream<NodePool> stream() {
        return nodePools.stream();
    }
}
