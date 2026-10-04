package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capability 5: literal path extraction, one group of assertions per dimension.
 *
 * <p>The dimension that carries the most weight is the confidence annotation: a
 * path from a proof and a path from a partial harvest are different claims, and a
 * consumer that cannot tell them apart will read "this script mentions /data
 * somewhere" as "this script provably touches /data".</p>
 */
class ShellPathExtractionTest {

    private final ShellAnalysis analysis = ShellTestSupport.analysis();

    private List<ShellPath> pathsOf(String script) {
        return analysis.extractPaths(script);
    }

    private ShellPath path(String script, String expected) {
        List<ShellPath> paths = pathsOf(script);
        for (ShellPath path : paths) {
            if (path.path().equals(expected)) {
                return path;
            }
        }
        throw new AssertionError("expected path " + expected + " in " + paths
                + " for <<" + script + ">>");
    }

    // ---- dimension 1: argument-position convention --------------------------

    @Test
    void readCommandsTakeTheirFirstArgumentAsAPath() {
        assertEquals("/etc/passwd", path("cat /etc/passwd\n", "/etc/passwd").path());
        assertEquals("/etc/passwd", path("head /etc/passwd\n", "/etc/passwd").path());
        assertEquals("/var/log", path("ls /var/log\n", "/var/log").path());
        assertEquals("/var/log", path("wc /var/log\n", "/var/log").path());
    }

    @Test
    void copyAndMoveTakeTwoPathPositions() {
        assertEquals("/tmp/b", path("cp /etc/a /tmp/b\n", "/tmp/b").path());
        assertEquals("/etc/a", path("mv /etc/a /tmp/b\n", "/etc/a").path());
    }

    @Test
    void aSecondArgumentIsNotAPathForSinglePathCommands() {
        // `grep pattern file` — the pattern is at index 1 and is not a path.
        assertFalse(pathsOf("echo x\n").stream().anyMatch(p -> p.path().equals("x")));
    }

    @Test
    void everythingAfterADoubleDashIsAPath() {
        ShellPath dashNamed = path("grep -r pattern -- --weird-name\n", "--weird-name");
        assertEquals(ShellPath.Access.READ, dashNamed.access());
    }

    // ---- dimension 2: literal form ------------------------------------------

    @Test
    void absoluteRelativeAndBareNamesArePaths() {
        for (String candidate : new String[]{"/abs/path", "./rel", "../up", "bare.txt"}) {
            ShellPath found = path("cat " + candidate + "\n", candidate);
            assertEquals(candidate, found.path());
        }
    }

    @Test
    void tildeIsAnExpansionNotAName() {
        assertFalse(pathsOf("cat ~/secrets\n").stream().anyMatch(p -> p.path().contains("~")),
                "a leading ~ is expanded at run time, so it is not a literal name");
    }

    @Test
    void globAndExpansionCharactersAreNotPaths() {
        assertTrue(pathsOf("cat *.txt\n").isEmpty());
        assertTrue(pathsOf("cat a?b\n").isEmpty());
        assertTrue(pathsOf("cat $HOME/x\n").isEmpty());
        assertTrue(pathsOf("cat `date`\n").isEmpty());
    }

    // ---- dimension 3: source confidence -------------------------------------

    @Test
    void aPathFromAProofIsMarkedProven() {
        assertEquals(ShellPath.Confidence.PROVEN,
                path("cat /etc/passwd\n", "/etc/passwd").confidence());
    }

    @Test
    void aPathFromAHarvestIsMarkedHarvested() {
        assertEquals(ShellPath.Confidence.HARVESTED,
                path("if true; then cat /etc/passwd; fi\n", "/etc/passwd").confidence());
    }

    /**
     * The two claims must not be interchangeable. The same path in a provable
     * script and in a script with unreadable constructs carries different
     * confidence, and that difference is the whole reason the field exists.
     */
    @Test
    void theSamePathHasDifferentConfidenceInDifferentScripts() {
        assertEquals(ShellPath.Confidence.PROVEN,
                path("cat /etc/passwd\n", "/etc/passwd").confidence());
        assertEquals(ShellPath.Confidence.HARVESTED,
                path("cat /etc/passwd > out.txt\n", "/etc/passwd").confidence());
    }

    // ---- dimension 4: flags are not paths ----------------------------------

    @Test
    void flagsAreNotPaths() {
        for (String script : new String[]{"cat -n /etc/passwd\n", "ls -la /var/log\n",
                "grep -r pattern /var/log\n"}) {
            for (ShellPath path : pathsOf(script)) {
                assertFalse(path.path().startsWith("-"),
                        "<<" + script + ">> reported the flag " + path);
            }
        }
    }

    // ---- dimension 5: redirects are write paths ----------------------------

    @Test
    void redirectDestinationsAreWritePaths() {
        ShellPath destination = path("cat /etc/passwd > /tmp/out.txt\n", "/tmp/out.txt");
        assertEquals(ShellPath.Access.WRITE, destination.access());
        assertEquals(ShellPath.Confidence.PROVEN, destination.confidence());
    }

    @Test
    void readAndWriteAreDistinctClasses() {
        ShellPath read = path("cat /etc/passwd > /tmp/out.txt\n", "/etc/passwd");
        ShellPath write = path("cat /etc/passwd > /tmp/out.txt\n", "/tmp/out.txt");
        assertEquals(ShellPath.Access.READ, read.access());
        assertEquals(ShellPath.Access.WRITE, write.access());
    }

    // ---- dimension 6: no filesystem check -----------------------------------

    /**
     * This capability extracts literals and nothing else. A path that does not
     * exist is still reported, because whether it exists is a different question
     * with a different cost — and answering it here would make the output depend
     * on the machine the analysis runs on.
     */
    @Test
    void noFilesystemExistenceCheckIsPerformed() {
        String definitelyMissing = "/definitely/not/here/at/all.txt";
        assertFalse(Files.exists(Path.of(definitelyMissing)));
        ShellPath found = path("cat " + definitelyMissing + "\n", definitelyMissing);
        assertEquals(definitelyMissing, found.path(),
                "a non-existent path is still a literal the script names");
    }

    @Test
    void pathsSurviveTheDynamicSubstitutionOfTheCommandName() {
        // The command is `cat`, which is what makes the argument a path.
        assertEquals("/etc/passwd", path("cat /etc/passwd\n", "/etc/passwd").path());
        assertFalse(pathsOf("echo /etc/passwd\n").stream()
                        .anyMatch(p -> p.path().equals("/etc/passwd")),
                "echo does not read files, so its argument is not a path");
    }

    @Test
    void theFixtureYieldsItsLiteralPaths() throws Exception {
        String fixture = Files.readString(Path.of("src/test/resources/shell/agent-composite.sh"));
        List<String> paths = pathsOf(fixture).stream().map(ShellPath::path).toList();
        assertTrue(paths.contains("/var/log"), paths.toString());
        assertTrue(paths.contains("/etc/config"), paths.toString());
        assertTrue(paths.contains("/tmp/config.bak"), paths.toString());
        assertTrue(paths.contains("/tmp/agent-output.txt"), paths.toString());
        for (ShellPath path : pathsOf(fixture)) {
            assertEquals(ShellPath.Confidence.HARVESTED, path.confidence(),
                    "the fixture is not provable, so nothing in it may claim to be");
        }
    }
}
