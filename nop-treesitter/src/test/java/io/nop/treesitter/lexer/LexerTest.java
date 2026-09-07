package io.nop.treesitter.lexer;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2: the JSON token lexer — UTF-8 byte-stream scanning with per-state
 * lex mode selection from the loaded language, byte offsets tracked, and
 * fast-fail on bytes no transition can consume.
 */
class LexerTest {

    private static final Language LANGUAGE = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");

    private static List<Lexer.Token> lexAll(String source, int lexState) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<Lexer.Token> tokens = new ArrayList<>();
        int pos = 0;
        for (;;) {
            Lexer.Token token = Lexer.next(LANGUAGE, bytes, pos, lexState);
            tokens.add(token);
            if (token.symbol() == Lexer.END_SYMBOL) {
                break;
            }
            pos = token.endOffset();
        }
        return tokens;
    }

    @Test
    void lexesPunctuationWithByteOffsets() {
        List<Lexer.Token> tokens = lexAll("[{ },] ", 0);
        assertEquals(List.of(5, 1, 3, 2, 6), tokens.stream().limit(5).map(Lexer.Token::symbol).toList());
        assertEquals(List.of(0, 1, 3, 4, 5), tokens.stream().limit(5).map(Lexer.Token::startOffset).toList());
        assertEquals(List.of(1, 2, 4, 5, 6), tokens.stream().limit(5).map(Lexer.Token::endOffset).toList());
        assertEquals(0, tokens.get(5).symbol(), "trailing whitespace ends in the end token");
    }

    @Test
    void lexesNumbersIncludingExponents() {
        assertEquals(10, lexAll("345", 0).get(0).symbol());
        assertEquals(10, lexAll("10.1", 0).get(0).symbol());
        assertEquals(10, lexAll("-10", 0).get(0).symbol());
        assertEquals(10, lexAll("1e10", 0).get(0).symbol());
        assertEquals(10, lexAll("1e+10", 0).get(0).symbol());
        assertEquals(10, lexAll("1E+10", 0).get(0).symbol());
        assertEquals(10, lexAll("1e-10", 0).get(0).symbol());
        assertEquals(10, lexAll("1.5e+10", 0).get(0).symbol());
        assertEquals(10, lexAll("-1.5E+10", 0).get(0).symbol());
    }

    @Test
    void lexesNumberWithFullExtent() {
        Lexer.Token token = lexAll("1e10", 0).get(0);
        assertEquals(10, token.symbol());
        assertEquals(0, token.startOffset());
        assertEquals(4, token.endOffset());
    }

    @Test
    void lexesKeywordsTrueFalseNull() {
        assertEquals(11, lexAll("true", 0).get(0).symbol());
        assertEquals(12, lexAll("false", 0).get(0).symbol());
        assertEquals(13, lexAll("null", 0).get(0).symbol());
    }

    @Test
    void lexesLineAndBlockComments() {
        Lexer.Token line = lexAll("// we allow comments\n1", 0).get(0);
        assertEquals(14, line.symbol());
        assertEquals(0, line.startOffset());
        assertEquals(20, line.endOffset(), "line comment ends before the newline");

        Lexer.Token block = lexAll("/*\n * block\n */\n1", 0).get(0);
        assertEquals(14, block.symbol());
        assertEquals(0, block.startOffset());
        assertEquals(15, block.endOffset(), "block comment ends at the closing */");
    }

    @Test
    void lexesStringWithEscapesInStringContentMode() {
        // lex state 1 is the in-string mode: content runs, escapes, closing quote
        byte[] bytes = "\"def\\n\"".getBytes(StandardCharsets.UTF_8);
        Lexer.Token open = Lexer.next(LANGUAGE, bytes, 0, 0);
        assertEquals(7, open.symbol());
        Lexer.Token content = Lexer.next(LANGUAGE, bytes, 1, 1);
        assertEquals(8, content.symbol());
        assertEquals(1, content.startOffset());
        assertEquals(4, content.endOffset());
        Lexer.Token escape = Lexer.next(LANGUAGE, bytes, 4, 1);
        assertEquals(9, escape.symbol());
        assertEquals(4, escape.startOffset());
        assertEquals(6, escape.endOffset());
        Lexer.Token close = Lexer.next(LANGUAGE, bytes, 6, 1);
        assertEquals(7, close.symbol());
        assertEquals(6, close.startOffset());
        assertEquals(7, close.endOffset());
    }

    @Test
    void lexStateIsSelectedFromTheLoadedLanguage() {
        assertEquals(1, LANGUAGE.lexState(17), "in-string parse states use lex state 1");
        assertEquals(0, LANGUAGE.lexState(1), "ordinary parse states use lex state 0");
        assertEquals(0, LANGUAGE.keywordLexState(17), "JSON has no keyword lexer");
    }

    @Test
    void nearKeywordDoesNotCollapseIntoKeywordToken() {
        byte[] bytes = "truex".getBytes(StandardCharsets.UTF_8);
        Lexer.Token first = Lexer.next(LANGUAGE, bytes, 0, 0);
        assertEquals(11, first.symbol());
        assertEquals(0, first.startOffset());
        assertEquals(4, first.endOffset(), "only the exact word lexes as the keyword");
        TreeSitterException ex = assertThrows(TreeSitterException.class,
                () -> Lexer.next(LANGUAGE, bytes, first.endOffset(), 0));
        assertTrue(ex.getMessage().contains("lex error"), ex.getMessage());
    }

    @Test
    void nearKeywordSuffixRaisesInsteadOfBeingSwallowed() {
        byte[] bytes = "truex".getBytes(StandardCharsets.UTF_8);
        Lexer.Token first = Lexer.next(LANGUAGE, bytes, 0, 0);
        assertEquals(11, first.symbol());
        TreeSitterException ex = assertThrows(TreeSitterException.class,
                () -> Lexer.next(LANGUAGE, bytes, first.endOffset(), 0));
        assertTrue(ex.getMessage().contains("lex error"), ex.getMessage());
    }

    @Test
    void malformedBytesRaiseLexError() {
        assertThrows(TreeSitterException.class, () -> lexAll("[abc]", 0));
        assertThrows(TreeSitterException.class, () -> lexAll("1a", 0));
        assertThrows(TreeSitterException.class, () -> lexAll("tru", 0));
        assertThrows(TreeSitterException.class, () -> lexAll("\"unterminated", 1));
    }

    @Test
    void unknownLexStateRaisesTypedError() {
        TreeSitterException ex = assertThrows(TreeSitterException.class,
                () -> Lexer.next(LANGUAGE, "x".getBytes(StandardCharsets.UTF_8), 0, 2));
        assertTrue(ex.getMessage().contains("lex state 2"), ex.getMessage());
    }

    @Test
    void endTokenAtEof() {
        Lexer.Token end = lexAll("", 0).get(0);
        assertEquals(0, end.symbol());
        assertEquals(0, end.startOffset());
        assertEquals(0, end.endOffset());
    }
}