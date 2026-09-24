package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code nop-lint match} subcommand (roadmap item 39, design 03 §2.4,
 * plan Decision 2): compiles one source pattern and prints its matches in
 * one file — the rule author's debugging face. Language resolution goes
 * through the file extension's registry binding unless {@code --language}
 * overrides it; an unknown language, an unresolvable extension, or a
 * pattern that fails to compile aborts with the internal-error face (exit
 * 2). Matches print as {@code <file>:<line>:<col>: <first line of the
 * matched text>} (1-based line and column, the byte range converted
 * through the file's {@link LineIndex}).
 *
 * <p>The argument face is independent of the check parser and equally
 * fail-closed: a missing pattern, a missing file, or a second positional
 * argument is a usage error.</p>
 */
public final class MatchCommand {

    private MatchCommand() {
    }

    /**
     * Runs the match over {@code args} (everything after the subcommand
     * token) and returns the exit code — always 0 when the run completes;
     * every failure surfaces as the caller's exit-2 face.
     */
    public static int run(String[] args, LanguageRegistry registry, PrintStream out,
                          PrintStream err) throws IOException {
        String patternText = null;
        Path file = null;
        String languageOverride = null;
        List<String> positional = new ArrayList<>(2);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--language".equals(arg)) {
                if (++i >= args.length || args[i].isBlank())
                    throw new NopLintException("usage: nop-lint match <pattern> <file>"
                            + " [--language <id>] (--language requires a value)");
                languageOverride = args[i];
            } else if (arg.startsWith("-")) {
                throw new NopLintException("usage: nop-lint match <pattern> <file>"
                        + " [--language <id>] (unknown option '" + arg + "')");
            } else if (!arg.isBlank()) {
                positional.add(arg);
            }
        }
        if (positional.size() != 2) {
            throw new NopLintException("usage: nop-lint match <pattern> <file> [--language <id>]"
                    + " (pattern and file are required)");
        }
        patternText = positional.get(0);
        file = Path.of(positional.get(1));

        String languageId = languageOverride;
        if (languageId == null) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? TargetScanner.NO_EXTENSION : name.substring(dot + 1);
            languageId = TargetScanner.languageIdForExtension(extension);
        }
        LintLanguage language = registry.resolve(languageId);

        byte[] bytes = Files.readAllBytes(file);
        String source = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        SourcePattern pattern = SourcePatternCompiler.compile(patternText, language);
        LintTree tree = language.parse(source);
        List<Match> matches = pattern.matchIn(tree.root());

        LineIndex lines = new LineIndex(source);
        for (Match match : matches) {
            int offset = match.node().range().startByte();
            int line = lines.lineOfByte(offset);
            int column = offset - lines.lineStartByte(line) + 1;
            String text = match.node().text();
            int newline = text.indexOf('\n');
            String firstLine = newline < 0 ? text : text.substring(0, newline);
            out.println(file + ":" + line + ":" + column + ": " + firstLine);
        }
        out.println(matches.size() + " match(es)");
        return NopLintCli.EXIT_OK;
    }
}
