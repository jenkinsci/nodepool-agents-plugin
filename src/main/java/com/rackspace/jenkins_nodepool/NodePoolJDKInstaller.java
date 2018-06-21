package com.rackspace.jenkins_nodepool;

import com.trilead.ssh2.Connection;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An abstract base class for NodePool JDK installers.  This class includes a few convenience methods and abstract
 * method declarations for implementing classes to perform installations.
 * <p>
 * Generally, the `FilePath performInstallation(Node node, TaskListener tl, Connection connection)` method provides
 * the installation implementation logic which leverages the connection to invoke required java installation commands
 * on the remote NodePool slave instance.
 * <p>
 * Earlier implementations of derived classes include an Ubuntu OpenJDK installer and a more generic script installer
 * which accepts an arbitrary script to run the installation (e.g. apt-get install openjdk-8-jre-headless -y).
 */
public abstract class NodePoolJDKInstaller extends ToolInstaller {

    /**
     * The default working directory for installing
     */
    private static final String DEFAULT_INSTALL_WORKING_DIR = "/tmp";

    /**
     * Our class logger.
     */
    private static final Logger LOG = Logger.getLogger(NodePoolJDKInstaller.class.getName());

    private String installWorkingDir = DEFAULT_INSTALL_WORKING_DIR;

    /**
     * Creates a new NodePool JDK installer.
     *
     * @param label the label associated with this installer
     */
    public NodePoolJDKInstaller(String label) {
        super(label);
    }

    /**
     * Returns the installation working directory for the installer (typically: /tmp).
     *
     * @return the installation working directory
     */
    public String getInstallWorkingDir() {
        return installWorkingDir;
    }

    /**
     * Sets the installation working directory for the installer.  The Default is typically: /tmp.
     *
     * @param installWorkingDir the installation working directory.
     * @throws IllegalArgumentException if installWorkingDir is null or empty
     */
    public void setInstallWorkingDir(String installWorkingDir) throws IllegalArgumentException {
        if (installWorkingDir == null || installWorkingDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Installation working directory cannot be null or empty.");
        }

        this.installWorkingDir = installWorkingDir;
    }

    /**
     * Returns the Java home folder associated with this installation (value set by the implementation class).
     *
     * @return the Java home folder associated with this installation
     */
    public abstract String getJavaHome();

    /**
     * Ensure that the configured tool is really installed. If it is already installed, do nothing.
     *
     * @param tool the tool being installed
     * @param node the computer on which to install the tool
     * @param tl   any status messages produced by the installation go here
     * @return the (directory) path at which the tool can be found
     * @throws IOException          if the installation fails
     * @throws InterruptedException if communication with a agent is interrupted
     */
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener tl) throws IOException, InterruptedException {
        return performInstallation(node, tl, null);
    }

    /**
     * Ensure that the configured tool is really installed. If it is already installed, do nothing.
     *
     * @param node       the computer on which to install the tool
     * @param tl         any status messages produced by the installation go here
     * @param connection the connection object
     * @return the (directory) path at which the tool can be found
     * @throws IOException          if the installation fails
     * @throws InterruptedException if communication with a agent is interrupted
     */
    public abstract FilePath performInstallation(Node node, TaskListener tl, Connection connection) throws IOException, InterruptedException;

    /**
     * Routine to run a remote command to determine if java is installed (e.g. java -version).
     *
     * @param launcher the launcher
     * @param tl       the task listener
     * @return true if the command was successful, false otherwise
     * @throws IOException          if an error occurs while launching and running the java version command
     * @throws InterruptedException if communication with a agent is interrupted
     */
    public boolean isJavaInstalled(Launcher launcher, TaskListener tl) throws IOException, InterruptedException {
        fine(tl, "Testing to see if java is installed...");
        final int exitCode = launcher.launch()
                .cmds("java", "-version")
                .stdout(tl)
                .pwd(getInstallWorkingDir())
                .join();

        if (exitCode != 0) {
            fine(tl, String.format("Failed to execute: java -version, exit code is: %d. Java does't appear to be installed.", exitCode));
        }

        return exitCode == 0;
    }

    /**
     * Returns the formatted current time stamp.
     *
     * @return the formatted current time stamp.
     */
    protected static String getTimestamp() {
        return String.format("[%1$tY-%1$tm-%1$tdT%1$tH:%1$tM:%1$tS%tz]", new Date());
        //return String.format("[%1$tD %1$tT]", new Date());
    }

    /**
     * Logs the specified message to both the logger and the task listener.
     *
     * @param lvl      the log level
     * @param listener the task listener
     * @param msg      the message to log
     */
    protected void log(Level lvl, TaskListener listener, String msg) {
        listener.getLogger().println(String.format("%s [%s] %s", getTimestamp(), lvl, msg));
        LOG.log(lvl, msg);
    }

    /**
     * Logs the specified message as Level.FINE to both the logger and the task listener.
     *
     * @param listener the task listener
     * @param msg      the message to log
     */
    protected void fine(TaskListener listener, String msg) {
        log(Level.FINE, listener, msg);
    }

    /**
     * Logs the specified message as Level.FINE to both the logger and the task listener.
     *
     * @param listener the task listener
     * @param msg      the message to log
     */
    protected void info(TaskListener listener, String msg) {
        log(Level.INFO, listener, msg);
    }

    /**
     * Logs the specified message as Level.FINE to both the logger and the task listener.
     *
     * @param listener the task listener
     * @param msg      the message to log
     */
    protected void warn(TaskListener listener, String msg) {
        log(Level.WARNING, listener, msg);
    }

    /**
     * A simple wrapper method for launching a set of commands.  This method leverages the Java tmpdir property for
     * the working directory of the command (handy for running simple commands).
     *
     * @param log      the task listener
     * @param launcher the launcher
     * @param commands one or more commands (as a vararg)
     * @return the exit code from the command(s)
     * @throws IOException          if an error occurs while launching and running the command
     * @throws InterruptedException if an error occurs while launching and running the command
     */
    protected int executeCommand(TaskListener log, Launcher launcher, String... commands) throws IOException, InterruptedException {
        return launcher.launch()
                .cmds(commands)
                .stdout(log)
                .pwd(getInstallWorkingDir())
                .join();
    }

    /**
     * A simple wrapper method for launching a set of commands.  This method leverages the Java tmpdir property for
     * the working directory of the command (handy for running simple commands).
     *
     * @param log        the task listener
     * @param launcher   the launcher
     * @param workingDir the working directory when running the command
     * @param commands   one or more commands (as a vararg)
     * @return the exit code from the command(s)
     * @throws IOException          if an error occurs while launching and running the command
     * @throws InterruptedException if an error occurs while launching and running the command
     */
    protected int executeCommand(TaskListener log, Launcher launcher, String workingDir, String... commands) throws IOException, InterruptedException {
        return launcher.launch()
                .cmds(commands)
                .stdout(log)
                .pwd(workingDir)
                .join();
    }
}
