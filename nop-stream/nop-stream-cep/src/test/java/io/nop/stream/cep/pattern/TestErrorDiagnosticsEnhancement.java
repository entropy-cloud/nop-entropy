package io.nop.stream.cep.pattern;

import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import org.junit.jupiter.api.Test;

import static io.nop.stream.cep.NopCepErrors.ARG_PATTERN_DETAIL;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestErrorDiagnosticsEnhancement {

    @Test
    void testUntilAlreadySetContainsDiagnosticParam() {
        Pattern<String, String> pattern = Pattern.<String>begin("start")
                .oneOrMore()
                .until(SimpleCondition.of(v -> true));

        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> pattern.until(SimpleCondition.of(v -> false)));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("untilCondition already set"));
        assertTrue(detail.contains("start"));
    }

    @Test
    void testUntilOnNonLoopingContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("a").until(SimpleCondition.of(v -> true)));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("until requires LOOPING or TIMES quantifier"));
        assertTrue(detail.contains("a"));
    }

    @Test
    void testNotNextAfterOptionalContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").optional().notNext("next"));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("notNext not allowed after optional"));
    }

    @Test
    void testNotFollowedByAfterOptionalContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").optional().notFollowedBy("next"));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("notFollowedBy not allowed after optional"));
    }

    @Test
    void testCheckIfNoNotPatternContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").notNext("n").oneOrMore());

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("Not pattern"));
    }

    @Test
    void testCheckIfQuantifierAppliedContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").oneOrMore().oneOrMore());

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("Quantifier already applied"));
    }

    @Test
    void testGreedyOnGroupPatternContainsDiagnosticParam() {
        GroupPattern<String, String> group = Pattern.<String, String>begin(
                Pattern.<String>begin("a").followedBy("b"));

        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> group.greedy());

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("GroupPattern"));
    }

    @Test
    void testOptionalAfterGreedyContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").oneOrMore().greedy().followedBy("end").optional());

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("greedy"));
    }

    @Test
    void testGroupPatternWhereThrowsUnsupportedOperationException() {
        GroupPattern<String, String> group = Pattern.<String, String>begin(
                Pattern.<String>begin("a").followedBy("b"));

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> group.where(SimpleCondition.of(v -> true)));
        assertTrue(ex.getMessage().contains("where"));
    }

    @Test
    void testGroupPatternOrThrowsUnsupportedOperationException() {
        GroupPattern<String, String> group = Pattern.<String, String>begin(
                Pattern.<String>begin("a").followedBy("b"));

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> group.or(SimpleCondition.of(v -> true)));
        assertTrue(ex.getMessage().contains("or"));
    }

    @Test
    void testGroupPatternSubtypeThrowsUnsupportedOperationException() {
        GroupPattern<String, String> group = Pattern.<String, String>begin(
                Pattern.<String>begin("a").followedBy("b"));

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> group.subtype(String.class));
        assertTrue(ex.getMessage().contains("subtype"));
    }

    @Test
    void testNFACompilerDuplicateNameThrowsException() {
        io.nop.stream.cep.nfa.compiler.NFAStateNameHandler handler =
                new io.nop.stream.cep.nfa.compiler.NFAStateNameHandler();

        handler.checkNameUniqueness("testName");

        assertThrows(MalformedPatternException.class,
                () -> handler.checkNameUniqueness("testName"));
    }

    @Test
    void testCombinationsOnSingletonContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").allowCombinations());

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail, "checkPattern must forward its errorMessage to {patternDetail}");
        assertTrue(detail.contains("Combinations not applicable"));
    }

    @Test
    void testConsecutiveAfterCombinationsContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("start").oneOrMore().allowCombinations().consecutive());

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail);
        assertTrue(detail.contains("combinations"));
    }

    @Test
    void testDuplicatePatternNameContainsDiagnosticParam() {
        io.nop.stream.cep.nfa.compiler.NFACompiler.compileFactory(
                Pattern.<String>begin("a"), false);
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> io.nop.stream.cep.nfa.compiler.NFACompiler.compileFactory(
                        Pattern.<String>begin("a").followedBy("a"), false));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail, "duplicate-name check must bind the offending name");
        assertTrue(detail.contains("a"));
    }

    @Test
    void testNotFollowedByWithoutWindowContainsDiagnosticParam() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> io.nop.stream.cep.nfa.compiler.NFACompiler.compileFactory(
                        Pattern.<String>begin("a").followedBy("b").notFollowedBy("c"), false));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail, "NOT_FOLLOW-without-window check must bind pattern detail");
        assertTrue(detail.contains("NotFollowedBy"));
        assertTrue(detail.contains("c"));
    }

    @Test
    void testSkipStrategyNameMismatchContainsDiagnosticParam() {
        io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy skip =
                io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy.skipToFirst("nonexistent");
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> io.nop.stream.cep.nfa.compiler.NFACompiler.compileFactory(
                        Pattern.<String>begin("a", skip).followedBy("b"), false));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail, "skip-strategy name mismatch must bind pattern detail");
        assertTrue(detail.contains("nonexistent"));
    }

    @Test
    void testWithinZeroDurationFailsFast() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("a").followedBy("b").within(java.time.Duration.ZERO));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail, "non-positive window must fail fast with diagnostics, not silently disable timing");
        assertTrue(detail.contains("positive"));
        assertTrue(detail.contains("a"));
    }

    @Test
    void testWithinNegativeDurationFailsFast() {
        assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("a").followedBy("b").within(java.time.Duration.ofMillis(-5)));
    }

    @Test
    void testWithinNullDurationStillTolerated() {
        // null is the "no window set" contract used by CepPatternBuilder when the model has no within
        Pattern<String, String> pattern = Pattern.<String>begin("a").followedBy("b").within(null);
        assertNotNull(pattern);
    }

    @Test
    void testPatternNameWithDelimiterFailsFast() {
        MalformedPatternException ex = assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("a:b"));

        String detail = (String) ex.getParam(ARG_PATTERN_DETAIL);
        assertNotNull(detail, "pattern names containing the state-name delimiter silently corrupt match attribution");
        assertTrue(detail.contains("a:b"));
    }

    @Test
    void testAppendedPatternNameWithDelimiterFailsFast() {
        assertThrows(MalformedPatternException.class,
                () -> Pattern.<String>begin("a").followedBy("x:y"));
    }
}
