package com.rackspace.jenkins_nodepool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;

/**
 * A zookeeper watcher for node pool activity.
 *
 * @author Rackspace
 */
public class NodePoolRequestStateWatcher implements CuratorWatcher {

    /**
     * Our class logger.
     **/
    private static final Logger log = getLogger(NodePoolRequestStateWatcher.class.getName());

    private final CountDownLatch latch = new CountDownLatch(1);
    private final Gson gson = new Gson();
    private final CuratorFramework curatorFramework;
    private final String zpath;
    private final RequestState desiredState;

    /**
     * Creates a new node pool request state watcher for the specified path and request state value.
     *
     * @param curatorFramework a reference to the Curator Framework
     * @param zpath            the zookeeper node path
     * @param desiredState     the desired state to watch for
     */
    NodePoolRequestStateWatcher(CuratorFramework curatorFramework, String zpath, RequestState desiredState) {
        this.curatorFramework = curatorFramework;
        this.zpath = zpath;
        this.desiredState = desiredState;

        try {
            registerWatch(zpath);
        } catch (Exception e) {
            latch.countDown();
            log.warning(e.getClass().getSimpleName() + " while registering watcher. Message: " +
                    e.getLocalizedMessage());
        }
    }

    /**
     * Convenience method to register a watch on the specified path.
     *
     * @param zpath the path in ZooKeeper to watch
     * @throws Exception if an error occurrs registering the ZooKeeper Watch
     */
    private void registerWatch(String zpath) throws Exception {

        log.fine("Creating watch for state:" + desiredState + " on path:" + zpath);
        // The watch will be triggered by a successful operation that creates/delete the node or sets the data on the node.
        final Stat stat = curatorFramework.checkExists().usingWatcher(this).forPath(zpath);
        if (stat == null) {
            log.warning("Created watch on non-existent path: " + zpath);
        }
    }

    /**
     * Our event callback method.  When something changes and the request is fulfilled, we'll adjust our latch and the
     * blocking call to waitUntilDone() will return.
     *
     * @param event the watch event value provided by the callback
     */
    @Override
    public void process(WatchedEvent event) {
        log.fine("Watch event received:" + event.toString());

        try {
            // Re-register if not a NodeDeleted and not a None event type
            if (event.getType() != Watcher.Event.EventType.NodeDeleted && event.getType() != Watcher.Event.EventType.None) {
                // Continuous watching on znodes requires reset/re-register of watches after every event/trigger.
                // Need to quickly re-register as the pending -> fulfilled state transition may happen fast - don't want
                // to miss the event.
                registerWatch(zpath);
            }

            // Only interested in the node data changed event
            if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {

                // Let's fetch the data from ZK directly so we can read the state value
                final RequestState state = getStateFromPath();
                log.fine("Watch event received event type:" + event.getType() + ". State is:" + state);

                // Are we in the desired state yet? No reason to continue if the request failed.
                if (state == desiredState || state == RequestState.failed) {
                    latch.countDown();
                } else {
                    log.fine("Watch event ignoring event type:" + event.getType() +
                            ". Fetched state is: " + state + "/" + getStateFromPath());
                }
            } else {
                log.fine("Watch event ignoring event type:" + event.getType());
            }
        } catch (Exception e) {
            log.warning(e.getClass().getSimpleName() +
                    " occurred while updating state from Zookeeper. Message: " + e.getLocalizedMessage() +
                    ". Ignoring the event.");
        }
    }

    /**
     * Waits until event fires or until the specified timeout.
     *
     * @param timeout the timeout value
     * @param unit    the unit of the timeout value, typically TimeUnit.SECONDS
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
    public void waitUntilDone(long timeout, TimeUnit unit) throws InterruptedException {
        latch.await(timeout, unit);
    }

    /**
     * Convenience routine to return the state value from the zpath.
     *
     * @return the RequestState value stored in the zpath.
     * @throws KeeperException      if the ZooKeeper server signals an error with a non-zero error code
     * @throws InterruptedException if the ZooKeeper server transaction is interrupted.
     */
    RequestState getStateFromPath() throws Exception {

        // Let's fetch the data from ZK directly so we can read the state value

        // Read the raw values
        final byte[] bytes = curatorFramework.getData().forPath(this.zpath);

        // Convert to a string
        final String jsonString = new String(bytes, Charset.forName("UTF-8"));

        // Load into a map
        final Map<String, Object> data = gson.fromJson(jsonString, new TypeToken<Map<String, Object>>() {
        }.getType());

        // Grab the specific state value
        return RequestState.valueOf((String) data.get("state"));
    }
}
