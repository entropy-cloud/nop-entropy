package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Target file discovery (design 03 §2.4 增注, 2026-09-22): a target path
 * that is a file is taken as-is; a directory is walked recursively and every
 * regular file is collected. A file is lintable when its extension (the
 * part after the last dot, lowercased) maps through the explicit
 * extension-to-language table ({@code java→java}, {@code ts→typescript},
 * {@code tsx→tsx} — the item 19 landing that replaced the v1
 * {@code extension == language id} convention; {@code xml→xml} and
 * {@code xbiz→xml} — the item 21 landing covering the platform's XML-family
 * models) <b>and</b> the mapped language id is registered. The table lives here, not in the
 * {@link LanguageRegistry}: extension binding is a CLI scanning concern and
 * the registry stays a pure id-to-binding resolver. Extension names outside
 * the table (including {@code .mts}/{@code .cts}, deliberately not aliased
 * in v1) and table entries whose language is unregistered are never silent:
 * they are counted per extension for the explicit skipped summary.
 *
 * <p>A target path that does not exist is a hard input error and throws;
 * the CLI maps it to exit code 2. Collected files are sorted
 * lexicographically so diagnostic output is stable across runs.</p>
 */
public final class TargetScanner {

    /**
     * The extension label used for files without any dot in their name.
     */
    public static final String NO_EXTENSION = "(none)";

    /**
     * The explicit extension-to-language-id table (item 19, design 03
     * §2.4 增注). Keys are lowercased extensions; values are binding ids in
     * registry-normalized (lowercase) form.
     */
    private static final Map<String, String> EXTENSION_TO_LANGUAGE_ID = Map.of(
            "java", "java",
            "ts", "typescript",
            "tsx", "tsx",
            "xml", "xml",
            "xbiz", "xml");

    /**
     * The inverse index: language id → its fixture/source extensions, sorted.
     * The RuleTester derives the allowed fixture suffixes from it, so the
     * two directions can never drift apart.
     */
    private static final Map<String, List<String>> LANGUAGE_ID_TO_EXTENSIONS =
            buildLanguageIndex();

    private TargetScanner() {
    }

    /**
     * The language id an extension binds, or null when the extension is
     * outside the table (the file then lands in the skipped summary).
     * {@code extension} must already be lowercased.
     */
    public static String languageIdForExtension(String extension) {
        return extension == null ? null : EXTENSION_TO_LANGUAGE_ID.get(extension);
    }

    /**
     * The source extensions a language id maps to (e.g. {@code typescript} →
     * {@code [ts]}), sorted; empty when the id is outside the table.
     */
    public static List<String> extensionsForLanguage(String languageId) {
        if (languageId == null) {
            return List.of();
        }
        String normalized = languageId.trim().toLowerCase(Locale.ROOT);
        return LANGUAGE_ID_TO_EXTENSIONS.getOrDefault(normalized, List.of());
    }

    private static Map<String, List<String>> buildLanguageIndex() {
        Map<String, List<String>> index = new HashMap<>();
        for (Map.Entry<String, String> entry : EXTENSION_TO_LANGUAGE_ID.entrySet()) {
            index.computeIfAbsent(entry.getValue(), key -> new ArrayList<>())
                    .add(entry.getKey());
        }
        Map<String, List<String>> sorted = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : index.entrySet()) {
            Collections.sort(entry.getValue());
            sorted.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(sorted);
    }

    /**
     * One discovered lintable file and the language id its extension
     * resolved to.
     */
    public record LintableFile(Path path, String languageId) {
    }

    /**
     * The outcome of one scan: the lintable files in stable order and the
     * skipped-file counts per extension (explicit, never silent).
     */
    public record ScanResult(List<LintableFile> lintable, SkippedFiles skipped) {
    }

    /**
     * Scans the target paths against the registry's registered language
     * ids.
     *
     * @throws NopLintException when a target path does not exist or is not
     *                          readable
     */
    public static ScanResult scan(List<String> targets, LanguageRegistry registry) {
        List<String> languageIds = registry.registeredIds();
        List<LintableFile> lintable = new ArrayList<>();
        SkippedFiles skipped = new SkippedFiles();

        for (String target : targets) {
            Path path = Path.of(target);
            if (!Files.exists(path)) {
                throw new NopLintException("target path does not exist: '" + target + "'");
            }
            if (Files.isDirectory(path)) {
                scanDirectory(path, languageIds, lintable, skipped);
            } else {
                classify(path, languageIds, lintable, skipped);
            }
        }

        lintable.sort(Comparator.comparing(f -> f.path().toAbsolutePath().toString()));
        return new ScanResult(List.copyOf(lintable), skipped);
    }

    private static void scanDirectory(Path dir, List<String> languageIds,
                                      List<LintableFile> lintable, SkippedFiles skipped) {
        try (Stream<Path> children = Files.walk(dir)) {
            children.filter(Files::isRegularFile)
                    .forEach(file -> classify(file, languageIds, lintable, skipped));
        } catch (IOException e) {
            throw new NopLintException("failed to walk target directory '" + dir + "': "
                    + e.getMessage(), e);
        }
    }

    private static void classify(Path file, List<String> languageIds,
                                 List<LintableFile> lintable, SkippedFiles skipped) {
        String extension = extensionOf(file);
        String languageId = languageIdForExtension(extension);
        if (languageId != null && languageIds.contains(languageId)) {
            lintable.add(new LintableFile(file, languageId));
        } else {
            skipped.record(file, extension);
        }
    }

    /**
     * The lowercased extension of {@code file}, or null when the file name
     * carries no dot (the classification then counts it as
     * {@link #NO_EXTENSION}).
     */
    static String extensionOf(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * The extension label a skipped file is counted under.
     */
    static String extensionLabel(String extension) {
        return extension == null ? NO_EXTENSION : extension;
    }
}
