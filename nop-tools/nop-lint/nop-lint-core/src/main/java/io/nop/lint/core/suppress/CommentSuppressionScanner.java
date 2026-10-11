package io.nop.lint.core.suppress;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.node.SourceRange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans a parsed tree's comments for the inline suppression DSL (design 09
 * §2) and builds the suppression spans plus the pairing meta-diagnostics.
 *
 * <p><b>Scan vehicle (adjudicated, design 09 §2 增注)</b>: comment nodes of
 * the parsed CST ({@link LintNode#isExtra()} or a comment kind), never raw
 * line scans — a directive inside a string literal or an identifier cannot
 * suppress anything, and the vehicle stays language-agnostic through the
 * {@link LintNode} facade. Both line and block comments carry directives;
 * the six DSL forms are supported.</p>
 *
 * <p><b>Unknown-form handling (adjudicated, fail-closed)</b>: text matching
 * {@code nop-lint-} that is not followed by one of the five directive
 * keywords (with a non-alphanumeric boundary) is plain prose and ignored; a
 * recognized directive with malformed arguments — an empty rule list on
 * {@code enable}, any argument on {@code enable-all}, an empty rule segment,
 * or a token outside the rule-id alphabet — throws {@link NopLintException}
 * with the source line. At most one directive per comment: a comment
 * declaring two is rejected instead of guessing at the argument split.
 * {@code --reason "..."} (design 09 §2.1) is accepted and dropped: v1 has no
 * reason domain to carry it into.</p>
 *
 * <p><b>Scope model</b>: each directive opens byte-range spans (one per
 * targeted rule, a single all-rules span when no rule list is given).
 * {@code disable-next-line}/{@code disable-line} scope to the next/current
 * line; {@code disable} scopes from the directive until the matching
 * {@code enable} (a specific enable closes only its own rules; a bare
 * {@code disable} of all rules is lifted only by {@code enable-all});
 * anything still open at end of file suppresses to the end and raises
 * {@code unpaired-disable} (info). An {@code enable} with no matching open
 * disable raises {@code unpaired-disable} too.</p>
 */
public final class CommentSuppressionScanner {

    private static final String COMMENT_KEYWORD = "comment";

    private static final Pattern DIRECTIVE = Pattern.compile(
            "nop-lint-(disable-next-line|disable-line|enable-all|disable|enable)(?![0-9A-Za-z])");
    private static final Pattern RULE_TOKEN = Pattern.compile("[A-Za-z0-9_.$/-]+");
    private static final String ALL_RULES_KEY = "*";

    /**
     * The next-line disable directive: suppresses
     * the rules on the line after the directive's line. (The literal keyword
     * is deliberately not spelled out here — this scanner parses its own
     * source like any other file, and a spelled-out keyword followed by
     * prose would fail rule-id parsing and abort the run fail-closed.)
     */
    private enum Type {
        DISABLE_NEXT_LINE, DISABLE_LINE, DISABLE, ENABLE, ENABLE_ALL
    }

    private record Directive(Type type, Set<String> rules, SourceRange tokenRange) {
    }

    private record OpenEntry(int startByte, SourceRange directiveRange, String key) {
    }

    public SuppressionScan scan(LintTree tree) {
        Objects.requireNonNull(tree, "tree must not be null");
        LineIndex lines = new LineIndex(tree.source());
        List<Directive> directives = new ArrayList<>();
        for (LintNode comment : collectComments(tree.root())) {
            Directive directive = parseDirective(comment, lines);
            if (directive != null) {
                directives.add(directive);
            }
        }

        List<SuppressionSpan> spans = new ArrayList<>();
        List<Diagnostic> meta = new ArrayList<>();
        List<SourceRange> disableDirectiveRanges = new ArrayList<>();
        Map<String, OpenEntry> open = new LinkedHashMap<>();
        int sourceLength = tree.source().length;

        for (Directive directive : directives) {
            switch (directive.type()) {
                case DISABLE -> {
                    disableDirectiveRanges.add(directive.tokenRange());
                    for (String target : targetsOf(directive)) {
                        if (!open.containsKey(target)) {
                            open.put(target, new OpenEntry(directive.tokenRange().startByte(),
                                    directive.tokenRange(), target));
                        }
                    }
                }
                case DISABLE_LINE, DISABLE_NEXT_LINE -> {
                    disableDirectiveRanges.add(directive.tokenRange());
                    int directiveLine = lines.lineOfByte(directive.tokenRange().startByte());
                    int targetLine = directive.type() == Type.DISABLE_NEXT_LINE
                            ? directiveLine + 1
                            : directiveLine;
                    spans.add(new SuppressionSpan(
                            new SourceRange(lines.lineStartByte(targetLine), lines.lineEndByte(targetLine)),
                            Set.copyOf(directive.rules()), directive.tokenRange()));
                }
                case ENABLE -> {
                    List<String> unmatched = new ArrayList<>();
                    for (String rule : directive.rules()) {
                        OpenEntry entry = open.remove(rule);
                        if (entry == null) {
                            unmatched.add(rule);
                        } else {
                            spans.add(close(entry, directive.tokenRange().startByte()));
                        }
                    }
                    if (!unmatched.isEmpty()) {
                        meta.add(unpaired(directive.tokenRange(), "enable directive for rule(s) "
                                + unmatched + " has no matching open disable directive"));
                    }
                }
                case ENABLE_ALL -> {
                    if (open.isEmpty()) {
                        meta.add(unpaired(directive.tokenRange(),
                                "nop-lint-enable-all directive has no matching open disable directive"));
                    } else {
                        for (OpenEntry entry : open.values()) {
                            spans.add(close(entry, directive.tokenRange().startByte()));
                        }
                        open.clear();
                    }
                }
            }
        }

        for (OpenEntry entry : open.values()) {
            spans.add(new SuppressionSpan(new SourceRange(entry.startByte(), sourceLength),
                    rulesOfKey(entry.key()), entry.directiveRange()));
        }
        Map<SourceRange, List<String>> stillOpen = new LinkedHashMap<>();
        for (OpenEntry entry : open.values()) {
            stillOpen.computeIfAbsent(entry.directiveRange(), k -> new ArrayList<>()).add(entry.key());
        }
        for (Map.Entry<SourceRange, List<String>> entry : stillOpen.entrySet()) {
            meta.add(unpaired(entry.getKey(), "disable directive for " + describeKeys(entry.getValue())
                    + " is never closed by a matching enable directive"));
        }

        return new SuppressionScan(spans, meta, disableDirectiveRanges);
    }

    private SuppressionSpan close(OpenEntry entry, int endByte) {
        return new SuppressionSpan(new SourceRange(entry.startByte(), endByte),
                rulesOfKey(entry.key()), entry.directiveRange());
    }

    private static Set<String> rulesOfKey(String key) {
        return ALL_RULES_KEY.equals(key) ? Set.of() : Set.of(key);
    }

    private static List<String> targetsOf(Directive directive) {
        if (directive.rules().isEmpty()) {
            return List.of(ALL_RULES_KEY);
        }
        return List.copyOf(directive.rules());
    }

    private static String describeKeys(List<String> keys) {
        if (keys.size() == 1 && ALL_RULES_KEY.equals(keys.get(0))) {
            return "all rules";
        }
        return "rule(s) " + keys;
    }

    private static Diagnostic unpaired(SourceRange tokenRange, String message) {
        return new Diagnostic(SuppressionMeta.UNPAIRED_DISABLE, SuppressionMeta.SEVERITY_UNPAIRED,
                message, tokenRange);
    }

    /**
     * Comment nodes in source order: extras (comments in most grammars) and
     * nodes whose kind names a comment, so the scanner stays grammar-agnostic.
     */
    private static List<LintNode> collectComments(LintNode root) {
        List<LintNode> comments = new ArrayList<>();
        for (LintNode node : root) {
            if (node.isExtra() || kindNamesComment(node.kind())) {
                comments.add(node);
            }
        }
        return comments;
    }

    /**
     * Zero-allocation case-insensitive contains of "comment": the suppression
     * tail walks every node of every file, and on mixed-case kind surfaces
     * (XML facade tag names, recovery nodes) {@code toLowerCase()} allocated
     * a fresh string per node.
     */
    static boolean kindNamesComment(String kind) {
        int max = kind.length() - COMMENT_KEYWORD.length();
        for (int i = 0; i <= max; i++) {
            if (kind.regionMatches(true, i, COMMENT_KEYWORD, 0, COMMENT_KEYWORD.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the single directive of a comment, or null when the comment
     * carries none. Prose containing {@code nop-lint-} without a directive
     * keyword is not a directive; a recognized keyword with malformed
     * arguments fails closed.
     */
    private Directive parseDirective(LintNode comment, LineIndex lines) {
        String text = comment.text();
        Matcher matcher = DIRECTIVE.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            ends.add(matcher.end());
        }
        if (starts.size() > 1) {
            throw new NopLintException("Comment at line " + lineOfChar(comment, starts.get(1), lines)
                    + " declares " + starts.size() + " nop-lint directives; at most one directive "
                    + "per comment is supported (argument ownership would be ambiguous)");
        }
        if (starts.isEmpty()) {
            return null;
        }

        int relStart = starts.get(0);
        int relEnd = ends.get(0);
        Type type = switch (text.substring(relStart + "nop-lint-".length(), relEnd)) {
            case "disable-next-line" -> Type.DISABLE_NEXT_LINE;
            case "disable-line" -> Type.DISABLE_LINE;
            case "enable-all" -> Type.ENABLE_ALL;
            case "disable" -> Type.DISABLE;
            case "enable" -> Type.ENABLE;
            default -> throw new IllegalStateException("unreachable keyword: "
                    + text.substring(relStart, relEnd));
        };

        int tokenStart = byteOffsetOfChar(comment, relStart);
        int tokenEnd = byteOffsetOfChar(comment, relEnd);
        String rawArgs = rawArguments(text, relEnd);
        Set<String> rules = parseRules(rawArgs, type, tokenStart, lines);
        return new Directive(type, rules, new SourceRange(tokenStart, tokenEnd));
    }

    /**
     * The argument region: the remainder of the directive's own line within
     * the comment, with a trailing block-comment terminator (the C-family
     * star-slash sequence, or the XML arrow sequence) and the optional
     * {@code --reason "..."} suffix (design 09 §2.1, dropped in v1) removed.
     */
    private static String rawArguments(String text, int relEnd) {
        int end = text.indexOf('\n', relEnd);
        if (end < 0) {
            end = text.length();
        }
        String raw = text.substring(relEnd, end);
        int close = raw.indexOf("*/");
        if (close >= 0) {
            raw = raw.substring(0, close);
        }
        int xmlClose = raw.indexOf("-->");
        if (xmlClose >= 0) {
            raw = raw.substring(0, xmlClose);
        }
        int reason = raw.indexOf("--reason");
        if (reason >= 0) {
            raw = raw.substring(0, reason);
        }
        return raw;
    }

    private Set<String> parseRules(String raw, Type type, int tokenStart, LineIndex lines) {
        String trimmed = raw.trim();
        int line = lines.lineOfByte(tokenStart);
        if (type == Type.ENABLE_ALL) {
            if (!trimmed.isEmpty()) {
                throw new NopLintException("nop-lint-enable-all at line " + line
                        + " takes no arguments (got '" + trimmed
                        + "'); it restores every rule by definition");
            }
            return Set.of();
        }
        if (trimmed.isEmpty()) {
            if (type == Type.ENABLE) {
                throw new NopLintException("nop-lint-enable at line " + line
                        + " requires a rule list (use nop-lint-enable-all to restore every rule)");
            }
            return Set.of();
        }
        Set<String> rules = new LinkedHashSet<>();
        for (String segment : trimmed.split(",", -1)) {
            String rule = segment.trim();
            if (rule.isEmpty()) {
                throw new NopLintException("Empty rule name in directive at line " + line
                        + " (arguments were '" + trimmed + "')");
            }
            if (!RULE_TOKEN.matcher(rule).matches()) {
                throw new NopLintException("Invalid rule id '" + rule + "' in directive at line "
                        + line + " (expected tokens of [A-Za-z0-9_.$/-])");
            }
            rules.add(rule);
        }
        return rules;
    }

    /**
     * The UTF-8 byte offset of a char offset inside the comment's text:
     * multi-byte content before a directive shifts its byte position.
     */
    private static int byteOffsetOfChar(LintNode comment, int charOffset) {
        return comment.range().startByte()
                + comment.text().substring(0, charOffset).getBytes(StandardCharsets.UTF_8).length;
    }

    private static int lineOfChar(LintNode comment, int charOffset, LineIndex lines) {
        return lines.lineOfByte(byteOffsetOfChar(comment, charOffset));
    }
}
