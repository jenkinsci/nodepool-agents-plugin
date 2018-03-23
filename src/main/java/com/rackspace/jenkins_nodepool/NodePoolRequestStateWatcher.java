package com.rackspace.jenkins_nodepool;

import com.google.gson.Gson;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.nio.charset.Charset;
import java.util.HashMap;
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
public class NodePoolRequestStateWatcher implements Watcher {

    /**
     * Our class logger.
     **/
    private static final Logger log = getLogger(NodePoolRequestStateWatcher.class.getName());

    private final CountDownLatch latch = new CountDownLatch(1);
    private final Gson gson = new Gson();
    private final ZooKeeper zk;
    private final String zpath;
    private final RequestState desiredState;

    /**
     * Creates a new node pool request state watcher for the specified path and request state value.
     *
     * @param zk           a zookeeper client reference
     * @param zpath        the zookeeper node path
     * @param desiredState the desired state to watch for
     */
    NodePoolRequestStateWatcher(ZooKeeper zk, String zpath, RequestState desiredState) {
        this.zk = zk;
        this.zpath = zpath;
        this.desiredState = desiredState;

        try {
            log.info("Creating watch on path: " + zpath);
            zk.exists(zpath, this);
        } catch (KeeperException | InterruptedException e) {
            latch.countDown();
            log.warning(e.getClass().getSimpleName() + " while registering watcher. Message: " +
                    e.getLocalizedMessage());
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
        log.fine("Event received: " + event.toString());

        // Only interested in the node data changed event
        if (event.getType() == Event.EventType.NodeDataChanged) {
            try {
                // Let's fetch the data from ZK directly so we can read the state value
                final RequestState state = getStateFromPath();

                // Are we in the desired state?
                if (state == desiredState) {
                    latch.countDown();
                } else {
                    log.fine("Ignoring event type: " + event.getType() + " with state: " + state);
                }
            } catch (Exception e) {
                log.warning(e.getClass().getSimpleName() +
                        " occurred while updating state from Zookeeper. Message: " + e.getLocalizedMessage() +
                        ". Ignoring the event.");
            }
        } else {
            log.fine("Ignoring event type: " + event.getType());
        }
    }

    /**
     * Waits until event fires or until the specified timeout.
     *
     * @param timeout the timeout value
     * @param unit    the unit of the timeout value, typically TimeUnit.SECONDS
     * @return {@code true} if the wait returned before the timeout and {@code false}
     * if the waiting time elapsed before the count reached zero
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean waitUntilDone(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    /**
     * Convenience routine to return the state value from the zpath.
     *
     * @return the RequestState value stored in the zpath.
     * @throws KeeperException      if the ZooKeeper server signals an error with a non-zero error code
     * @throws InterruptedException if the ZooKeeper server transaction is interrupted.
     */
    private RequestState getStateFromPath() throws KeeperException, InterruptedException {

        // TODO: DAD - figure out how to use the NodeRequest object without all the extra stuff  (e.g. NodePool and Task)
        // Let's fetch the data from ZK directly so we can read the state value

        // Read the raw values
        byte[] bytes = zk.getData(this.zpath, null, null);

        // Convert to a string
        String jsonString = new String(bytes, Charset.forName("UTF-8"));

        // Load into a map
        Map data = gson.fromJson(jsonString, HashMap.class);

        // Grab the specific state value
        return RequestState.valueOf((String) data.get("state"));
    }
}
