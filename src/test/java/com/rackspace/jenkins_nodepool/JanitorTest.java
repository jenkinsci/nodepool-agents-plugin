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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Mockito.when;

/**
 * Most of this class isn't testable due as Node.toComputer is final so can't be
 * mocked :((
 *
 * @author hughsaunders
 */
public class JanitorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    Janitor janitor;
    Mocks m;

    @Before
    public void setUp() {
        janitor = new Janitor();
        m = new Mocks();
    }

    /**
     * Test of hasInvalidLabel method, of class Janitor.
     */
    @Test
    public void testHasInvalidLabel() {
        NodePools nps = NodePools.get();
        nps.getNodePools().add(m.np);

        assertFalse(janitor.hasInvalidLabel(m.nps));
        when(m.nps.getLabelString()).thenReturn("non matching label");
        assertTrue(janitor.hasInvalidLabel(m.nps));
    }


}
