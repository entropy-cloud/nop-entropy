package io.nop.treesitter.query;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * A compiled query: an ordered list of patterns, each a matcher tree over the
 * language's symbol / field ids plus its predicate list, and an interned
 * capture-name table shared by all patterns.
 *
 * <p>Compilation validates every name against the {@link Language}: unknown
 * node types, unknown anonymous tokens, unknown field names, regex failures in
 * {@code #match?}, and predicate capture references that never appear in the
 * pattern all raise a typed {@link TreeSitterException} naming the offending
 * symbol — no silent acceptance. Capture names are interned exactly like the
 * upstream {@code symbol_table_insert_name} in {@code query.c}: the same name
 * maps to the same capture id everywhere in the query.</p>
 */
public final class TSQuery {

    private final Language language;
    private final List<Pattern> patterns;
    private final List<String> captureNames;

    private TSQuery(Language language, List<Pattern> patterns, List<String> captureNames) {
        this.language = language;
        this.patterns = List.copyOf(patterns);
        this.captureNames = List.copyOf(captureNames);
    }

    /**
     * Parses and compiles a query source against the given language.
     */
    public static TSQuery compile(Language language, String source) {
        return compile(language, TSQueryParser.parse(source));
    }

    /**
     * Compiles a parsed query AST against the given language.
     */
    public static TSQuery compile(Language language, Query ast) {
        TSQueryCompiler compiler = new TSQueryCompiler(language);
        return compiler.compile(ast);
    }

    public Language language() {
        return language;
    }

    public int patternCount() {
        return patterns.size();
    }

    public Pattern pattern(int index) {
        return patterns.get(index);
    }

    /**
     * The interned capture-name table in first-appearance order.
     */
    public List<String> captureNames() {
        return captureNames;
    }

    public String captureName(int captureId) {
        return captureNames.get(captureId);
    }

    public List<Pattern> patterns() {
        return patterns;
    }

    /**
     * A compiled pattern: the pattern's index in the query, its root matcher
     * (the matcher tree carries the pattern's children recursively) and its
     * predicate list.
     */
    public record Pattern(int patternIndex, Matcher root, List<Predicate> predicates) {
        public Pattern {
            predicates = List.copyOf(predicates);
        }
    }

    /**
     * A compiled node matcher: either a named type, the wildcard, an anonymous
     * token, or an alternation of matchers. Type / wildcard / anonymous
     * matchers carry their own child matchers.
     */
    public sealed interface Matcher permits TypeMatcher, WildcardMatcher, AnonymousMatcher, AlternationMatcher {
    }

    /**
     * Matches a named node of the given symbol id.
     */
    public record TypeMatcher(int symbolId, int captureId, List<ChildMatcher> children) implements Matcher {
    }

    /**
     * Matches any named node ({@code (_)}); its children match the node's children.
     */
    public record WildcardMatcher(int captureId, List<ChildMatcher> children) implements Matcher {
    }

    /**
     * Matches an anonymous token with the given symbol id (e.g. {@code ":"}).
     */
    public record AnonymousMatcher(int symbolId, int captureId, List<ChildMatcher> children) implements Matcher {
    }

    /**
     * Matches a node when any of its element matchers matches it
     * ({@code [ (null) (true) (false) ]}); captures apply to the matched node.
     */
    public record AlternationMatcher(List<Matcher> elements, int captureId) implements Matcher {
        public AlternationMatcher {
            elements = List.copyOf(elements);
        }
    }

    /**
     * A child matcher: an optional field-id qualifier (0 = positional) plus the
     * matcher applied to the child node.
     */
    public record ChildMatcher(int fieldId, Matcher matcher) {
    }

    /**
     * A compiled predicate: the interned capture id it inspects, the compared
     * text ({@code #eq?}) or the regular expression source ({@code #match?}),
     * and — for {@code #match?} — the pre-compiled full-match pattern.
     */
    public record Predicate(int captureId, String text, boolean regex, java.util.regex.Pattern compiled) {
    }

    /**
     * Compiles a parsed AST into the matcher IR, validating every name against
     * the language.
     */
    private static final class TSQueryCompiler {

        private final Language language;
        private final List<String> captureNames = new ArrayList<>();
        private final Map<String, Integer> captureIds = new HashMap<>();
        private final List<Pattern> patterns = new ArrayList<>();

        TSQueryCompiler(Language language) {
            this.language = language;
        }

        TSQuery compile(Query ast) {
            int index = 0;
            for (io.nop.treesitter.query.Pattern astPattern : ast.patterns()) {
                List<String> patternCaptures = new ArrayList<>();
                collectCaptures(astPattern.root(), patternCaptures);
                Matcher root = compileMatcher(astPattern.root());
                List<Predicate> predicates = compilePredicates(astPattern.predicates(), patternCaptures);
                patterns.add(new Pattern(index++, root, predicates));
            }
            return new TSQuery(language, patterns, captureNames);
        }

        private void collectCaptures(PatternNode node, List<String> out) {
            if (node.capture() != null) {
                out.add(node.capture());
            }
            if (node instanceof PatternNode.Alternation alt) {
                for (PatternNode element : alt.elements()) {
                    collectCaptures(element, out);
                }
            } else {
                for (ChildPattern child : node.children()) {
                    collectCaptures(child.node(), out);
                }
            }
        }

        private Matcher compileMatcher(PatternNode node) {
            int captureId = captureId(node.capture());
            if (node instanceof PatternNode.Alternation alt) {
                List<Matcher> elements = new ArrayList<>();
                for (PatternNode element : alt.elements()) {
                    Matcher compiled = compileMatcher(element);
                    if (compiled instanceof AlternationMatcher nested && nested.captureId() < 0) {
                        elements.addAll(nested.elements());
                    } else {
                        elements.add(compiled);
                    }
                }
                return new AlternationMatcher(elements, captureId);
            }
            if (node instanceof PatternNode.Wildcard wildcard) {
                return new WildcardMatcher(captureId, compileChildren(wildcard.children()));
            }
            if (node instanceof PatternNode.Anonymous anonymous) {
                int symbol = language.symbolId(anonymous.text(), false);
                if (symbol < 0) {
                    throw new TreeSitterException("query compile error: anonymous node '" + anonymous.text()
                            + "' is not a token in this grammar");
                }
                return new AnonymousMatcher(symbol, captureId, compileChildren(anonymous.children()));
            }
            PatternNode.Type type = (PatternNode.Type) node;
            int symbol = language.symbolId(type.typeName(), true);
            if (symbol < 0) {
                throw new TreeSitterException("query compile error: unknown node type '" + type.typeName()
                        + "' in this grammar");
            }
            return new TypeMatcher(symbol, captureId, compileChildren(type.children()));
        }

        private List<ChildMatcher> compileChildren(List<ChildPattern> children) {
            List<ChildMatcher> result = new ArrayList<>();
            for (ChildPattern child : children) {
                int fieldId = 0;
                if (child.fieldName() != null) {
                    fieldId = language.fieldId(child.fieldName());
                    if (fieldId == 0) {
                        throw new TreeSitterException("query compile error: unknown field '" + child.fieldName()
                                + "' in this grammar");
                    }
                }
                result.add(new ChildMatcher(fieldId, compileMatcher(child.node())));
            }
            return List.copyOf(result);
        }

        private int captureId(String name) {
            if (name == null) {
                return -1;
            }
            Integer existing = captureIds.get(name);
            if (existing != null) {
                return existing;
            }
            int id = captureNames.size();
            captureNames.add(name);
            captureIds.put(name, id);
            return id;
        }

        private List<Predicate> compilePredicates(List<io.nop.treesitter.query.Predicate> astPredicates,
                                                  List<String> patternCaptures) {
            List<Predicate> result = new ArrayList<>();
            for (io.nop.treesitter.query.Predicate astPredicate : astPredicates) {
                Integer captureId = captureIds.get(astPredicate.captureName());
                if (captureId == null || !patternCaptures.contains(astPredicate.captureName())) {
                    throw new TreeSitterException("query compile error: predicate references unknown capture '@"
                            + astPredicate.captureName() + "'");
                }
                if (astPredicate instanceof io.nop.treesitter.query.Predicate.Match match) {
                    java.util.regex.Pattern compiled;
                    try {
                        compiled = java.util.regex.Pattern.compile("\\A(?:" + match.regex() + ")\\z");
                    } catch (PatternSyntaxException e) {
                        throw new TreeSitterException("query compile error: invalid regex '" + match.regex()
                                + "' in #match?: " + e.getMessage(), e);
                    }
                    result.add(new Predicate(captureId, match.regex(), true, compiled));
                } else {
                    io.nop.treesitter.query.Predicate.Eq eq =
                            (io.nop.treesitter.query.Predicate.Eq) astPredicate;
                    result.add(new Predicate(captureId, eq.text(), false, null));
                }
            }
            return List.copyOf(result);
        }
    }
}