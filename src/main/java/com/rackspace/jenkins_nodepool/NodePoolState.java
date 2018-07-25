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

import java.util.logging.Logger;

/**
 * An enumeration for the various NodePool states.
 *
 * @author Rackspace
 */
public enum NodePoolState {
    // This set of states is derived from the ZK state model in the OpenStack Infra nodepool sources. The data models
    // should match what we have here in our code.  Sadly, they didn't define a set of common data models where we could
    // import or generate from a shared specification - so we redefine it here.
    // Reference: https://github.com/openstack-infra/nodepool/blob/master/nodepool/zk.py#L28-L57

    // We are building this image (or node) but it is not ready for use.
    BUILDING("building"),
    // The image is being uploaded.
    UPLOADING("uploading"),
    // The image/upload/node is ready for use.
    READY("ready"),
    // The image/upload/node should be deleted.
    DELETING("deleting"),
    // The build failed.
    FAILED("failed"),
    // Node request is submitted/unhandled.
    REQUESTED("requested"),
    // Node request has been processed successfully.
    FULFILLED("fulfilled"),
    // Node request is being worked.
    PENDING("pending"),
    // Node is being tested
    TESTING("testing"),
    // Node is being used (note - the data model uses a hyphen, but we can't use a hyphen as a java symbol)
    IN_USE("in-use"),
    // Node has been used
    USED("used"),
    // Node is being held
    HOLD("hold"),
    // Initial state
    INIT("init"),
    // Aborted due to a transient error like overquota that should not count as a failed launch attempt
    ABORTED("aborted");

    /**
     * Our class logger.
     */
    private static final Logger LOG = java.util.logging.Logger.getLogger(NodePoolState.class.getName());

    private final String stateString;

    /**
     * Creates a new NodePool state enum entity.
     *
     * @param stateString the NodePool state string value
     */
    NodePoolState(final String stateString) {
        this.stateString = stateString;
    }

    /**
     * Converts a NodePool state string into a NodePoolState enum object.
     *
     * @param stateString the string representation of the NodePool state
     * @return a NodePoolState object based on the string value, otherwise returns null if there is not match of the
     * specified string to a NodePoolState.
     */
    public static NodePoolState fromString(final String stateString) {
        // The 'in-use' state is a bit tricky in Java as we can't use a hyphen in the symbol name. This is how the
        // nodepool authors defined it so we must accept it. All the other symbols are fine and we can use the default
        // valueOf() routine.
        // So, we use this routine to properly translate it from a string to the corresponding symbol.  We are generous
        // and accept either all one word, with a hyphen or with an underscore for the in-use string - in all cases it
        // maps to the proper IN_USE enum symbol.
        if (stateString.equalsIgnoreCase("inuse") ||
                stateString.equalsIgnoreCase("in-use") ||
                stateString.equalsIgnoreCase("in_use")) {
            return NodePoolState.IN_USE;
        } else {
            for (NodePoolState s : NodePoolState.values()) {
                if (s.stateString.equalsIgnoreCase(stateString)) {
                    return s;
                }
            }
            final String msg = String.format("No NodePoolState match for string: %s", stateString);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Returns the specification string associated with this NodePool state object.
     *
     * @return the specification string associated with this NodePool state object.
     */
    public String getStateString() {
        return toString();
    }

    /**
     * Returns the string representation of this object.
     *
     * @return the string representation of this object.
     */
    @Override
    public String toString() {
        return this.stateString;
    }
}
