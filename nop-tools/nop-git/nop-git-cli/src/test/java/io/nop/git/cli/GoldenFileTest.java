package io.nop.git.cli;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-file contract: the recorded outputs under
 * {@code src/test/resources/golden} must reproduce byte-for-byte (exit code +
 * stdout + stderr). Valid without a system git — the recordings pin the
 * native-compatible format; {@link CliParityTest} additionally compares
 * against the live git when present.
 */
class GoldenFileTest {

    @TempDir
    Path base;

    static Stream<Path> goldens() throws IOException {
        URI dir = URI.create("golden:/");
        Path resources = Paths.get(System.getProperty("user.dir"))
                .resolve("src/test/resources/golden");
        assertTrue(Files.isDirectory(resources), "golden resources missing");
        return Files.list(resources).filter(p -> p.getFileName().toString().endsWith(".txt"));
    }

    @ParameterizedTest
    @MethodSource("goldens")
    void reproduceGolden(Path golden) throws Exception {
        String goldenContent = Files.readString(golden, StandardCharsets.UTF_8);
        String[] headerLines = goldenContent.split("\n", 3);
        String argsLine = headerLines[0].substring("### args: ".length());
        int expectedExit = Integer.parseInt(headerLines[1].substring("### exit: ".length()));

        String scenario = golden.getFileName().toString().split("__")[0];
        Path repo = buildFixture(scenario);

        String[] args = argsLine.split(" ");
        String[] full = new String[args.length + 2];
        full[0] = "-C";
        full[1] = repo.toAbsolutePath().toString();
        System.arraycopy(args, 0, full, 2, args.length);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int exit = GitCompatCli.run(full,
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));

        // byte-exact comparison: the recording wrote exactly this format
        String actual = "### args: " + argsLine + "\n### exit: " + exit
                + "\n--- stdout ---\n" + outBuf
                + "--- stderr ---\n" + errBuf;
        assertEquals(goldenContent, actual, "golden drift in " + golden.getFileName());
    }

    private Path buildFixture(String scenario) throws Exception {
        return switch (scenario) {
            case "empty" -> Fixture.createRepo(base, "empty");
            case "clean" -> Fixture.cleanSingleCommit(base, "clean");
            case "untracked" -> Fixture.untrackedContent(base, "untracked");
            case "unstaged" -> Fixture.dirtyUnstaged(base, "unstaged");
            case "staged" -> Fixture.dirtyStaged(base, "staged");
            case "rename" -> Fixture.stagedRename(base, "rename");
            case "remote" -> Fixture.withRemote(base, "remote");
            default -> throw new IllegalStateException("unknown scenario " + scenario);
        };
    }
}
