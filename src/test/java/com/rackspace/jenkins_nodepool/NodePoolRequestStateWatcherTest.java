package com.rackspace.jenkins_nodepool;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.junit.*;
import static org.junit.Assert.*;

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
    private static final int DEFAULT_TEST_TIMEOUT_SEC = 30;

    public NodePoolRequestStateWatcherTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        log.fine("Creating and starting test server on port: " + zookeeperPort);

        // Define a ZK specification
        final InstanceSpec zkSpec = new InstanceSpec(null, zookeeperPort, -1, -1, true, 1);

        // Create and start the server
        zkTestServer = new TestingServer(zkSpec, true);

        // Create a client
        log.fine("Creating ZooKeeper client on port: " + zookeeperPort);
        zkCli = CuratorFrameworkFactory.newClient(zkTestServer.getConnectString(),
                new RetryOneTime(2000));
        zkCli.start();

        if (zkCli.getZookeeperClient().blockUntilConnectedOrTimedOut()) {
            log.fine("Created ZooKeeper client on port: " + zookeeperPort);
        } else {
            throw new Exception("Client timed out connecting to ZooKeeper on port: " + zookeeperPort);
        }
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        log.fine("Stopping client and test server on port: " + zookeeperPort);
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

        final String zpath = "/test/Success";

        try {
            // First we add some data to this ZK instance
            addData(zpath);

            // Create the timer and schedule it to run after a short delay
            final Timer timer = new Timer();
            timer.schedule(getTimerTask(zpath, RequestState.fulfilled), 5000L);

            final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                    zkCli, zpath, RequestState.fulfilled);

            log.fine("Waiting for " + DEFAULT_TEST_TIMEOUT_SEC + " seconds max for the watcher...");
            watcher.waitUntilDone(DEFAULT_TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

            assertSame("NodePoolRequestStateWatcher discovered request state was " +
                            watcher.getStateFromPath() + " before timeout of " + DEFAULT_TEST_TIMEOUT_SEC + " seconds",
                    watcher.getStateFromPath(), RequestState.fulfilled);
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + " occurred while creating a NodePool. Message: " +
                    e.getLocalizedMessage());
        }
    }

    /**
     * Test node pool request state with a timeout condition (not fulfilled path).
     */
    @Test
    public void testNodePoolRequestStateWatcherNotFulfilled() {

        final String zpath = "/test/NotFulfilled";

        try {
            // First we add some data to this ZK instance
            addData(zpath);

            // Create the timer and schedule it to run after a short delay
            final Timer timer = new Timer();
            timer.schedule(getTimerTask(zpath, RequestState.pending), 5000L);

            final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                    zkCli, zpath, RequestState.fulfilled);

            log.fine("Waiting for " + DEFAULT_TEST_TIMEOUT_SEC + " seconds max for the watcher...");
            try {
                watcher.waitUntilDone(DEFAULT_TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
                fail("Expected exception, but not thrown.");
            } catch (InterruptedException ex) {
                // pass
            }

            // Should timeout with non-success since we only changed value to pending (not fulfulled)
            assertNotSame("NodePoolRequestStateWatcher discovered request state was NOT fulfilled before timeout of " +
                            DEFAULT_TEST_TIMEOUT_SEC + " seconds",
                    watcher.getStateFromPath(), RequestState.fulfilled);
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

        final String zpath = "/test/WatcherFail";

        try {
            // First we add some data to this ZK instance
            addData(zpath);

            // Create the timer and schedule it to run after a longer delay which will simulate a long running process
            final Timer timer = new Timer();
            timer.schedule(getTimerTask(zpath, RequestState.fulfilled), 10000L);

            // Set a watch - this time with a short timeout so that we will...timeout early
            final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                    zkCli, zpath, RequestState.fulfilled);

            final int timeoutInSeconds = 5;
            log.fine("Waiting for " + timeoutInSeconds + " seconds max for the watcher...");
            try {
                watcher.waitUntilDone(timeoutInSeconds, TimeUnit.SECONDS);
                fail("Expected exception, but not thrown.");
            } catch (InterruptedException ex) {
                // pass
            }

            assertNotSame("NodePoolRequestStateWatcher discovered request state was NOT fulfilled before timeout of " +
                            DEFAULT_TEST_TIMEOUT_SEC + " seconds",
                    watcher.getStateFromPath(), RequestState.fulfilled);
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + " occurred while creating a NodePool. Message: " +
                    e.getLocalizedMessage());
        }
    }

    /**
     * Test node pool request state with a timeout condition (success path).
     */
    @Test
    public void testNodePoolRequestStateWatcherMultipleRequestStateSuccess() {

        final String zpath = "/test/Success";

        try {
            // First we add some data to this ZK instance
            addData(zpath);

            // Create the timer and schedule it to run after a short delay
            final Timer timer1 = new Timer();
            timer1.schedule(getTimerTask(zpath, RequestState.requested), 2000L);

            // Create the timer and schedule it to run after a short delay
            final Timer timer2 = new Timer();
            timer2.schedule(getTimerTask(zpath, RequestState.pending), 3000L);

            // Create the timer and schedule it to run after a short delay
            final Timer timer3 = new Timer();
            timer3.schedule(getTimerTask(zpath, RequestState.fulfilled), 5000L);

            final NodePoolRequestStateWatcher watcher = new NodePoolRequestStateWatcher(
                    zkCli, zpath, RequestState.fulfilled);

            log.fine("Waiting for " + DEFAULT_TEST_TIMEOUT_SEC + " seconds max for the watcher...");
            watcher.waitUntilDone(DEFAULT_TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

            assertSame("NodePoolRequestStateWatcher discovered request state was " +
                            watcher.getStateFromPath() + " before timeout of " + DEFAULT_TEST_TIMEOUT_SEC + " seconds",
                    watcher.getStateFromPath(), RequestState.fulfilled);
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + " occurred while creating a NodePool. Message: " +
                    e.getLocalizedMessage());
        }
    }

    /**
     * Convenience routine to create and return a timer task for updating the specified zpath with the desired state.
     *
     * @param zpath        the path
     * @param desiredState the desired state
     * @return a new timer task for updating the zpath with the desired state.
     */
    private TimerTask getTimerTask(final String zpath, final RequestState desiredState) {
        // Create a timer task to run based on the path and desired state.
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    // Update the state - indicating we did something and fulfilled the request at some point in the future.
                    log.fine("Timer task to update path:" + zpath + " with state:" + desiredState + " is running...");
                    updateState(zpath, desiredState);
                } catch (Exception e) {
                    log.warning(e.getClass().getSimpleName() +
                            " occurred while waiting in timer task thread. Message: " + e.getLocalizedMessage());
                }
            }
        };
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
        log.fine("Reading data from Zookeeper at path: " + zpath);
        byte[] bytes = zkCli.getData().forPath(zpath);

        // Convert to a string
        String jsonString = new String(bytes, Charset.forName("UTF-8"));

        // Load the data into a into a map
        final Map<String, Object> data = gson.fromJson(jsonString, new TypeToken<Map<String, Object>>() {
        }.getType());
        log.fine("Data from Zookeeper at path: " + zpath + ", state: " + data.get("state"));

        // Update the state value and state time
        data.put("state", state);
        data.put("state_time", (double) (System.currentTimeMillis() / 1000));

        // Save back into ZK
        log.fine("Saving data into Zookeeper at path: " + zpath + ", state: " + state);
        // Save into ZK
        saveModel(zpath, data);
    }

    /**
     * Common routine to save the data model to ZK.
     *
     * @param zpath the path within ZK
     * @param data  the data model to store at the specified path
     * @throws Exception if an error occurs while saving data to Zookeeper
     */
    private void saveModel(String zpath, Map<String, Object> data) throws Exception {
        // Convert the data back to a JSON string
        final String zkDataAsString = gson.toJson(data, new TypeToken<Map<String, Object>>() {
        }.getType());

        if (zkCli.checkExists().forPath(zpath) == null) {
            log.fine("Saving data into Zookeeper at new path: " + zpath);
            zkCli.create().creatingParentContainersIfNeeded().forPath(
                    zpath, zkDataAsString.getBytes(Charset.forName("UTF-8")));
        } else {
            log.fine("Saving data into Zookeeper at existing path: " + zpath);
            zkCli.setData().forPath(zpath, zkDataAsString.getBytes(Charset.forName("UTF-8")));
        }
    }

}
