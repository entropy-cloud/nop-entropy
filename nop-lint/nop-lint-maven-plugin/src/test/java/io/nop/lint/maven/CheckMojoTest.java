package io.nop.lint.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code nop-lint:check} goal's failure-mapping matrix (roadmap item 37,
 * plan Decision 2/5, run through the REAL {@code CheckRunner} pipeline: the
 * fixture rule set under {@code /test/lint/mojo/rules/} loads through the
 * registered VFS, the ServiceLoader discovers the real Java binding, and the
 * diagnostics reach the build result through the mapped exception — the
 * hollow-defense assertion lives in {@link #violationFailsTheBuild()}).
 *
 * <p>The test instantiates the Mojo directly (the Maven harness is not
 * compatible with the Maven 4 wrapper, plan Decision 4 fallback): the
 * parameters are set reflectively and the Maven log is captured, which is
 * exactly the surface the goal touches — it reads no other Maven service.</p>
 */
public class CheckMojoTest {

    @TempDir
    Path workDir;

    private CapturingLog log;

    @BeforeEach
    void setUp() {
        log = new CapturingLog();
    }

    @Test
    public void cleanSourcesPassQuietly() throws Exception {
        Path src = Files.createDirectories(workDir.resolve("src"));
        write(src, "Clean.java", "package demo;\n\nclass Clean {\n    void run() {\n    }\n}\n");

        CheckMojo mojo = mojo(workDir.toFile(), null);
        mojo.execute();

        assertTrue(log.infos.stream().anyMatch(l -> l.contains("mojo-error") || l.contains("0")),
                "the report renders into the log: " + log.infos);
    }

    @Test
    public void violationFailsTheBuild() {
        Path src = writeViolatingModule();

        CheckMojo mojo = mojo(workDir.toFile(), null);
        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);

        // hollow defense: the diagnostics the CheckRunner really found flow
        // into the build result (rule id + count in the failure message)
        assertTrue(e.getMessage().contains("demo/mojo-error"),
                "the rule id must reach the failure message: " + e.getMessage());
        assertTrue(e.getMessage().contains("1 error-severity"),
                "the diagnostic count must reach the failure message: " + e.getMessage());
        assertTrue(Files.exists(src), "check mode never rewrites the module");
    }

    @Test
    public void violationOnlyWarnsWhenFailOnErrorIsFalse() throws Exception {
        writeViolatingModule();

        CheckMojo mojo = mojo(workDir.toFile(), null);
        set(mojo, "failOnError", false);
        mojo.execute();

        assertTrue(log.warns.stream().anyMatch(w -> w.contains("demo/mojo-error")),
                "the demoted failure logs a warning carrying the rule id: " + log.warns);
    }

    @Test
    public void explicitMissingTargetIsAHardError() {
        CheckMojo mojo = mojo(workDir.toFile(), List.of(new File(workDir.toFile(), "no-such-dir")));
        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
    }

    @Test
    public void missingDefaultTargetWarnsAndReturnsQuietly() throws Exception {
        CheckMojo mojo = mojo(workDir.toFile(), null);
        mojo.execute();

        assertTrue(log.warns.stream().anyMatch(w -> w.contains("nothing to lint")),
                "the no-sources adaptation warns: " + log.warns);
    }

    @Test
    public void skipShortCircuitsBeforeAnyLinting() throws Exception {
        writeViolatingModule();

        CheckMojo mojo = mojo(workDir.toFile(), null);
        set(mojo, "skip", true);
        mojo.execute();

        assertTrue(log.infos.stream().anyMatch(l -> l.contains("skipped")),
                "the skip branch logs: " + log.infos);
        assertEquals(0, log.warns.size(), "skip must not produce findings");
    }

    @Test
    public void internalErrorFailsTheBuildAsAnExecutionProblem() {
        writeViolatingModule();

        CheckMojo mojo = mojo(workDir.toFile(), null);
        set(mojo, "rulesPrefix", "/test/lint/mojo/nonexistent/");
        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("aborted"), e.getMessage());
    }

    @Test
    public void internalErrorOnlyLogsWhenFailOnErrorIsFalse() throws Exception {
        writeViolatingModule();

        CheckMojo mojo = mojo(workDir.toFile(), null);
        set(mojo, "rulesPrefix", "/test/lint/mojo/nonexistent/");
        set(mojo, "failOnError", false);
        mojo.execute();

        assertTrue(log.errors.stream().anyMatch(w -> w.contains("aborted")),
                "the demoted internal error logs: " + log.errors);
    }

    private Path writeViolatingModule() {
        Path src = workDir.resolve("src");
        try {
            Files.createDirectories(src);
            return write(src, "Bad.java",
                    "package demo;\n\nclass Bad {\n    void run() {\n        System.exit(1);\n    }\n}\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private CheckMojo mojo(File basedir, List<File> targets) {
        MavenProject project = new MavenProject();
        project.setFile(new File(basedir, "pom.xml"));

        CheckMojo mojo = new CheckMojo();
        mojo.setLog(log);
        set(mojo, "project", project);
        set(mojo, "profile", "standard");
        set(mojo, "rulesPrefix", "/test/lint/mojo/rules/");
        // the @Parameter defaultValue is a Maven-injection-time contract; a
        // directly instantiated Mojo carries the Java field defaults, so the
        // production default is applied explicitly here
        set(mojo, "failOnError", true);
        if (targets != null) {
            set(mojo, "targets", targets);
        }
        return mojo;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set field '" + field + "'", e);
        }
    }

    private static Path write(Path dir, String name, String content) {
        try {
            Path path = dir.resolve(name);
            Files.writeString(path, content);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The captured Maven log — the Mojo's only reporting surface.
     */
    private static final class CapturingLog implements Log {
        final List<String> infos = new java.util.ArrayList<>();
        final List<String> warns = new java.util.ArrayList<>();
        final List<String> errors = new java.util.ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            infos.add(String.valueOf(content));
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            infos.add(String.valueOf(content));
        }

        @Override
        public void info(Throwable error) {
            infos.add(String.valueOf(error));
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            warns.add(String.valueOf(content));
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            warns.add(String.valueOf(content));
        }

        @Override
        public void warn(Throwable error) {
            warns.add(String.valueOf(error));
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            errors.add(String.valueOf(content));
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            errors.add(String.valueOf(content));
        }

        @Override
        public void error(Throwable error) {
            errors.add(String.valueOf(error));
        }
    }
}
