package jgitdemo;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This JUnit test assumes that a git repo exists on github, and that valid github credentials are given in a property file.
 */
public class JGitPushAfterGcDemoTest {

    private static final Logger LOGGER = Logger.getLogger(JGitPushAfterGcDemoTest.class);
    private static final TextProgressMonitor PROGRESS_MONITOR = new TextProgressMonitor(new LoggerPrintWriter(LOGGER, Level.DEBUG));

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void reproducePushSlowdownAfterGc() throws Exception {
        final Properties properties = readProperties("github");
        final String gitUsername = properties.getProperty("user");
        final String gitPassword = properties.getProperty("password");
        final UsernamePasswordCredentialsProvider credProvider = new UsernamePasswordCredentialsProvider(gitUsername, gitPassword);
        final String githubProject = "ruby";
        final String remoteURL = "https://github.com/" + gitUsername + "/" + githubProject;

        // final Properties properties = readProperties("gitlab");
        // final String githubProject = "proctor-data.git";
        // final String remoteURL = "https://code.corp.indeed.com/" + gitUsername + "/" + githubProject;


        // choose beween cleaned-up folder and fixed folder
//        final File localFolder = folder.newFolder(githubProject);
        final File localFolder = new File("/tmp/" + githubProject);

        LOGGER.debug("Cloning from " + remoteURL + " as " + gitUsername + " to " + localFolder);
        final Git git = cloneOrPullRepository(remoteURL, localFolder, credProvider);

        /* *********** Call to reproduce later git push delay **********/
        LOGGER.debug("Call GC");
        git.gc().setProgressMonitor(PROGRESS_MONITOR).call();



        LOGGER.debug("Modify, add and commit");
        modifyAndCommitReadme(gitUsername, localFolder, git);

        LOGGER.debug("Push");
        final long start = System.currentTimeMillis();
        // push
        final Iterable<PushResult> pushResults = git.push()
                .setProgressMonitor(PROGRESS_MONITOR)
                .setCredentialsProvider(credProvider)
                .call();
        LOGGER.info("Push took " + (System.currentTimeMillis() - start));
        LOGGER.info(pushResults);
    }

    private void modifyAndCommitReadme(final String githubUsername, final File localFolder, final Git git) throws IOException, GitAPIException {
        final File readmeFile = new File(localFolder, "README.md");

        // modify README
        try (FileWriter fr = new FileWriter(readmeFile, true)) {
            fr.write(System.currentTimeMillis() + "\n");
        }

        git.add()
                .addFilepattern(readmeFile.getAbsolutePath())
                .call();
        git.commit()
                .setCommitter(githubUsername, githubUsername)
                .setAuthor(githubUsername, githubUsername)
                .setMessage("commit " + System.currentTimeMillis())
                .call();
    }

    private Properties readProperties(final String repo) {
        // needs local copy of template with credential
        try (final InputStream stream = getClass().getResourceAsStream(repo + ".properties")) {
            final Properties properties = new Properties();
            properties.load(stream);
            return properties;
            /* or properties.loadFromXML(...) */
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }


    private Git pullRepository(
            final File workingDir,
            final UsernamePasswordCredentialsProvider credProvider
    ) throws GitAPIException, IOException {
        final Git git = Git.open(workingDir);
        git.pull().setProgressMonitor(PROGRESS_MONITOR)
                .setRebase(true)
                .setCredentialsProvider(credProvider)
                .call();
        return git;
    }

    private Git cloneOrPullRepository(
            final String gitUrl,
            final File workingDir,
            final UsernamePasswordCredentialsProvider credentialsProvider
    ) throws GitAPIException, IOException {
        if (workingDir.exists()) {
            LOGGER.warn("Skipping clone, pulling");
            return pullRepository(workingDir, credentialsProvider);
        }
        return Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(workingDir)
                .setProgressMonitor(PROGRESS_MONITOR)
                .setCredentialsProvider(credentialsProvider)
                .call();
    }
}
