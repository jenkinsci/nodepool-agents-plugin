package com.rackspace.jenkins_nodepool;


import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/*
 * The MIT License
 *
 * Copyright 2018 Rackspace.
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

/**
 *
 * @author Rackspace
 */
public class HoldStep extends Builder implements SimpleBuildStep {

    private static final Logger LOG = Logger.getLogger(HoldStep.class.getName());

    private final String reason;
    private final String duration;
    private PrintStream consoleLog;

    @DataBoundConstructor
    public HoldStep(String duration, String reason){
        this.reason = reason;
        this.duration = duration;
    }

    public HoldStep(String duration){
        this(duration, "Held from Pipeline Step");
    }

    public HoldStep(){
        this("1d");
    }

    public String getReason(){
        return reason;
    }
    public String getDuration(){
        return duration;
    }

    private void log(String message, Level level){
        consoleLog.println(message);
        LOG.log(level, message);
    }
    private void log(String message){
        log(message, Level.INFO);
    }
    private void setConsoleLogger(PrintStream logger){
        this.consoleLog = logger;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        setConsoleLogger(listener.getLogger());
        // try and find the current computer
        // use a few different methods so freestyle and pipeline jobs work
        Executor executor = Executor.currentExecutor();
        VirtualChannel vc = launcher.getChannel();
        FilePath fp = new FilePath(vc, "/");
        Job job = run.getParent();

        Computer computer;
        if (executor != null){
            computer = executor.getOwner();
        } else if (Computer.currentComputer() != null) {
            computer = Computer.currentComputer();
        } else if(fp.toComputer() != null){
            computer = fp.toComputer();
        } else {
            log("Can't hold as theres no node allocated :(");
            return;
        }

        if (computer instanceof NodePoolComputer){
            try {
                NodePoolComputer npc = (NodePoolComputer) computer;
                NodePoolSlave nps = (NodePoolSlave) npc.getNode();
                if (nps != null){
                    String build_id = job.getDisplayName()+"-"+run.getNumber();
                    nps.setHeld(true);
                    nps.setHoldReason(reason);
                    nps.setHoldUser(build_id);
                    nps.setHoldUntil(duration, true);
                    log("Held node: " + npc.toString()
                      + " IP:"+nps.getNodePoolNode().getHost()
                      + " Hold Expiry Time: "+nps.getHoldUntilTimeFormatted());
                }
            } catch (Exception ex) {
                log("Failed to hold node: "+ex.toString(), Level.SEVERE);
            }
        }else {
            log("Can't hold as the current node is not a nodepool node");
        }
    }

    @Symbol("nodePoolHold")
    @Extension
    public static class DescriptorImple extends BuildStepDescriptor<Builder>{
        @Override
        public String getDisplayName() {
            return "Set NodePool hold from within a job";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> t) {
            return true;
        }
    }
}
