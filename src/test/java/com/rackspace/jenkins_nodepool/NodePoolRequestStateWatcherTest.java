package com.rackspace.jenkins_nodepool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.junit.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

/**
 * A test class for the Node Pool Request State Watcher class.
 *
 * @author Rackspace
 */
public class NodePoolRequestStateWatcherTest {
    /**
     * Our class logger.
     **/
    private static final Logger log = Logger.getLogger(NodePoolRequestStateWatcherTest.class.getName());

    private final static int zookeeperPort = 2181;
    private static TestingServer zkTestServer;
    private static CuratorFramework zkCli;
    private final static Gson gson = new Gson();

    public NodePoolRequestStateWatcherTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        log.info("Creating and starting test server on port: " + zookeeperPort);

        // Define a ZK specification
        final InstanceSpec zkSpec = new InstanceSpec(null, zookeeperPort, -1, -1, true, 1);

        // Create and start the server
        zkTestServer = new TestingServer(zkSpec, true);

        // Create a client
        log.info("Creating ZooKeeper client on port: " + zookeeperPort);
        zkCli = CuratorFrameworkFactory.newClient(zkTestServer.getConnectString(),
                new RetryOneTime(2000));
        zkCli.start();

        if (zkCli.getZookeeperClient().blockUntilConnectedOrTimedOut()) {
            log.info("Created ZooKeeper client on port: " + zookeeperPort);
        } else {
            throw new Exception("Client timed out connecting to ZooKeeper on port: " + zookeeperPort);
        }
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        log.info("Stopping client and test server on port: " + zookeeperPort);
        zkCli.close();
        zkTestServer.stop();
    }

    @Before
    public void before() {
    }

    @After
    public void after() {
    }

    /**
     * Test node pool request state with a timeout condition (success path).
     */
    @Test
    public void testNodePoolRequestStateWatcherSuccess() {

        final String zpath = "/test/success";

        try {
            // First we add some data to this ZK instance
            addData(zpath);

            // Create a timer task to run after a short delay
            final TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        // Update the state - indicating we did something and fulfilled the request at some point in the future.
                        log.info("Timer task to update path " + zpath + " with RequestState.fulfilled is running...");
                        updateState(zpath, RequestState.fulfilled);
                    } catch (Exception e) {
                        log.warning(e.getClass().getSimpleName() + " occurred while waiting in timer task thread. Message: " + e.getLocalizedMessage());
                    }
                }
            };

            // Create the timer and schedule it to run after a short delay
            final Timer timer = new Timer();
            timer.schedule(timerTask, 5000L);
            log.info("Scheduled timer task to update RequestState is running...");

            final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                    zkCli.getZookeeperClient().getZooKeeper(), zpath, RequestState.fulfilled);
            final int timeoutInSeconds = 30;
            log.info("Waiting for " + timeoutInSeconds + " seconds max for the watcher...");
            final boolean success = watcher.waitUntilDone(timeoutInSeconds, TimeUnit.SECONDS);

            assertTrue("NodePoolRequestStateWatcher discovered request state was fulfilled before timeout of " +
                    timeoutInSeconds + " seconds", success);
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + " occurred while creating a NodePool. Message: " +
                    e.getLocalizedMessage());
        }
    }

    /**
     * Test node pool request state with a timeout condition (failure path).
     */
    @Test
    public void testNodePoolRequestStateWatcherFail() {

        final String zpath = "/test/fail";

        try {
            // First we add some data to this ZK instance
            addData(zpath);

            // Create a timer task to run after a short delay
            final TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        // Update the state - indicating we did something and fulfilled the request at some point in the future.
                        log.info("Timer task to update path " + zpath + " with RequestState.fulfilled is running...");
                        updateState(zpath, RequestState.fulfilled);
                    } catch (Exception e) {
                        log.warning(e.getClass().getSimpleName() + " occurred while waiting in timer task thread. Message: " + e.getLocalizedMessage());
                    }
                }
            };

            // Create the timer and schedule it to run after a longer delay which will simulate a long running process
            final Timer timer = new Timer();
            timer.schedule(timerTask, 30000L);
            log.info("Scheduled timer task to update RequestState is running...");

            // Set a watch - this time with a short timeout so that we will...timeout early
            final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                    zkCli.getZookeeperClient().getZooKeeper(), zpath, RequestState.fulfilled);
            final int timeoutInSeconds = 10;
            log.info("Waiting for " + timeoutInSeconds + " seconds max for the watcher...");
            final boolean success = watcher.waitUntilDone(timeoutInSeconds, TimeUnit.SECONDS);

            assertFalse("NodePoolRequestStateWatcher discovered request state was fulfilled before timeout of " +
                    timeoutInSeconds + " seconds", success);
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + " occurred while creating a NodePool. Message: " +
                    e.getLocalizedMessage());
        }
    }

    /**
     * Simple routine to create a map full of data, convert to JSON and then convert to an array of bytes then store
     * into Zookeeper using the specified path.
     *
     * @param zpath the path for the data
     * @throws Exception if any errors occur while converting the data or communicating with Zookeeper
     */
    private void addData(String zpath) throws Exception {
        // Create a new map - keys are strings, values are anything
        final Map<String, Object> data = new HashMap<>();
        // Add some values
        data.put("node_types", Arrays.asList("a", "b"));
        data.put("requestor", "Some NodePool Requestor");
        data.put("state", RequestState.requested);
        data.put("state_time", (double) (System.currentTimeMillis() / 1000));
        data.put("jenkins_label", "Some Jenkins Label");

        // Save into ZK
        saveModel(zpath, data);
    }

    /**
     * Simple routine to create a map full of data, convert to JSON and then convert to an array of bytes then store
     * into Zookeeper using the specified path.
     *
     * @param zpath the path for the data
     * @param state the request state value
     * @throws Exception if any errors occur while converting the data or communicating with Zookeeper
     */
    private void updateState(String zpath, RequestState state) throws Exception {
        // Read the raw values
        log.info("Reading data from Zookeeper at path: " + zpath);
        byte[] bytes = zkCli.getData().forPath(zpath);

        // Convert to a string
        String jsonString = new String(bytes, Charset.forName("UTF-8"));

        // Load the data into a into a map
        final Map<String, Object> data = gson.fromJson(jsonString, new TypeToken<Map<String, Object>>() {
        }.getType());
        log.info("Data from Zookeeper at path: " + zpath + ", state: " + data.get("state"));

        // Update the state value and state time
        data.put("state", state);
        data.put("state_time", (double) (System.currentTimeMillis() / 1000));

        // Save back into ZK
        log.info("Saving data into Zookeeper at path: " + zpath + ", state: " + state);
        // Save into ZK
        saveModel(zpath, data);
    }

    /**
     * Common routine to save the data model to ZK.
     * @param zpath the path within ZK
     * @param data the data model to store at the specified path
     * @throws Exception if an error occurs while saving data to Zookeeper
     */
    private void saveModel(String zpath, Map<String, Object> data) throws Exception {
        // Convert the data back to a JSON string
        final String zkDataAsString = gson.toJson(data, new TypeToken<Map<String, Object>>() {
        }.getType());

        if (zkCli.checkExists().forPath(zpath) == null) {
            log.info("Saving data into Zookeeper at new path: " + zpath);
            zkCli.create().creatingParentContainersIfNeeded().forPath(
                    zpath, zkDataAsString.getBytes(Charset.forName("UTF-8")));
        } else {
            log.info("Saving data into Zookeeper at existing path: " + zpath);
            zkCli.setData().forPath(zpath, zkDataAsString.getBytes(Charset.forName("UTF-8")));
        }
    }
}
