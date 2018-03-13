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

import hudson.model.Computer;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SingleUseRetentionStrategy This strategy will delete a computer once it is
 * idle, if it has at least one associated build.
 *
 * @author hughsaunders
 */
public class SingleUseRetentionStrategy extends RetentionStrategy {

    private static final Logger LOG = Logger.getLogger(SingleUseRetentionStrategy.class.getName());

    /**
     *
     * @param c Computer
     * @return
     */
    @Override
    public long check(Computer c) {
        if (!c.getBuilds().isEmpty() && c.countBusy() == 0) {
            try {
                c.doDoDelete();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        return 2;
    }
}
