package io.nop.jq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One test case from the jq official test suite (tests/jq.test format).
 *
 * <p>Format: blocks separated by blank lines; lines starting with '#' are comments.
 * A normal block is: program line, input line (empty means null input),
 * then one or more expected output lines (jq streams multiple outputs).
 * A {@code %%FAIL} block is: program line, then the expected jq error output;
 * the implementation under test must raise an error for the program.
 * {@code %%FAIL IGNORE} blocks are skipped by the official suite and skipped here too.
 */
public final class JqOfficialCase {
    public final String program;
    public final String input;
    public final List<String> expectedOutputs;
    public final String expectedError;
    public final boolean fail;
    /**
     * jq.test convention: a `# Runtime error:` comment terminates the expected
     * outputs — the program must produce those outputs and then raise an error.
     */
    public final boolean errorAfterOutputs;

    JqOfficialCase(String program, String input, List<String> expectedOutputs,
                   String expectedError, boolean fail) {
        this(program, input, expectedOutputs, expectedError, fail, false);
    }

    JqOfficialCase(String program, String input, List<String> expectedOutputs,
                   String expectedError, boolean fail, boolean errorAfterOutputs) {
        this.program = program;
        this.input = input;
        this.expectedOutputs = expectedOutputs;
        this.expectedError = expectedError;
        this.fail = fail;
        this.errorAfterOutputs = errorAfterOutputs;
    }

    public static List<JqOfficialCase> parseResource(String path) throws IOException {
        try (InputStream is = JqOfficialCase.class.getResourceAsStream(path)) {
            if (is == null)
                throw new IOException("jq test resource not found: " + path);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return parse(reader.lines().toList());
            }
        }
    }

    public static List<JqOfficialCase> parse(List<String> lines) {
        List<JqOfficialCase> cases = new ArrayList<>();
        int i = 0;
        int n = lines.size();
        while (i < n) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }
            if (isModuleDependent(line)) {
                i = nextBlockEnd(lines, i);
                continue;
            }
            if (line.startsWith("%%FAIL")) {
                boolean ignore = line.contains("IGNORE");
                i++;
                if (i >= n) break;
                String program = lines.get(i++);
                String errorLine = i < n ? lines.get(i++) : "";
                // skip indented source-excerpt lines of the expected error output
                while (i < n && !lines.get(i).isBlank() && lines.get(i).startsWith("    "))
                    i++;
                if (!ignore) {
                    cases.add(new JqOfficialCase(program.stripTrailing(), "", List.of(),
                            errorLine, true));
                }
                continue;
            }
            String program = lines.get(i++);
            String input = i < n ? lines.get(i++) : "";
            List<String> outputs = new ArrayList<>();
            boolean errorAfterOutputs = false;
            while (i < n) {
                String out = lines.get(i);
                String trimmed = out.trim();
                if (trimmed.startsWith("%%FAIL"))
                    break;
                if (trimmed.startsWith("#")) {
                    if (trimmed.startsWith("# Runtime error")) {
                        errorAfterOutputs = true;
                        i++;
                        break;
                    }
                    break;
                }
                if (out.isBlank())
                    break;
                outputs.add(out);
                i++;
            }
            cases.add(new JqOfficialCase(program.stripTrailing(), input, outputs, null,
                    false, errorAfterOutputs));
        }
        return cases;
    }

    /**
     * Programs that require jq's module system (import/include/modulemeta);
     * the official suite runs them with -L against module files on disk, which
     * is out of scope for this implementation (see design Non-Goals).
     */
    private static boolean isModuleDependent(String program) {
        return program.startsWith("import ") || program.startsWith("include ")
                || program.startsWith("modulemeta");
    }

    /** Index of the first line at/after start that terminates the block. */
    private static int nextBlockEnd(List<String> lines, int start) {
        int i = start;
        while (i < lines.size() && !lines.get(i).isBlank()) {
            i++;
        }
        return i;
    }

    @Override
    public String toString() {
        return program + "  <<  " + input;
    }
}
