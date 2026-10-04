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

    /**
     * A command's own first argument is not automatically a path. For the grep
     * family the first argument is the <em>pattern</em>, and for {@code echo} it is
     * simply text.
     */
    @Test
    void aFirstArgumentThatIsNotAPathIsNotOne() {
        assertFalse(pathsOf("grep secret /var/log\n").stream()
                        .anyMatch(p -> p.path().equals("secret")),
                "the grep family's first argument is the pattern, not a file");
        assertFalse(pathsOf("echo x\n").stream().anyMatch(p -> p.path().equals("x")),
                "echo does not read files");
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

    /**
     * Asserting only that no reported path starts with {@code -} passes on an
     * <b>empty</b> list, which is the hollowness this test had before it was
     * rewritten: the implementation could drop every path and still satisfy it.
     * It therefore asserts the exact path set for each case, which fails both when
     * a flag is reported and when a real path goes missing.
     */
    @Test
    void flagsAreNotPaths() {
        assertEquals(List.of("/etc/passwd"),
                pathsOf("cat -n /etc/passwd\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/var/log"),
                pathsOf("ls -la /var/log\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/var/log"),
                pathsOf("grep -r pattern /var/log\n").stream().map(ShellPath::path).toList());
    }

    /**
     * A flag must not shift the argument positions. Counting argv slots instead of
     * non-flag arguments put {@code /etc/passwd} at position 2 in
     * {@code cat -n /etc/passwd} and dropped the path entirely — losing the path
     * exactly when a flag is present, which is the common case.
     */
    @Test
    void aFlagDoesNotShiftThePathPositions() {
        assertEquals(List.of("/etc/passwd"),
                pathsOf("cat -n /etc/passwd\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/var/log/syslog"),
                pathsOf("head -n 5 /var/log/syslog\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/var/log"),
                pathsOf("grep -r pattern /var/log\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/tmp/x"),
                pathsOf("mkdir -p /tmp/x\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/data"),
                pathsOf("rm -rf /data\n").stream().map(ShellPath::path).toList());
    }

    @Test
    void bothArgumentsOfACopyAreFoundEvenWithAFlag() {
        assertEquals(List.of("/etc/a", "/tmp/b"),
                pathsOf("cp -a /etc/a /tmp/b\n").stream().map(ShellPath::path).toList());
    }

    /**
     * Commands whose non-flag operands are not all paths are deliberately absent
     * from the role table. Reporting their operands would report things that are
     * not files: a missing path is recoverable, an invented one is not.
     */
    @Test
    void commandsWithNonFileOperandsReportNothing() {
        assertTrue(pathsOf("find /var -type f\n").isEmpty(),
                "`find`'s `-type f` operand is a type, not a file");
        assertTrue(pathsOf("dd if=/etc/passwd of=/tmp/x\n").isEmpty(),
                "every `dd` operand is a key=value assignment, not a path");
        assertTrue(pathsOf("tr a-z A-Z\n").isEmpty(), "`tr` operands are character sets");
        assertTrue(pathsOf("tar czf /tmp/a.tgz /etc\n").isEmpty(),
                "tar accepts dashless flag clusters the flag rule cannot see");
        assertTrue(pathsOf("sed -i.bak s/a/b/ /etc/x\n").isEmpty(),
                "sed is not in the table; its in-place write is invisible by omission");
    }

    /**
     * An option's value is not a path. {@code chmod} is deliberately absent from
     * the positional table (its first argument is a mode), and {@code stat -c %s}
     * drops the format specifier, so neither reports one.
     */
    @Test
    void anOptionValueIsNotAPath() {
        assertEquals(List.of("/etc/shadow"),
                pathsOf("chmod 777 /etc/shadow\n").stream().map(ShellPath::path).toList(),
                "the mode is dropped, the file is kept");
        assertEquals(List.of("/var/log"),
                pathsOf("stat -c %s /var/log\n").stream().map(ShellPath::path).toList());
    }

    /** A carefully quoted path is still a path. */
    @Test
    void quotedPathsAreNotDropped() {
        assertEquals(List.of("/etc/passwd"),
                pathsOf("cat \"/etc/passwd\"\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/etc/passwd"),
                pathsOf("cat '/etc/passwd'\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/data"),
                pathsOf("rm \"/data\"\n").stream().map(ShellPath::path).toList());
    }

    /** A quoted path whose content is still expanded is not a literal. */
    @Test
    void aQuotedPathContainingAnExpansionIsStillNotALiteral() {
        assertTrue(pathsOf("cat \"$HOME/x\"\n").isEmpty());
        assertTrue(pathsOf("cat \"*.txt\"\n").isEmpty());
    }

    /** A partial argv is a subset, so its paths carry the weaker marker. */
    @Test
    void aPathFromAPartialArgvCarriesTheWeakerMarker() {
        assertEquals(ShellPath.Confidence.HARVESTED,
                path("if true; then cat /etc/passwd; fi\n", "/etc/passwd").confidence());
        assertEquals(ShellPath.Confidence.HARVESTED_PARTIAL,
                path("rm -rf $TARGET /data\n", "/data").confidence());
    }

    @Test
    void nonAsciiPathsAreFound() {
        assertEquals(List.of("/tmp/café"),
                pathsOf("rm -rf /tmp/café\n").stream().map(ShellPath::path).toList());
        assertEquals(List.of("/etc/passwd"),
                pathsOf("echo é; cat /etc/passwd\n").stream().map(ShellPath::path).toList());
    }

    // ---- dimension 5: redirects are write paths ----------------------------

    @Test
    void redirectDestinationsAreWritePaths() {
        // A redirection is a dynamic construct, so this script is not provable and
        // nothing in it may claim to be proven-sourced — including the destination.
        ShellPath destination = path("cat /etc/passwd > /tmp/out.txt\n", "/tmp/out.txt");
        assertEquals(ShellPath.Access.WRITE, destination.access());
        assertEquals(ShellPath.Confidence.HARVESTED, destination.confidence());
    }

    /**
     * An input redirect only ever reads. What a command <em>argument</em> means is
     * outside this capability — {@code tee /tmp/out} writes, {@code cat /tmp/out}
     * reads, and the capability extracts literals rather than judging intent — so
     * only the redirect's own direction is asserted here.
     */
    @Test
    void inputRedirectsAreReadPaths() {
        assertEquals(ShellPath.Access.READ, path("tee /tmp/out < /etc/in\n", "/etc/in").access());
        assertEquals(ShellPath.Access.WRITE, path("cat a >> /tmp/out\n", "/tmp/out").access());
    }

    /** {@code 2>&1} names a descriptor, not a file. */
    @Test
    void aDescriptorRedirectIsNotAPath() {
        assertTrue(pathsOf("cmd 2>&1\n").isEmpty());
    }

    @Test
    void readAndWriteAreDistinctClasses() {
        ShellPath read = path("cat /etc/passwd > /tmp/out.txt\n", "/etc/passwd");
        ShellPath write = path("cat /etc/passwd > /tmp/out.txt\n", "/tmp/out.txt");
        assertEquals(ShellPath.Access.READ, read.access());
        assertEquals(ShellPath.Access.WRITE, write.access());
    }

    /**
     * Accepted collateral, pinned so that it stays a decision: the grep family
     * requires an operand to look like a path, so a <em>bare filename</em> is
     * indistinguishable from a bare pattern and is not reported. The direction is
     * safe — a missed path, never an invented one.
     */
    @Test
    void aBareFilenameOfTheGrepFamilyIsNotReported() {
        assertTrue(pathsOf("grep foo README.md\n").isEmpty(),
                "a bare filename is indistinguishable from a bare pattern");
        assertEquals(List.of("src/"),
                pathsOf("grep foo src/\n").stream().map(ShellPath::path).toList(),
                "a path with a separator is still reported");
    }

    /**
     * A flag whose operand is itself a file must not shift the pattern out of the
     * pattern slot. This is the case a positional table cannot express: with
     * {@code grep -f <patterns>}, position 2 lands on the <em>pattern</em>.
     */
    @Test
    void aFlagWithAFileOperandDoesNotPromoteThePattern() {
        assertEquals(List.of("/etc/patterns", "/var/log"),
                pathsOf("grep -f /etc/patterns pattern /var/log\n").stream()
                        .map(ShellPath::path).toList(),
                "the pattern must never be reported as a file the script reads");
        // `-o -f` — the flag's operand is still a file, and `pat` is still not one.
        assertEquals(List.of("/etc/p", "/var/log"),
                pathsOf("grep -o -f /etc/p pat /var/log\n").stream()
                        .map(ShellPath::path).toList());
    }

    /** A quoted flag is still a flag. */
    @Test
    void aQuotedFlagIsStillAFlag() {
        assertTrue(pathsOf("rm \"-rf\"\n").isEmpty());
        assertTrue(pathsOf("rm '-rf'\n").isEmpty());
    }

    /** A quoted token holding a command separator is not one path. */
    @Test
    void aQuotedTokenWithASeparatorIsNotAPath() {
        assertTrue(pathsOf("cat '/a; rm -rf /b'\n").isEmpty());
        assertTrue(pathsOf("cat \"/etc/a rm -rf /b\"\n").isEmpty());
        assertTrue(pathsOf("rm \"/etc/x && rm -rf /\"\n").isEmpty());
        assertTrue(pathsOf("cat '  /etc/passwd  '\n").isEmpty());
        assertTrue(pathsOf("cat '--'\n").isEmpty());
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
