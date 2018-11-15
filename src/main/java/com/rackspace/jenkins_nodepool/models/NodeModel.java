package com.rackspace.jenkins_nodepool.models;

import com.rackspace.jenkins_nodepool.NodePoolState;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class NodeModel implements Serializable {
    /* Example data model in JSON:
    { "private_ipv4":"10.184.224.134",
      "allocated_to":"100-0001341546",
      "external_id":"e0686047-046e-40af-9309-130bd7027fe2",
      "type":["ubuntu-bionic-om-io2"],
      "host_keys":["ssh-ed25519 AAAblahblahblah"],
      "cloud":"pubcloud_uk",
      "hostname":"nodepool-ubuntu-bionic-om-io2-pubcloud-lon-0000000000",
      "state_time":1.5421423299199882E9,
      "provider":"pubcloud-lon",
      "state":"in-use",
      "connection_port":22.0,
      "public_ipv4":"XXX.XXX.XXX.XXX",
      "created_time":1.542141968379526E9,
      "ssh_port":22.0,
      "connection_type":"ssh",
      "public_ipv6":"2a00:1a48:78ff:b0:be76:4eff:fe08:60b4",
      "pool":"onmetal",
      "build_id":"PM_rpc-openstack-master-xenial_mnaio_no_artifacts-swift-elk#01",
      "region":"LON",
      "image_id":"ubuntu-bionic-onmetal",
      "launcher":"nodepool-server-iad-3-21467-PoolWorker.pubcloud-lon-onmetal",
      "interface_ip":"XXX.XXX.XXX.XXX"}
     */
    private List<String> host_keys;
    private Double state_time;
    private Float created_time;
    private String launcher;
    private String allocated_to;
    private String pool;
    private String interface_ip;
    private Integer ssh_port;
    private String hostname;
    private String public_ipv6;
    private String public_ipv4;
    private String private_ipv4;
    private Integer connection_port;
    private String connection_type;
    private String cloud;
    private String image_id;
    private Long hold_expiration;
    private String hold_job;
    private String username;
    private String az;
    private String provider;
    private String external_id;
    private List<String> type;
    private String comment;
    private NodePoolState state;
    private String region;
    private String build_id;

    public NodeModel() {
    }

    public NodeModel(List<String> host_keys, Double state_time, Float created_time, String launcher, String allocated_to, String pool, String interface_ip, Integer ssh_port, String hostname, String public_ipv6, String public_ipv4, String private_ipv4, Integer connection_port, String connection_type, String cloud, String image_id, Long hold_expiration, String hold_job, String username, String az, String provider, String external_id, List<String> type, String comment, NodePoolState state, String region, String build_id) {
        this.host_keys = host_keys;
        this.state_time = state_time;
        this.created_time = created_time;
        this.launcher = launcher;
        this.allocated_to = allocated_to;
        this.pool = pool;
        this.interface_ip = interface_ip;
        this.ssh_port = ssh_port;
        this.hostname = hostname;
        this.public_ipv6 = public_ipv6;
        this.public_ipv4 = public_ipv4;
        this.private_ipv4 = private_ipv4;
        this.connection_port = connection_port;
        this.connection_type = connection_type;
        this.cloud = cloud;
        this.image_id = image_id;
        this.hold_expiration = hold_expiration;
        this.hold_job = hold_job;
        this.username = username;
        this.az = az;
        this.provider = provider;
        this.external_id = external_id;
        this.type = type;
        this.comment = comment;
        this.state = state;
        this.region = region;
        this.build_id = build_id;
    }

    public List<String> getHost_keys() {
        return host_keys;
    }

    public void setHost_keys(List<String> host_keys) {
        this.host_keys = host_keys;
    }

    public Double getState_time() {
        return state_time;
    }

    public void setState_time(Double state_time) {
        this.state_time = state_time;
    }

    public Float getCreated_time() {
        return created_time;
    }

    public void setCreated_time(Float created_time) {
        this.created_time = created_time;
    }

    public String getLauncher() {
        return launcher;
    }

    public void setLauncher(String launcher) {
        this.launcher = launcher;
    }

    public String getAllocated_to() {
        return allocated_to;
    }

    public void setAllocated_to(String allocated_to) {
        this.allocated_to = allocated_to;
    }

    public String getPool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    public String getInterface_ip() {
        return interface_ip;
    }

    public void setInterface_ip(String interface_ip) {
        this.interface_ip = interface_ip;
    }

    public Integer getSsh_port() {
        return ssh_port;
    }

    public void setSsh_port(Integer ssh_port) {
        this.ssh_port = ssh_port;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPublic_ipv6() {
        return public_ipv6;
    }

    public void setPublic_ipv6(String public_ipv6) {
        this.public_ipv6 = public_ipv6;
    }

    public String getPublic_ipv4() {
        return public_ipv4;
    }

    public void setPublic_ipv4(String public_ipv4) {
        this.public_ipv4 = public_ipv4;
    }

    public String getPrivate_ipv4() {
        return private_ipv4;
    }

    public void setPrivate_ipv4(String private_ipv4) {
        this.private_ipv4 = private_ipv4;
    }

    public Integer getConnection_port() {
        if (connection_port == null) {
            return 22;
        } else {
            return connection_port;
        }
    }

    public void setConnection_port(Integer connection_port) {
        this.connection_port = connection_port;
    }

    public String getConnection_type() {
        return connection_type;
    }

    public void setConnection_type(String connection_type) {
        this.connection_type = connection_type;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getImage_id() {
        return image_id;
    }

    public void setImage_id(String image_id) {
        this.image_id = image_id;
    }

    public Long getHold_expiration() {
        return hold_expiration;
    }

    public void setHold_expiration(Long hold_expiration) {
        this.hold_expiration = hold_expiration;
    }

    public String getHold_job() {
        return hold_job;
    }

    public void setHold_job(String hold_job) {
        this.hold_job = hold_job;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAz() {
        return az;
    }

    public void setAz(String az) {
        this.az = az;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternal_id() {
        return external_id;
    }

    public void setExternal_id(String external_id) {
        this.external_id = external_id;
    }

    public List<String> getType() {
        return type;
    }

    public void setType(List<String> type) {
        this.type = type;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public NodePoolState getState() {
        return state;
    }

    public void setState(NodePoolState state) {
        this.state = state;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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
        NodeModel that = (NodeModel) o;
        return Objects.equals(host_keys, that.host_keys) &&
                Objects.equals(state_time, that.state_time) &&
                Objects.equals(created_time, that.created_time) &&
                Objects.equals(launcher, that.launcher) &&
                Objects.equals(allocated_to, that.allocated_to) &&
                Objects.equals(pool, that.pool) &&
                Objects.equals(interface_ip, that.interface_ip) &&
                Objects.equals(ssh_port, that.ssh_port) &&
                Objects.equals(hostname, that.hostname) &&
                Objects.equals(public_ipv6, that.public_ipv6) &&
                Objects.equals(public_ipv4, that.public_ipv4) &&
                Objects.equals(private_ipv4, that.private_ipv4) &&
                Objects.equals(connection_port, that.connection_port) &&
                Objects.equals(connection_type, that.connection_type) &&
                Objects.equals(cloud, that.cloud) &&
                Objects.equals(image_id, that.image_id) &&
                Objects.equals(hold_expiration, that.hold_expiration) &&
                Objects.equals(hold_job, that.hold_job) &&
                Objects.equals(username, that.username) &&
                Objects.equals(az, that.az) &&
                Objects.equals(provider, that.provider) &&
                Objects.equals(external_id, that.external_id) &&
                Objects.equals(type, that.type) &&
                Objects.equals(comment, that.comment) &&
                Objects.equals(state, that.state) &&
                Objects.equals(region, that.region) &&
                Objects.equals(build_id, that.build_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host_keys, state_time, created_time, launcher, allocated_to, pool, interface_ip, ssh_port, hostname, public_ipv6, public_ipv4, private_ipv4, connection_port, connection_type, cloud, image_id, hold_expiration, hold_job, username, az, provider, external_id, type, comment, state, region, build_id);
    }

    @Override
    public String toString() {
        return "NodeModel{" +
                "host_keys=" + host_keys +
                ", state_time=" + state_time +
                ", created_time=" + created_time +
                ", launcher='" + launcher + '\'' +
                ", allocated_to='" + allocated_to + '\'' +
                ", pool='" + pool + '\'' +
                ", interface_ip='" + interface_ip + '\'' +
                ", ssh_port=" + ssh_port +
                ", hostname='" + hostname + '\'' +
                ", public_ipv6='" + public_ipv6 + '\'' +
                ", public_ipv4='" + public_ipv4 + '\'' +
                ", private_ipv4='" + private_ipv4 + '\'' +
                ", connection_port=" + connection_port +
                ", connection_type='" + connection_type + '\'' +
                ", cloud='" + cloud + '\'' +
                ", image_id='" + image_id + '\'' +
                ", hold_expiration=" + hold_expiration +
                ", hold_job='" + hold_job + '\'' +
                ", username='" + username + '\'' +
                ", az='" + az + '\'' +
                ", provider='" + provider + '\'' +
                ", external_id='" + external_id + '\'' +
                ", type=" + type +
                ", comment='" + comment + '\'' +
                ", state='" + state + '\'' +
                ", region='" + region + '\'' +
                ", build_id='" + build_id + '\'' +
                '}';
    }
}
