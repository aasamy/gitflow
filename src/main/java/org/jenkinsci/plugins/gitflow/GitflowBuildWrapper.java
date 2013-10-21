package org.jenkinsci.plugins.gitflow;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * User: Antony
 * Date: 10/19/13
 * Time: 1:52 AM
 */
public class GitflowBuildWrapper extends BuildWrapper {
    private static Logger LOG = Logger.getLogger(GitflowBuildWrapper.class.getName());

    @DataBoundConstructor
    public GitflowBuildWrapper() {
    }

    /**
     * Runs before the {@link hudson.tasks.Builder} runs (but after the checkout has occurred), and performs a set up.
     *
     * @param build    The build in progress for which an {@link hudson.tasks.BuildWrapper.Environment} object is created.
     *                 Never null.
     * @param launcher This launcher can be used to launch processes for this build.
     *                 If the build runs remotely, launcher will also run a job on that remote machine.
     *                 Never null.
     * @param listener Can be used to send any message.
     * @return non-null if the build can continue, null if there was an error
     *         and the build needs to be aborted.
     * @throws java.io.IOException terminates the build abnormally. Hudson will handle the exception
     *                             and reports a nice error message.
     * @since 1.150
     */
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        System.out.println("##### GitflowBuildWrapper - setup. build: " + build);

        return new HelloWorldBuildEnvironment();
    }

    class HelloWorldBuildEnvironment extends Environment {
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        /**
         * Returns true if this task is applicable to the given project.
         *
         * @return true to allow user to configure this post-promotion task for the given project.
         * @see hudson.model.AbstractProject.AbstractProjectDescriptor#isApplicable(hudson.model.Descriptor)
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        /**
         * Human readable name of this kind of configurable object.
         */
        @Override
        public String getDisplayName() {
            return "Use GitFlow";
        }
    }
}