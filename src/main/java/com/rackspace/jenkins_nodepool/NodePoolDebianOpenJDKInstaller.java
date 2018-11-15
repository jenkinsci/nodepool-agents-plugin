package com.rackspace.jenkins_nodepool;

import com.trilead.ssh2.Connection;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.Messages;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * An installer that performs the necessary steps to install the Open JDK 8 JRE on a remote Ubuntu NodePool slave node.
 */
public class NodePoolDebianOpenJDKInstaller extends NodePoolJDKInstaller {

    /**
     * Our class logger.
     */
    private static final Logger LOG = Logger.getLogger(NodePoolDebianOpenJDKInstaller.class.getName());

    private static final String OPEN_JDK_8_JRE_PKG = "openjdk-8-jre-headless";
    private static final String DEFAULT_OPENJDK_8_JAVA_HOME = "/usr/lib/jvm/java-8-openjdk-amd64";

    /**
     * Serial version UID - change this when modifying this class.
     */
    private static final long serialVersionUID = -1021293810434797215L;

    /**
     * The JDK home directory for the JDK installation accomplished by the script.
     */
    private final String jdkHome;

    /**
     * Creates a new Debian OpenJDK installer.
     */
    public NodePoolDebianOpenJDKInstaller() {
        super(OPEN_JDK_8_JRE_PKG);
        this.jdkHome = DEFAULT_OPENJDK_8_JAVA_HOME;
    }

    /**
     * Creates a new NodePool JDK installer.
     *
     * @param label the label associated with this installer
     */
    public NodePoolDebianOpenJDKInstaller(String label) {
        super(label);
        this.jdkHome = DEFAULT_OPENJDK_8_JAVA_HOME;
    }

    /**
     * Creates a new Debian OpenJDK installer.
     *
     * @param label   the label for the tool installer
     * @param jdkHome the JDK home installation folder
     */
    public NodePoolDebianOpenJDKInstaller(String label, String jdkHome) {
        super(label);
        this.jdkHome = jdkHome;
    }

    /**
     * Returns the Java home folder associated with this installation.
     *
     * @return the Java home folder associated with this installation
     */
    @Override
    public String getJavaHome() {
        return this.jdkHome;
    }

    /**
     * Ensure that the Java is really installed.
     * If it is already installed, do nothing.
     * Called only if {@link #appliesTo(Node)} are true.
     *
     * @param node the computer on which to install the tool
     * @param tl   any status messages produced by the installation go here
     * @return the (directory) path at which the tool can be found,
     * typically coming from {@link #preferredLocation}
     * @throws IOException          if installation fails
     * @throws InterruptedException if communication with a agent is interrupted
     */
    @Override
    public FilePath performInstallation(Node node, TaskListener tl, Connection connection) throws IOException, InterruptedException {

        if (connection == null) {
            throw new InterruptedException("Connection is null - please set the connection before performing the installation.");
        }

        // Install the Openjdk 8 JRE
        final String[] installCommands = new String[]{
                "apt-get", "update",
                "&&",
                "apt-get", "install", OPEN_JDK_8_JRE_PKG, "-y"
        };
        fine(tl, format("Installing %s using command: %s", OPEN_JDK_8_JRE_PKG, Arrays.toString(installCommands)));

        final RemoteLauncher launcher = new RemoteLauncher(tl, connection);
        final int exitCode = executeCommand(tl, launcher, installCommands);

        if (exitCode != 0) {
            final String msg = format(
                    "Failed to install %s using command: %s via performInstallation() for node: %s - exit code is: %d",
                    OPEN_JDK_8_JRE_PKG, Arrays.toString(installCommands), node, exitCode);
            warn(tl, msg);
            throw new AbortException(msg);
        } else {
            fine(tl, format("Installed %s", OPEN_JDK_8_JRE_PKG));
        }

        // Let's test to see if the java installation was successful
        if (isJavaInstalled(launcher, tl)) {
            fine(tl, format("Running java command was successful for node: %s", node));
        } else {
            log(Level.WARNING, tl, format("Running java command was NOT successful for node: %s", node));
        }

        return new FilePath(new File(getJavaHome()));
    }
}
