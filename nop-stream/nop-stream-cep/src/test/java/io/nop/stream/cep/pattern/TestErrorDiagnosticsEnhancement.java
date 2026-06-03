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
}
