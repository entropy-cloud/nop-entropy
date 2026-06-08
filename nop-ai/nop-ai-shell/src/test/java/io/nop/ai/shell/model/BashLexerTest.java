package io.nop.ai.shell.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BashLexerTest {

    private Token findToken(List<Token> tokens, TokenType type) {
        return tokens.stream()
                .filter(t -> t.type() == type)
                .findFirst()
                .orElse(null);
    }

    @Test
    void testHereStringToken() {
        BashLexer lexer = new BashLexer("<<<");
        List<Token> tokens = lexer.tokenize();
        Token token = findToken(tokens, TokenType.REDIRECT_HERE_STRING);
        assertNotNull(token);
        assertEquals("<<<", token.value());
    }

    @Test
    void testFdInputToken() {
        BashLexer lexer = new BashLexer("<&");
        List<Token> tokens = lexer.tokenize();
        Token token = findToken(tokens, TokenType.REDIRECT_FD_INPUT);
        assertNotNull(token);
        assertEquals("<&", token.value());
    }

    @Test
    void testFdOutputToken() {
        BashLexer lexer = new BashLexer(">&");
        List<Token> tokens = lexer.tokenize();
        Token token = findToken(tokens, TokenType.REDIRECT_FD_OUTPUT);
        assertNotNull(token);
        assertEquals(">&", token.value());
    }

    @Test
    void testMergeAppendToken() {
        BashLexer lexer = new BashLexer("&>>");
        List<Token> tokens = lexer.tokenize();
        Token token = findToken(tokens, TokenType.REDIRECT_MERGE_APPEND);
        assertNotNull(token);
        assertEquals("&>>", token.value());
    }

    @Test
    void testHereDocToken() {
        BashLexer lexer = new BashLexer("<<");
        List<Token> tokens = lexer.tokenize();
        Token token = findToken(tokens, TokenType.REDIRECT_HERE_DOC);
        assertNotNull(token);
        assertEquals("<<", token.value());
    }

    @Test
    void testFdInputNotConfusedWithFdOutput() {
        BashLexer lexer = new BashLexer("cmd <& 3");
        List<Token> tokens = lexer.tokenize();
        Token token = findToken(tokens, TokenType.REDIRECT_FD_INPUT);
        assertNotNull(token);
        assertEquals(TokenType.REDIRECT_FD_INPUT, token.type());

        Token fdOutputToken = findToken(tokens, TokenType.REDIRECT_FD_OUTPUT);
        assertNull(fdOutputToken);
    }

    @Test
    void testMergeNotConfusedWithMergeAppend() {
        BashLexer lexer = new BashLexer("&> out.txt");
        List<Token> tokens = lexer.tokenize();
        Token mergeToken = findToken(tokens, TokenType.REDIRECT_MERGE);
        assertNotNull(mergeToken);
        assertEquals("&>", mergeToken.value());

        Token mergeAppendToken = findToken(tokens, TokenType.REDIRECT_MERGE_APPEND);
        assertNull(mergeAppendToken);
    }
}
