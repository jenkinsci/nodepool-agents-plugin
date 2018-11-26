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

import com.google.gson.Gson;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * A wrapper class for Zookeeper Objects.
 */
public class ZooKeeperObject<T> {
    /**
     * This classes logger.
     */
    private static final Logger LOG = Logger.getLogger(ZooKeeperObject.class.getName());

    private final Class<T> typeParameterClass;

    /**
     * JSON reader/writer helper
     */
    private static final Gson GSON = new Gson();

    /**
     * Path to ZNode
     */
    String path;

    /**
     * An identifier associated with a ZNode
     */
    String zKID;

    /**
     * A reference to the Zookeeper connection framework
     */
    private CuratorFramework conn;

    /**
     * Creates a new Zookeeper object model.
     *
     * @param path               the Zookeeper node path
     * @param zKID               the zookeeper node id
     * @param conn               the ZK connection object
     * @param typeParameterClass the type of the class - used to assist in marshalling/unmarshalling
     */
    public ZooKeeperObject(String path, String zKID, CuratorFramework conn, Class<T> typeParameterClass) {
        this.path = path;
        this.zKID = zKID;
        this.conn = conn;
        this.typeParameterClass = typeParameterClass;
    }

    /**
     * Returns the connection string associated with this object.
     *
     * @return the connection string associated with this object.
     */
    public String getConnectionString() {
        return conn.getZookeeperClient().getCurrentConnectionString();
    }

    /**
     * Returns the path for the object.
     *
     * @return the path for the underlying object.
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the path for the object.
     *
     * @param path the path value
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Returns the id for the object.
     *
     * @return the id for the object.
     */
    public String getZKID() {
        return zKID;
    }

    /**
     * Sets the ID for the object.
     *
     * @param zKID the ID value
     */
    public void setZKID(String zKID) {
        this.zKID = zKID;
    }

    /**
     * Creates the associated Zookeeper node if it doesn't already exist.
     *
     * @param model the data model
     * @return the created path
     * @throws ZookeeperException if an error occurs while checking or creating the Zookeeper node
     */
    private String createZNode(final T model) throws ZookeeperException {
        try {
            // Convert to JSON and save to ZK
            final String jsonStringModel = GSON.toJson(model, model.getClass());
            //LOG.log(FINEST, format("Saving model, path: %s, data: %s", path, jsonStringModel));

            if (!exists()) {
                conn.create().creatingParentsIfNeeded().forPath(path, jsonStringModel.getBytes(StandardCharsets.UTF_8));
            } else {
                conn.setData().forPath(path, jsonStringModel.getBytes(StandardCharsets.UTF_8));
            }
            return path;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while creating ZK node %s. Message: %s",
                    e.getClass().getSimpleName(), getPath(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }

    /**
     * Creates the associated Zookeeper node if it doesn't already exist.
     *
     * @param model the data model
     * @param mode  the Zookeeper create mode
     * @return the created path
     * @throws ZookeeperException if an error occurs while checking or creating the Zookeeper node
     */
    private String createZNode(final T model, final CreateMode mode) throws ZookeeperException {
        try {
            // Convert to JSON and save to ZK
            final String jsonStringModel = GSON.toJson(model, model.getClass());
            //LOG.log(FINEST, format("Saving model, path: %s, data: %s", path, jsonStringModel));
            return conn.create().creatingParentsIfNeeded().withMode(mode).forPath(path, jsonStringModel.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while creating ZK node %s. Message: %s",
                    e.getClass().getSimpleName(), getPath(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }

    /**
     * Returns True if the associated ZNode exists, false otherwise.
     *
     * @return true if the node exists, false otherwise
     * @throws ZookeeperException if an error occurs while checking the Zookeeper node
     */
    private boolean exists() throws ZookeeperException {
        try {
            return conn.checkExists().forPath(getPath()) != null;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while checking if ZK node %s exists. Message: %s",
                    e.getClass().getSimpleName(), getPath(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }

    /**
     * Saves the specified model to Zookeeper
     *
     * @param model the data model
     * @return the path for the data
     * @throws ZookeeperException if an error occurs while saving the data model to Zookeeper node
     */
    public String save(final T model) throws ZookeeperException {
        try {
            return createZNode(model);
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while saving ZK data. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }

    /**
     * Saves the specified model to Zookeeper using the given create mode attribute. Note: if the CreateMode is one of
     * the SEQUENTIAL varieties, then the generated node id (ZKID) will will be updated appropriately.
     *
     * @param model the data model
     * @param mode  the Zookeeper create mode
     * @return the path for the data
     * @throws ZookeeperException if an error occurs while saving the data model to Zookeeper node
     */
    public String save(final T model, final CreateMode mode) throws ZookeeperException {
        try {
            String generatedPath;
            if (mode == CreateMode.EPHEMERAL_SEQUENTIAL || mode == CreateMode.PERSISTENT_SEQUENTIAL) {
                // Create the node - should return the generated path
                generatedPath = createZNode(model, mode);

                //LOG.log(FINEST, format("Setting sequential path of type %s to: %s", typeParameterClass.getSimpleName(), generatedPath));
                setPath(generatedPath);
                setZKID(idFromPath(generatedPath));
            } else {
                LOG.log(FINEST, format("Setting path of type %s to: %s", typeParameterClass.getSimpleName(), path));
                generatedPath = createZNode(model);
            }

            return generatedPath;
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while saving ZK data. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }

    /**
     * See load(Boolean)
     *
     * @return the data model as a Java object
     * @throws ZookeeperException if an error occurs while reading the data model from the Zookeeper node
     */
    public T load() throws ZookeeperException {
        return load(false);
    }

    /**
     * Reads the data model from Zookeeper.
     *
     * @param create Creates a new znode if the required path doesn't exist
     * @return the data model as a Java object
     * @throws ZookeeperException if an error occurs while reading the data model from the Zookeeper node
     */
    public T load(Boolean create) throws ZookeeperException {
        try {
            if (exists()) {
                byte[] bytes = conn.getData().forPath(this.path);
                // If no data or empty value
                if (bytes == null || bytes.length == 0) {
                    // Return a new empty model
                    return typeParameterClass.newInstance();
                } else {
                    // Convert the value to a string with the proper encoding and unmarshall into the appropriate type
                    final String jsonString = new String(bytes, StandardCharsets.UTF_8);
                    //LOG.log(FINEST, format("Loaded model, path: %s, data: %s", path, jsonString));
                    return GSON.fromJson(jsonString, typeParameterClass);
                }
            } else if (create) {
                // Return a new empty model
                return typeParameterClass.newInstance();
            } else {
                // Don't create a new node, because create is false
                throw new ZookeeperException("Can't read from non-existent znode: " + this.path);
            }
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while loading ZK data. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }

    /**
     * Deletes the associated Zookeeper Node.
     */
    public void delete() {
        try {
            if (exists()) {
                conn.delete().deletingChildrenIfNeeded().forPath(getPath());
                LOG.log(FINEST, format("Deleted path: %s", getPath()));
            } else {
                LOG.log(FINEST, format("Path already deleted: %s", getPath()));
            }
        } catch (Exception e) {
            // not sure what else we can do at this point.
            LOG.log(INFO, format("Failed to delete node at path: %s. Message: %s", getPath(), e.getLocalizedMessage()));
        }
    }

    /**
     * Extracts the ID from the provided path.
     *
     * @param path the path to the data
     * @return the ID portion of the path.
     * @throws NodePoolException if path is null, empty or otherwise malformed
     */
    public String idFromPath(final String path) throws NodePoolException {
        if (path == null || path.isEmpty()) {
            throw new NodePoolException("Path is null or empty");
        }

        if (!path.startsWith("/")) {
            throw new NodePoolException("Path is malformed - should start with a '/' character");
        }

        if (path.contains("-")) {
            final List<String> parts = Arrays.asList(path.split("-"));
            final String id = parts.get(parts.size() - 1);
            if (id == null || id.length() == 0) {
                throw new NodePoolException("Path is malformed - extracted ID is null or empty");
            } else {
                try {
                    Long.parseLong(id);
                } catch (NumberFormatException nfe) {
                    throw new NodePoolException(format("Path is malformed - extracted ID is invalid: %s", id));
                }
                return id;
            }
        } else {
            throw new NodePoolException("Malformed node path while looking for request id: " + path);
        }
    }

    /**
     * Returns the associated data model as a JSON string
     *
     * @return the associated data model as a JSON string
     * @throws ZookeeperException if an error occurs while loading and converting the data model
     */
    public String asJSON() throws ZookeeperException {
        try {
            if (exists()) {
                byte[] bytes = conn.getData().forPath(this.path);
                // If no data or empty value
                if (bytes == null || bytes.length == 0) {
                    // Return a new empty model
                    return "{}";
                } else {
                    // Convert the value to a string with the proper encoding and return
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } else {
                // Return a new empty model
                return "{}";
            }
        } catch (Exception e) {
            LOG.log(WARNING, format("%s occurred while loading ZK data. Message: %s",
                    e.getClass().getSimpleName(), e.getLocalizedMessage()));
            // Super annoying that the ZK curator framework throws general exceptions all over the place - return our
            // specialized type so that we can handle this separately if desired
            throw new ZookeeperException(e);
        }
    }
}
