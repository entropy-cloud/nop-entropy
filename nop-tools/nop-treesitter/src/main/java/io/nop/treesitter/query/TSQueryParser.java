package io.nop.treesitter.query;

import io.nop.treesitter.TreeSitterException;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the tree-sitter query S-expression syntax:
 * {@code (node_type [field:] (child_pattern)* @capture?)} node patterns,
 * wildcard {@code (_)}, anonymous string literals ({@code ":"}), alternation
 * groups ({@code [ p1 p2 ... ]}, nested allowed), capture suffixes
 * ({@code @name}), and predicate groups ({@code (#eq? @cap "text")},
 * {@code (#match? @cap "regex")}). Whitespace and {@code ;}-comments are
 * skipped everywhere.
 *
 * <p>The parser is deliberately strict: every malformed construct (unclosed
 * paren / bracket, empty pattern or alternation, stray token, capture name
 * missing after {@code @}, unterminated string) and every syntax construct the
 * runtime does not support (anchors {@code .}, quantifiers {@code + * ?},
 * unknown predicates) raises a {@link TreeSitterException} naming the construct
 * and the byte offset — there is no ignore-and-continue path.</p>
 */
public final class TSQueryParser {

    private final String src;
    private int pos;

    private TSQueryParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    /**
     * Parses a query source into its AST. Throws {@link TreeSitterException}
     * with an offset for every malformed or unsupported construct.
     */
    public static Query parse(String source) {
        return new TSQueryParser(source).parseAll();
    }

    private Query parseAll() {
        List<Pattern> patterns = new ArrayList<>();
        skipWs();
        while (pos < src.length()) {
            patterns.add(parsePattern());
            skipWs();
        }
        return new Query(patterns);
    }

    private Pattern parsePattern() {
        PatternNode root = parseNode().withCapture(parseCaptureSuffix());
        List<Predicate> predicates = new ArrayList<>();
        skipWs();
        while (pos < src.length() && src.charAt(pos) == '(' && isPredicateStart()) {
            predicates.add(parsePredicate());
            skipWs();
        }
        return new Pattern(root, predicates);
    }

    private boolean isPredicateStart() {
        return pos + 1 < src.length() && src.charAt(pos + 1) == '#';
    }

    private PatternNode parseNode() {
        skipWs();
        if (pos >= src.length()) {
            throw error("unexpected end of query while expecting a node pattern");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '(' -> parseParenNode();
            case '[' -> parseAlternation();
            case '_' -> {
                pos++;
                yield new PatternNode.Wildcard(null, List.of());
            }
            case '"' -> new PatternNode.Anonymous(parseStringLiteral(), null, List.of());
            case ')' -> throw error("unexpected ')' with no matching '('");
            case ']' -> throw error("unexpected ']' with no matching '['");
            case '.' -> throw error("anchor '.' is not supported by this runtime (patterns are rooted)");
            case '+', '*', '?' -> throw error("quantifier '" + c + "' is not supported by this runtime");
            default -> throw error("unexpected character '" + c + "' while expecting a node pattern");
        };
    }

    private PatternNode parseParenNode() {
        expect('(');
        skipWs();
        if (pos >= src.length()) {
            throw error("unclosed '(' at end of query");
        }
        if (src.charAt(pos) == ')') {
            throw error("empty pattern '()'");
        }
        String anonymousText = null;
        boolean wildcard = false;
        String typeName = null;
        if (src.charAt(pos) == '"') {
            anonymousText = parseStringLiteral();
        } else if (src.charAt(pos) == '_') {
            wildcard = true;
            pos++;
        } else if (isIdentStart(src.charAt(pos))) {
            typeName = scanIdentifier();
        } else {
            throw error("expected a node type, wildcard '_' or string literal after '('");
        }
        skipWs();

        List<ChildPattern> children = new ArrayList<>();
        while (pos < src.length() && src.charAt(pos) != ')') {
            children.add(parseChild());
            skipWs();
        }
        if (pos >= src.length()) {
            throw error("unclosed '(' in pattern '" + describeHead(anonymousText, wildcard, typeName) + "'");
        }
        pos++;

        if (wildcard) {
            return new PatternNode.Wildcard(null, children);
        }
        if (anonymousText != null) {
            return new PatternNode.Anonymous(anonymousText, null, children);
        }
        return new PatternNode.Type(typeName, null, children);
    }

    private String describeHead(String anonymousText, boolean wildcard, String typeName) {
        if (wildcard) {
            return "_";
        }
        return anonymousText != null ? "\"" + anonymousText + "\"" : typeName;
    }

    private ChildPattern parseChild() {
        String field = null;
        if (src.charAt(pos) != '_' && src.charAt(pos) != '"' && isIdentStart(src.charAt(pos))) {
            String ident = scanIdentifier();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ':') {
                pos++;
                field = ident;
                skipWs();
            } else {
                throw error("unexpected identifier '" + ident + "' (expected a child pattern or ')')");
            }
        }
        PatternNode child = parseNode().withCapture(parseCaptureSuffix());
        return new ChildPattern(field, child);
    }

    private PatternNode parseAlternation() {
        expect('[');
        skipWs();
        if (pos >= src.length()) {
            throw error("unclosed '[' at end of query");
        }
        if (src.charAt(pos) == ']') {
            throw error("empty alternation '[]'");
        }
        List<PatternNode> elements = new ArrayList<>();
        while (pos < src.length() && src.charAt(pos) != ']') {
            elements.add(parseNode().withCapture(parseCaptureSuffix()));
            skipWs();
        }
        if (pos >= src.length()) {
            throw error("unclosed '[' in alternation");
        }
        pos++;
        return new PatternNode.Alternation(elements, null);
    }

    private String parseCaptureSuffix() {
        skipWs();
        if (pos < src.length() && src.charAt(pos) == '@') {
            pos++;
            if (pos >= src.length() || !isIdentStart(src.charAt(pos))) {
                throw error("capture name expected after '@'");
            }
            String capture = scanIdentifier();
            rejectQuantifier();
            return capture;
        }
        rejectQuantifier();
        return null;
    }

    private void rejectQuantifier() {
        if (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '+' || c == '*' || c == '?') {
                throw error("quantifier '" + c + "' is not supported by this runtime");
            }
            if (c == '.') {
                throw error("anchor '.' is not supported by this runtime");
            }
        }
    }

    private Predicate parsePredicate() {
        expect('(');
        skipWs();
        if (pos >= src.length() || src.charAt(pos) != '#') {
            throw error("predicate must start with '#'");
        }
        pos++;
        String name = scanPredicateName();
        skipWs();
        String capture = parseCaptureSuffix();
        if (capture == null) {
            throw error("predicate '#" + name + "' requires a capture reference as its first argument");
        }
        skipWs();
        String value;
        if (pos < src.length() && src.charAt(pos) == '"') {
            value = parseStringLiteral();
        } else if (pos < src.length() && isIdentStart(src.charAt(pos))) {
            value = scanIdentifier();
        } else {
            throw error("predicate '#" + name + "' requires a string or symbol value after the capture reference");
        }
        skipWs();
        if (pos >= src.length() || src.charAt(pos) != ')') {
            throw error("unclosed predicate '#" + name + "'");
        }
        pos++;
        return switch (name) {
            case "eq?" -> new Predicate.Eq(capture, value);
            case "match?" -> new Predicate.Match(capture, value);
            default -> throw error("unsupported predicate '#" + name + "' (supported: #eq?, #match?)");
        };
    }

    private String parseStringLiteral() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                return sb.toString();
            }
            if (c == '\\') {
                pos++;
                if (pos >= src.length()) {
                    throw error("unclosed string literal at end of query");
                }
                char escaped = src.charAt(pos);
                sb.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> escaped;
                });
                pos++;
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw error("unclosed string literal");
    }

    private String scanIdentifier() {
        int start = pos;
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            throw error("identifier expected at offset " + pos);
        }
        return src.substring(start, pos);
    }

    private String scanPredicateName() {
        int start = pos;
        while (pos < src.length() && (isIdentPart(src.charAt(pos)) || src.charAt(pos) == '?')) {
            pos++;
        }
        if (pos == start) {
            throw error("predicate name expected after '#'");
        }
        return src.substring(start, pos);
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == ';') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || c == '.';
    }

    private TreeSitterException error(String reason) {
        return new TreeSitterException("query parse error at offset " + pos + ": " + reason);
    }
}