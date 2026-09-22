package io.nop.lint.core.constraint;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.semantic.TypeQuerySupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Compiles the parsed constraint models of one rule (design 01 §3.2/§3.3,
 * roadmap item 22) into executable {@link Constraint} values, enforcing the
 * compile-time capture contract fail-closed: every capture a constraint
 * references must be declared by the rule's matcher as a single-node capture
 * ({@code $VAR}/{@code $$VAR}); a reference to a sequence capture
 * ({@code $$$VAR}) or to an undeclared name is rejected here — with the rule
 * id, the constraint kind, and the capture name — instead of evaluating
 * against an empty environment at run time.
 *
 * <p>Semantics (design 01 §3.2 Decisions, plan 2026-09-22-1045-3): the text
 * comparisons use the capture node's source text verbatim (no trimming);
 * {@code differentText} holds exactly when not all referenced texts are
 * equal (the complement of {@code sameText}); {@code regex} is a full
 * match; {@code inList} is exact membership; {@code notExists} holds when
 * its inner pattern has no match in the match node's subtree (match node
 * included); {@code withinDepth} holds when the match node's subtree depth
 * (longest edge count to a descendant, the node itself = 0) is at most
 * {@code max}; {@code typeOf} resolves through the run's L2 query support
 * (roadmap item 20) and is a guarded fail in runs without it — the {@code
 * requires: "L2"} gate (parser) and the engine's profile gate keep typeOf
 * rules out of L2-less runs, and this branch fails loudly if that invariant
 * is ever broken.</p>
 */
public final class Constraints {

    private Constraints() {
    }

    /**
     * Compiles one parsed constraint for {@code ruleId}.
     *
     * @param model           the parsed constraint (kind + that kind's fields)
     * @param ruleId          the owning rule's id (diagnostics)
     * @param language        the rule's language binding; the notExists inner
     *                        pattern compiles against it
     * @param singleCaptures  the rule matcher's declared single-node capture
     *                        names ({@link io.nop.lint.core.pattern.SourcePattern#captureNames()})
     * @param multiCaptures   the rule matcher's declared sequence capture names
     * @return the executable constraint
     * @throws NopLintException when a capture reference is undeclared, a
     *                          sequence capture, or (notExists) the inner
     *                          pattern does not compile — every rejection
     *                          names the rule id, the constraint kind, and
     *                          the offending capture or pattern
     */
    /**
     * Compiles one parsed constraint for {@code ruleId} in an L2-less run
     * (null query support; typeOf stays guarded-fail there). The engine
     * wires real support through the six-arg overload.
     */
    public static Constraint compile(RuleDslModel.Constraint model, String ruleId, LintLanguage language,
                                     Set<String> singleCaptures, Set<String> multiCaptures) {
        return compile(model, ruleId, language, singleCaptures, multiCaptures, null);
    }

    public static Constraint compile(RuleDslModel.Constraint model, String ruleId, LintLanguage language,
                                     Set<String> singleCaptures, Set<String> multiCaptures,
                                     TypeQuerySupport typeQueries) {
        if (model == null)
            throw new NopLintException("Rule '" + ruleId + "' has a null constraint model");
        String kind = model.getKind();
        switch (kind) {
            case "sameText":
                return new SameText(checkCaptures(model, ruleId, model.getCaptures(),
                        singleCaptures, multiCaptures));
            case "differentText":
                return new DifferentText(checkCaptures(model, ruleId, model.getCaptures(),
                        singleCaptures, multiCaptures));
            case "regex":
                return new Regex(singleCapture(model, ruleId, singleCaptures, multiCaptures),
                        Pattern.compile(model.getPattern()));
            case "inList":
                return new InList(singleCapture(model, ruleId, singleCaptures, multiCaptures),
                        Set.copyOf(model.getValues()));
            case "typeOf":
                return new TypeOf(singleCapture(model, ruleId, singleCaptures, multiCaptures),
                        model.getIs(), typeQueries);
            case "notExists":
                return new NotExists(compileInnerPattern(model, ruleId, language));
            case "withinDepth":
                return new WithinDepth(model.getMax());
            default:
                throw new NopLintException("Rule '" + ruleId + "' declares unknown constraint kind '"
                        + kind + "' (the parser is the validation authority; fail-closed)");
        }
    }

    private static List<String> checkCaptures(RuleDslModel.Constraint model, String ruleId,
                                              List<String> captures, Set<String> singleCaptures,
                                              Set<String> multiCaptures) {
        for (String name : captures) {
            checkDeclared(model, ruleId, name, singleCaptures, multiCaptures);
        }
        return captures;
    }

    private static String singleCapture(RuleDslModel.Constraint model, String ruleId,
                                        Set<String> singleCaptures, Set<String> multiCaptures) {
        String name = model.getCapture();
        checkDeclared(model, ruleId, name, singleCaptures, multiCaptures);
        return name;
    }

    private static void checkDeclared(RuleDslModel.Constraint model, String ruleId, String name,
                                      Set<String> singleCaptures, Set<String> multiCaptures) {
        if (singleCaptures.contains(name))
            return;
        if (multiCaptures.contains(name))
            throw new NopLintException("Rule '" + ruleId + "' constraint '" + model.getKind()
                    + "' references '" + name + "', which is a sequence capture ($$$): constraints "
                    + "need single-node captures");
        throw new NopLintException("Rule '" + ruleId + "' constraint '" + model.getKind()
                + "' references capture '" + name + "', which no matcher of the rule declares "
                + "(add the meta-var to a pattern, or fix the reference; fail-closed)");
    }

    private static SourcePattern compileInnerPattern(RuleDslModel.Constraint model, String ruleId,
                                                     LintLanguage language) {
        try {
            return SourcePatternCompiler.compile(model.getPattern(), language);
        } catch (RuntimeException e) {
            // NopLintException (parser rejections) and backend parse failures
            // (e.g. TreeSitterException on pathological text) both surface as
            // the same fail-closed rejection naming the rule and the site.
            throw new NopLintException("Rule '" + ruleId + "' constraint 'notExists' has an invalid "
                    + "inner pattern: " + e.getMessage(), e);
        }
    }

    static String requireText(ConstraintContext ctx, String kind, String capture) {
        String text = ctx.captureText(capture);
        if (text == null)
            throw new NopLintException("constraint '" + kind + "' cannot resolve capture '" + capture
                    + "' at evaluation time (the compile-time capture check guarantees declared "
                    + "single captures are bound; invariant broken)");
        return text;
    }

    static final class SameText implements Constraint {
        private final List<String> captures;

        SameText(List<String> captures) {
            this.captures = captures;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            String first = requireText(ctx, "sameText", captures.get(0));
            for (int i = 1; i < captures.size(); i++) {
                if (!first.equals(requireText(ctx, "sameText", captures.get(i))))
                    return false;
            }
            return true;
        }
    }

    static final class DifferentText implements Constraint {
        private final List<String> captures;

        DifferentText(List<String> captures) {
            this.captures = captures;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            String first = requireText(ctx, "differentText", captures.get(0));
            for (int i = 1; i < captures.size(); i++) {
                if (!first.equals(requireText(ctx, "differentText", captures.get(i))))
                    return true;
            }
            return false;
        }
    }

    static final class Regex implements Constraint {
        private final String capture;
        private final Pattern pattern;

        Regex(String capture, Pattern pattern) {
            this.capture = capture;
            this.pattern = pattern;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            return pattern.matcher(requireText(ctx, "regex", capture)).matches();
        }
    }

    static final class InList implements Constraint {
        private final String capture;
        private final Set<String> values;

        InList(String capture, Set<String> values) {
            this.capture = capture;
            this.values = values;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            return values.contains(requireText(ctx, "inList", capture));
        }
    }

    /**
     * The L2-backed type constraint (design 01 §3.2 Decision, roadmap item
     * 20 Phase 2): {@code is} holds when the capture node's type is
     * assignable to the declared type, resolved through the run's {@link
     * TypeQuerySupport} (wire contract: design 06 §5.3). A query failure
     * surfaces as {@link TypeResolutionException} — the engine degrades the
     * rule instead of guessing.
     *
     * <p>With null support (an L2-less run) this is a guarded fail: the
     * {@code requires: "L2"} declaration (parser gate) and the engine's
     * profile gate keep typeOf rules out of such runs, so reaching the
     * evaluation means the gate invariant is broken and must fail loudly,
     * never silently return a guess (roadmap hard constraint: L2 is never
     * faked with L1 results).</p>
     */
    static final class TypeOf implements Constraint {
        private final String capture;
        private final String is;
        private final TypeQuerySupport typeQueries;

        TypeOf(String capture, String is, TypeQuerySupport typeQueries) {
            this.capture = capture;
            this.is = is;
            this.typeQueries = typeQueries;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            if (typeQueries == null)
                throw new NopLintException("constraint 'typeOf' (capture '" + capture + "', is '" + is
                        + "') evaluated in a run without L2 query support; the rule must have been "
                        + "gated out (profile skip or degrade) instead of being evaluated");
            LintNode node = ctx.env().getCapture(capture);
            if (node == null)
                throw new NopLintException("constraint 'typeOf' cannot resolve capture '" + capture
                        + "' at evaluation time (the compile-time capture check guarantees declared "
                        + "single captures are bound; invariant broken)");
            return typeQueries.isAssignableTo(node, is);
        }
    }

    static final class NotExists implements Constraint {
        private final SourcePattern pattern;

        NotExists(SourcePattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            return pattern.matchIn(ctx.matchNode()).isEmpty();
        }
    }

    static final class WithinDepth implements Constraint {
        private final int max;

        WithinDepth(int max) {
            this.max = max;
        }

        @Override
        public boolean holds(ConstraintContext ctx) {
            return subtreeDepth(ctx.matchNode()) <= max;
        }

        /**
         * The longest edge count from {@code root} down to any descendant
         * ({@code root} alone = 0), computed iteratively so deep trees
         * cannot overflow the call stack.
         */
        private static int subtreeDepth(LintNode root) {
            int maxDepth = 0;
            Deque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(root, 0));
            while (!stack.isEmpty()) {
                Frame frame = stack.pop();
                if (frame.depth() > maxDepth)
                    maxDepth = frame.depth();
                for (LintNode child : frame.node().children()) {
                    stack.push(new Frame(child, frame.depth() + 1));
                }
            }
            return maxDepth;
        }
    }

    private record Frame(LintNode node, int depth) {
    }
}
