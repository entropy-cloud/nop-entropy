package io.nop.lint.core.suppress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comment-kind gate of the suppression tail (plan 08 Phase 2): the
 * zero-allocation {@code regionMatches} form must accept every case variant
 * the former {@code toLowerCase().contains("comment")} accepted — the
 * mixed-case forms are exactly where the old code paid a per-node string
 * allocation (XML facade tag names, recovery nodes).
 */
class TestCommentKindDetection {

    @Test
    void acceptsEveryCaseFormTheLowercasedFormAccepted() {
        assertTrue(CommentSuppressionScanner.kindNamesComment("comment"));
        assertTrue(CommentSuppressionScanner.kindNamesComment("Comment"));
        assertTrue(CommentSuppressionScanner.kindNamesComment("COMMENT"));
        assertTrue(CommentSuppressionScanner.kindNamesComment("line_comment"));
        assertTrue(CommentSuppressionScanner.kindNamesComment("block_comment"));
        assertTrue(CommentSuppressionScanner.kindNamesComment("Comments"));
        assertTrue(CommentSuppressionScanner.kindNamesComment("xml_comment"));
    }

    @Test
    void rejectsNonCommentKinds() {
        assertFalse(CommentSuppressionScanner.kindNamesComment("statement"));
        assertFalse(CommentSuppressionScanner.kindNamesComment("expression"));
        assertFalse(CommentSuppressionScanner.kindNamesComment("ment"));
        assertFalse(CommentSuppressionScanner.kindNamesComment("commen"));
        assertFalse(CommentSuppressionScanner.kindNamesComment(""));
    }
}
