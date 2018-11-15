package com.rackspace.jenkins_nodepool.models;

import com.rackspace.jenkins_nodepool.NodePoolState;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class NodeRequestModel implements Serializable {
    private List<String> node_types;
    private List<String> declined_by;
    private Double state_time;
    private Boolean reuse;
    private String requestor;
    private NodePoolState state;
    private List<String> nodes;
    // Not listed in running system data models
    private String jenkins_label;
    private String build_id;

    public NodeRequestModel() {
    }

    public NodeRequestModel(List<String> node_types, List<String> declined_by, Double state_time, Boolean reuse, String requestor, NodePoolState state, List<String> nodes, String jenkins_label, String build_id) {
        this.node_types = node_types;
        this.declined_by = declined_by;
        this.state_time = state_time;
        this.reuse = reuse;
        this.requestor = requestor;
        this.state = state;
        this.nodes = nodes;
        this.jenkins_label = jenkins_label;
        this.build_id = build_id;
    }

    public List<String> getNode_types() {
        return node_types;
    }

    public void setNode_types(List<String> node_types) {
        this.node_types = node_types;
    }

    public List<String> getDeclined_by() {
        return declined_by;
    }

    public void setDeclined_by(List<String> declined_by) {
        this.declined_by = declined_by;
    }

    public Double getState_time() {
        return state_time;
    }

    public void setState_time(Double state_time) {
        this.state_time = state_time;
    }

    public Boolean getReuse() {
        return reuse;
    }

    public void setReuse(Boolean reuse) {
        this.reuse = reuse;
    }

    public String getRequestor() {
        return requestor;
    }

    public void setRequestor(String requestor) {
        this.requestor = requestor;
    }

    public NodePoolState getState() {
        return state;
    }

    public void setState(NodePoolState state) {
        this.state = state;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public String getJenkins_label() {
        return jenkins_label;
    }

    public void setJenkins_label(String jenkins_label) {
        this.jenkins_label = jenkins_label;
    }

    public String getBuild_id() {
        return build_id;
    }

    public void setBuild_id(String build_id) {
        this.build_id = build_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeRequestModel that = (NodeRequestModel) o;
        return Objects.equals(node_types, that.node_types) &&
                Objects.equals(declined_by, that.declined_by) &&
                Objects.equals(state_time, that.state_time) &&
                Objects.equals(reuse, that.reuse) &&
                Objects.equals(requestor, that.requestor) &&
                state == that.state &&
                Objects.equals(nodes, that.nodes) &&
                Objects.equals(jenkins_label, that.jenkins_label) &&
                Objects.equals(build_id, that.build_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node_types, declined_by, state_time, reuse, requestor, state, nodes, jenkins_label, build_id);
    }

    @Override
    public String toString() {
        return "NodeRequestModel{" +
                "node_types=" + node_types +
                ", declined_by=" + declined_by +
                ", state_time=" + state_time +
                ", reuse=" + reuse +
                ", requestor='" + requestor + '\'' +
                ", state=" + state +
                ", nodes=" + nodes +
                ", jenkins_label='" + jenkins_label + '\'' +
                ", build_id='" + build_id + '\'' +
                '}';
    }
}
