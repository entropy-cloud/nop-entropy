package io.nop.treesitter.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared upstream-corpus reader for the grammar corpus runners.
 *
 * <p>A section is one {@code === title ===} … closing-{@code ===} block with a
 * code input part (ended by a {@code ---} line) and an expected s-expression
 * part. Section-header metadata lines (upstream tree-sitter CLI conventions,
 * e.g. {@code :language(tsx)}) may appear between the title and the closing
 * bar and are exposed through {@link Section#metadata()}; runners filter on
 * them (the TS shared corpus tags one section {@code :language(typescript)}
 * and one {@code :language(tsx)}).</p>
 */
public final class CorpusUtil {

    private CorpusUtil() {
    }

    /**
     * One corpus section. {@code metadata} holds the raw header lines that
     * start with {@code ':'}, in file order.
     */
    public record Section(String file, String title, List<String> metadata,
                          String input, String expected, boolean hasFields) {

        /**
         * The {@code :language(...)} tag of the section, or {@code null} when
         * the section is unlabeled (runnable under every dialect).
         */
        public String language() {
            for (String line : metadata) {
                if (line.startsWith(":language(") && line.endsWith(")")) {
                    return line.substring(":language(".length(), line.length() - 1);
                }
            }
            return null;
        }
    }

    /**
     * Reads every section of the given corpus files, in file order. Fails
     * loudly on structural drift (unterminated header, missing separator,
     * empty input) so a vendored-corpus update can never silently desync the
     * runners.
     */
    public static List<Section> read(Path corpusDir, List<String> files) throws IOException {
        List<Section> sections = new ArrayList<>();
        for (String file : files) {
            List<String> lines = Files.readAllLines(corpusDir.resolve(file), StandardCharsets.UTF_8);
            int i = 0;
            int n = lines.size();
            while (i < n) {
                if (!lines.get(i).startsWith("=")) {
                    i++;
                    continue;
                }
                if (i + 2 >= n) {
                    throw new IOException(file + ":" + (i + 1) + ": unterminated section header");
                }
                String title = lines.get(i + 1);
                List<String> metadata = new ArrayList<>();
                int cursor = i + 2;
                while (cursor < n && lines.get(cursor).startsWith(":")) {
                    metadata.add(lines.get(cursor));
                    cursor++;
                }
                if (cursor >= n || !lines.get(cursor).startsWith("=")) {
                    throw new IOException(file + ":" + (cursor + 1) + ": missing closing header bar");
                }
                i = cursor + 1;
                while (i < n && lines.get(i).isEmpty()) {
                    i++;
                }
                List<String> input = new ArrayList<>();
                while (i < n && !lines.get(i).matches("-{3,}")) {
                    input.add(lines.get(i));
                    i++;
                }
                if (i >= n) {
                    throw new IOException(file + ": section '" + title + "' has no input separator");
                }
                i++;
                while (i < n && lines.get(i).isEmpty()) {
                    i++;
                }
                List<String> expected = new ArrayList<>();
                while (i < n && !lines.get(i).startsWith("=")) {
                    expected.add(lines.get(i));
                    i++;
                }
                String normalized = JavaCorpusTest.normalizeSexpOutput(String.join("\n", expected));
                sections.add(new Section(file, title, List.copyOf(metadata),
                        String.join("\n", input), normalized, normalized.contains(": (")));
            }
        }
        return sections;
    }
}
