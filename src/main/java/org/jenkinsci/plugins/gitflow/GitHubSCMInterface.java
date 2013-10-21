package org.jenkinsci.plugins.gitflow;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * User: Antony
 * Date: 10/17/13
 * Time: 6:47 PM
 */
public class GitHubSCMInterface extends AbstractSCMInterface {
    private static Logger logger = Logger.getLogger(GitHubSCMInterface.class.getName());
    final static String LOG_PREFIX = "[GitFlow] ";

    private String branch;

    @DataBoundConstructor
    public GitHubSCMInterface(String branch){
        if(branch != null && !branch.isEmpty()) {
            this.branch = branch;
        } else {
            this.branch = "master";
        }
    }

    public String getBranch() {
        return this.branch;
    }

    @Override
    public Descriptor<AbstractSCMInterface> getDescriptor() {
        return super.getDescriptor();
    }

    /**
     * This function is called after the SCM plugin has updated the workspace
     * with remote changes. When this function has been run, the workspace must
     * be ready to perform a build and tests. The integration branch must be
     * checked out, and the given commit must be merged into it.
     *
     * @param commit This commit represents the code that must be checked out.
     * @throws hudson.AbortException    It is not possible to leave the workspace in a state as described above.
     * @throws java.io.IOException      A repository could not be reached.
     * @throws IllegalArgumentException The given repository is not in a valid condition.
     */
    @Override
    public void prepareWorkspace(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit)
            throws AbortException, IOException, IllegalArgumentException {

        try {
            GitClient gitClient = getGitClient(build, listener);
            EnvVars environment = build.getEnvironment(listener);

            // Get the HEAD revision on the integration branch
            String gitURL = environment.get("GIT_URL");
            ObjectId branchRevId = gitClient.getHeadRev(gitURL, getBranch());

            // Checkout the HEAD of the integration branch
            listener.getLogger().println(LOG_PREFIX + "Checking out branch " + getBranch() + " revision " + branchRevId.getName());
            gitClient.checkoutBranch(getBranch(), branchRevId.getName());

            // And merge the topic branch into the integration branch
            String branch = environment.get(GitSCM.GIT_BRANCH);
            listener.getLogger().println(LOG_PREFIX + "Merging branch " + branch + " revision " + commit.getId() + " into " + getBranch());
            try {
                gitClient.merge(ObjectId.fromString((String) commit.getId()));
            } catch (GitException e) {
                listener.getLogger().println(LOG_PREFIX + "Automatic merge failed");
//                listener.getLogger().println(LOG_PREFIX + "Cleaning up workspace");
//                gitClient.checkoutBranch(getBranch(), branchRevId.getName());
//                gitClient.clean();
//                build.setResult(Result.FAILURE);

                throw new AbortException("Merge into integration branch exited unexpectedly. " + e.getMessage());
            }
        } catch (InterruptedException e) {
            throw new AbortException("Merge into integration branch exited unexpectedly");
        }

    }

    /**
     * Calculate and return the next commit from the argument
     *
     * @return The next pending commit. If no commit is pending null is returned.
     * @throws java.io.IOException      A repository could not be reached.
     * @throws IllegalArgumentException The given repository is not in a valid condition.
     */
    @Override
    public Commit<?> nextCommit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, Commit<?> commit) throws IOException, IllegalArgumentException {
        listener.getLogger().println(LOG_PREFIX + "Previous commit: " + (commit != null ? commit.getId() : "null"));

        Commit<String> next = null;
        if (build.getResult() == null) {

            GitClient gitClient;
            EnvVars environment;
            try {
                gitClient = getGitClient(build, listener);
                environment = build.getEnvironment(listener);

                // Get the HEAD revision on the integration branch
                String gitURL = environment.get("GIT_URL");
                ObjectId branchRevId = gitClient.getHeadRev(gitURL, getBranch());

                // Ge the revision on the topic branch where this build is triggered
                String commitRev = environment.get(GitSCM.GIT_COMMIT);
                ObjectId commitRevId = ObjectId.fromString(commitRev);

                // Are there any revisions in the topic branch not in the integration branch
                List<String> revs = gitClient.showRevision(branchRevId, commitRevId);

                // revs is the git command dump... crude parsing to figure out revisions
                if (revs.size() > 0 && revs.get(0) != null && !revs.get(0).isEmpty()) {
                    listener.getLogger().println(LOG_PREFIX + "Merging revisions: ");
                    for (String rev : revs) {
                        final String prefix = "commit ";
                        if (rev.startsWith(prefix)) {
                            listener.getLogger().println(LOG_PREFIX + "    " + rev.substring(prefix.length()));
                        }
                    }

                    if (commit != null && commit.getId().equals(commitRev)) {
                        // if the build is triggered on the same revision again, do nothing
                        listener.getLogger().println(LOG_PREFIX + "Already seen commit: " + commitRev);
                    } else {
                        // Else try to merge all revisions unto the latest
                        next = new Commit<String>(commitRev);
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

        } else {
            // Hack: nextCommit gets called twice.
            // We cannot base our logic just on previous commit.
            // It alternates between some value and null causing infinite builds
            listener.getLogger().println(LOG_PREFIX + "Build result already set to: " + build.getResult());
        }

        return next;
    }

    private GitClient getGitClient(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        SCM scm = build.getProject().getScm();
        if (scm != null && scm instanceof GitSCM) {
            GitSCM gitSCM = (GitSCM) scm;
            String gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);

            EnvVars environment = build.getEnvironment(listener);

            return Git.with(listener, environment)
                    .in(build.getWorkspace())
                    .using(gitExe) // only if you want to use Git CLI
                    .getClient();

        } else {
            listener.getLogger().println(LOG_PREFIX + "Configured SCM is not Git. Aborting...");
            throw new InterruptedException("Configured SCM is not Git");
        }
    }

    @Override
    public void commit(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        // TODO: How to get remote name
        listener.getLogger().println(LOG_PREFIX + "Committing. Pushing to origin " + getBranch());
        GitClient gitClient = getGitClient(build, listener);
        gitClient.push("origin", getBranch());
    }

    @Override
    public void rollback(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        listener.getLogger().println(LOG_PREFIX + "Rollback. Cleaning up workspace");
        GitClient gitClient = getGitClient(build, listener);
        gitClient.clean();
    }

    @Extension
    public static final class DescriptorImpl extends SCMInterfaceDescriptor<GitHubSCMInterface> {

        public String getDisplayName(){
            return "GitHub";
        }

        @Override
        public GitHubSCMInterface newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            GitHubSCMInterface i = (GitHubSCMInterface) super.newInstance(req, formData);

            i.branch = formData.getJSONObject("scmInterface").getString("branch");

            save();
            return i;
        }
    }
}
