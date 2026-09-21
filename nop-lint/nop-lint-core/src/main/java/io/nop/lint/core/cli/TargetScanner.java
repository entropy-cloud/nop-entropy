package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Target file discovery (design 03 §2.4 增注, 2026-09-22): a target path
 * that is a file is taken as-is; a directory is walked recursively and every
 * regular file is collected. A file is lintable when its extension (the
 * part after the last dot, lowercased) equals a registered language id —
 * the v1 binding convention {@code extension == language id}; nothing else
 * is configurable in v1 and richer extension tables arrive with the
 * language modules that need them (e.g. ts/tsx, roadmap item 19).
 *
 * <p>Nothing is silently dropped: files whose extension binds no language
 * (or that have no extension at all) are returned in the scan result,
 * grouped per extension for the explicit skipped summary. A target path
 * that does not exist is a hard input error and throws; the CLI maps it to
 * exit code 2. Collected files are sorted lexicographically so diagnostic
 * output is stable across runs.</p>
 */
public final class TargetScanner {

    /**
     * The extension label used for files without any dot in their name.
     */
    public static final String NO_EXTENSION = "(none)";

    private TargetScanner() {
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
        if (extension != null && languageIds.contains(extension)) {
            lintable.add(new LintableFile(file, extension));
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
