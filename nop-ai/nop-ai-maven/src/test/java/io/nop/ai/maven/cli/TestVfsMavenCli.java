package io.nop.ai.maven.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestVfsMavenCli {

    @TempDir
    File tempDir;

    private VfsMavenCli newCli() {
        File baseDir = new File(tempDir, "base");
        File deltaDir = new File(tempDir, "delta");
        baseDir.mkdirs();
        deltaDir.mkdirs();
        return new VfsMavenCli(baseDir, deltaDir);
    }

    @Test
    public void testBuildMavenCommandWithGoals() {
        VfsMavenCli cli = newCli();

        List<String> command = cli.buildMavenCommand("mvn", "compile");

        assertEquals("mvn", command.get(0));
        assertTrue(command.contains("-Dvfs.enabled=true"));
        assertTrue(command.contains("-Dvfs.base.dir=" + cli.getBaseDir().getAbsolutePath()));
        assertTrue(command.contains("-Dvfs.delta.dir=" + cli.getDeltaDir().getAbsolutePath()));
        assertTrue(command.contains("compile"));
        assertEquals(1, command.stream().filter("compile"::equals).count());
    }

    @Test
    public void testBuildMavenCommandWithExtraArgs() {
        VfsMavenCli cli = newCli();

        List<String> command = cli.buildMavenCommand("mvn",
                new String[]{"test"}, "-DskipTests=false", "-Dtest=MyTest");

        assertTrue(command.contains("test"));
        assertTrue(command.contains("-DskipTests=false"));
        assertTrue(command.contains("-Dtest=MyTest"));
    }

    @Test
    public void testBuildMavenCommandNoGoals() {
        VfsMavenCli cli = newCli();

        List<String> command = cli.buildMavenCommand("mvn", new String[0]);

        assertEquals("mvn", command.get(0));
        assertTrue(command.stream().noneMatch("compile"::equals));
    }

    @Test
    public void testBuildMavenCommandKeepsExtraArgSpaces() throws Exception {
        VfsMavenCli cli = newCli();
        File output = new File(cli.getBaseDir(), "target dir");
        output.mkdirs();
        Files.write(new File(output, "f.txt").toPath(), "x".getBytes());

        List<String> command = cli.buildMavenCommand("mvn", List.of("install"), List.of("-Dout=" + output.getAbsolutePath()));

        assertEquals(1, command.stream().filter(c -> c.contains("target dir")).count());
        assertTrue(command.stream().noneMatch(c -> c.startsWith("\"")));
    }
}
