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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public class NodePoolGlobalConfiguration extends GlobalConfiguration {

    private static final Logger LOG = Logger.getLogger(NodePoolGlobalConfiguration.class.getName());

    private String credentialsId;
    private String connectionString;
    private String labelPrefix;
    private String requestRoot;
    private String nodeRoot;
    private String priority;
    private String requestor;
    private String zooKeeperRoot;
    public static final Charset CHARSET = Charset.forName("UTF-8");


    public NodePoolGlobalConfiguration() {
        load();
    }

    @DataBoundConstructor
    public NodePoolGlobalConfiguration(String connectionString,
            String credentialsId, String labelPrefix, String requestRoot,
            String priority, String requestor, String zooKeeperRoot,
            String nodeRoot) {
        this.connectionString = connectionString;
        this.credentialsId = credentialsId;
        this.requestRoot = requestRoot;
        this.priority = priority;
        this.requestor = requestor;
        this.labelPrefix = labelPrefix;
        this.zooKeeperRoot = zooKeeperRoot;
        this.nodeRoot = nodeRoot;
    }

    public static NodePoolGlobalConfiguration getInstance() {
        return GlobalConfiguration.all().get(NodePoolGlobalConfiguration.class);
    }

    public String getNodeRoot() {
        load();
        return nodeRoot;
    }

    public void setNodeRoot(String nodeRoot) {
        this.nodeRoot = nodeRoot;
        save();
    }

    public String getZooKeeperRoot() {
        load();
        return zooKeeperRoot;
    }

    public void setZooKeeperRoot(String zooKeeperRoot) {
        this.zooKeeperRoot = zooKeeperRoot;
        save();
    }

    public String getLabelPrefix() {
        load();
        return labelPrefix;
    }

    public void setLabelPrefix(String labelPrefix) {
        this.labelPrefix = labelPrefix;
        save();
    }

    public String getRequestRoot() {
        load();
        return requestRoot;
    }

    public void setRequestRoot(String requestRoot) {
        this.requestRoot = requestRoot;
        save();
    }

    public String getPriority() {
        load();
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
        save();
    }

    public String getRequestor() {
        load();
        return requestor;
    }

    public void setRequestor(String requestor) {
        this.requestor = requestor;
        save();
    }

    public String getCredentialsId() {
        load();
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        save();
    }

    public String getConnectionString() {
        load();
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
        save();
    }

    /**
     * Shamelessly stolen from
     * https://github.com/jenkinsci/ssh-slaves-plugin/blob/master/src/main/java/hudson/plugins/sshslaves/SSHConnector.java#L314
     *
     */
    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String credentialsId) {
        AccessControlled _context = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
        if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
            return new StandardUsernameListBoxModel()
                    .includeCurrentValue(credentialsId);
        }
        return new StandardUsernameListBoxModel()
                .includeMatchingAs(
                        ACL.SYSTEM,
                        context,
                        StandardUsernameCredentials.class,
                        Collections.<DomainRequirement>singletonList(SSHLauncher.SSH_SCHEME),
                        SSHAuthenticator.matcher(Connection.class)
                )
                .includeCurrentValue(credentialsId);
    }

    @Override
    public String getDisplayName() {
        return "NodePool Global Configuration";
    }

    public Boolean valid() {
        if ("".equals(connectionString) || credentialsId == null) {
            return false;
        }
        if ("".equals(credentialsId) || credentialsId == null) {
            return false;
        }
        return true;
    }

    public void doTestZooKeeperConnection(StaplerRequest req,
            StaplerResponse resp, @QueryParameter final String connectionString) throws IOException, ServletException {
        new FormFieldValidator(req, resp, true) {
            @Override
            protected void check() throws IOException, ServletException {
                try {
                    ZooKeeperClient kzc = new ZooKeeperClient(connectionString,
                            zooKeeperRoot, new RetryOneTime(100));
                    String node = kzc.getConnection()
                            .create()
                            .creatingParentsIfNeeded()
                            .withMode(CreateMode.EPHEMERAL)
                            .forPath(MessageFormat.format("/testing/{0}", req.getSession().getId()));
                    kzc.disconnect();
                    ok(MessageFormat.format("Successfully connected to Zookeeper at {0}", connectionString));
                } catch (Exception e) {
                    error(MessageFormat.format("Failed to connecto to ZooKeeper :( {0}", e.getMessage()));
                }
            }
        }.process();
    }

    public FormValidation doCheckConnectionString(@QueryParameter String connectionString) {
        LOG.log(Level.INFO, "DoCheckConnectionString: {0}", connectionString);
        if (connectionString.contains(":")) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("Connection string must be of the form host:port or host:port,hostn:portn");
        }
    }

    public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
        if ("".equals(credentialsId)) {
            return FormValidation.error("SSH credentials must be supplied. User+Password or User+Key are ok.");
        } else {
            return FormValidation.ok();

        }
    }

    public FormValidation doCheckLabelPrefix(@QueryParameter String labelPrefix) {
        LOG.log(Level.INFO, "doCheckLabelPrefix: {0}", labelPrefix);
        if ("".equals(labelPrefix)) {
            return FormValidation.error("label prefix must not be blank, that would cause this plugin to request a node for every label.");
        } else {
            return FormValidation.ok();
        }
    }

    //TODO: figure out how to reuse the form validation functions or dedupe this somehow.
    public Boolean isConfigured() {
        if (connectionString == null || !connectionString.contains(":")) {
            return false;
        }
        if (credentialsId == null || "".equals(credentialsId)) {
            return false;
        }
        if (labelPrefix == null || "".equals(labelPrefix)) {
            return false;
        }
        return true;
    }

    //TODO: kick off build queue scan on save.
    // maybe compare number of items in queue matching label prefix with number of active requests?
}
