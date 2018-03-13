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

import com.google.gson.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;

enum State{
	requested, pending, fulfilled, failed
}

/**
 * Represents a nodepool node request. Data format is JSON dump of following dict structure:
 * 	---
 * 	node_types:
 * 		- label1
 * 		- label2
 *	requestor: string id (eg hostname)
 * 	state: string requested|pending|fulfilled|failed
 * 	state_time: float seconds since epoch
 *
 * @author hughsaunders
 */
public class NodeRequest extends HashMap {

    private static final Charset charset = Charset.forName("UTF-8");
    private static final Logger LOGGER = Logger.getLogger(NodeRequest.class.getName());
    private static final Gson gson = new Gson();

    private String nodePoolID;
    private String nodePath;

    public NodeRequest(CuratorFramework conn, String label, String jenkinsLabel) {
        this(conn, "jenkins", Arrays.asList(new String[]{label}), jenkinsLabel);
    }

    @SuppressFBWarnings
    public NodeRequest(CuratorFramework conn, String requestor, List<String> labels, String jenkinsLabel) {
        put("node_types", new ArrayList(labels));
        put("requestor", requestor);
        put("state", State.requested);
        put("state_time", new Double(System.currentTimeMillis() / 1000));
        put("jenkins_label", jenkinsLabel);
    }

    // public methods

    public String getNodePath() {
        return nodePath;
    }

    public void setNodePath(String nodePath) {
        this.nodePath = nodePath;
    }

    public String getNodePoolID() {
        return nodePoolID;
    }

    public void setNodePoolID(String nodePoolID) {
        this.nodePoolID = nodePoolID;
    }

    public State getState() {
        return (State)get("state");
    }

    @Override
    public String toString(){
        String jsonStr = gson.toJson(this);
        return jsonStr;
    }

    public byte[] getBytes(){
        return toString().getBytes(charset);
    }

    void updateFromMap(Map data) {

        // convert state time from string
        final Double stateTime = (Double)data.get("state_time");
        data.put("state_time", stateTime);

        // convert 'state' back into its corresponding enum value
        final String stateString = (String)data.get("state");
        data.put("state", State.valueOf(stateString));

        putAll(data);
    }

    public Map<String, String> getAllocatedNodes() {
        // Example fulfilled request
        // {"nodes": ["0000000000"], "node_types": ["debian"], "state": "fulfilled", "declined_by": [], "state_time": 1520849225.4513698, "reuse": false, "requestor": "NodePool:min-ready"}
        if (get("state") != State.fulfilled){
            throw new IllegalStateException("Attempt to get allocated nodes from a node request before it has been fulfilled");
        }
        List<String> nodes = (List) get("nodes");
        List<String> node_types = (List) get("node_types");
        Map<String, String> nodesMap = new HashMap();
        for (int i = 0; i < nodes.size(); i++) {
            nodesMap.put(nodes.get(i), node_types.get(i));
        }
        return nodesMap;
    }

    String getNodePoolLabel() {
        final List<String> labels = (List<String>)get("node_types");
        return labels.get(0);
    }

    String getJenkinsLabel() {
        return (String)get("jenkins_label");
    }
}
