/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.antlr4.common;

import io.nop.api.core.exceptions.NopException;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.InterpreterRuleContext;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNType;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 回归覆盖 wi10#4（plan 2306 项 6b）：twoPhaseParse 的 SLL 阶段 checkEnd 抛出的
 * NopException（not-end-properly）不得绕过 LL 二阶段重试——它与
 * ParseCancellationException 同样代表 SLL 预测失败，需要回落 LL 后再判失败。
 * 其他 NopException（词法错误等）不属于 SLL 预测失败，必须原样传播且不重试。
 *
 * 用最小 stub Parser 直接驱动 twoPhaseParse 合同：parseFn 第一次调用故意不消费
 * token，使真实 checkEnd 抛出 not-end-properly 的 NopException；第二次调用消费至
 * EOF 使 LL 阶段成功。修复前第一次 NopException 直接向外传播（parseFn 仅被调用
 * 一次），修复后发生重试。
 */
public class TestTwoPhaseParseRetry {

    static class StubAntlrParser extends Parser {
        StubAntlrParser(TokenStream input) {
            super(input);
            // 真实 parser 在自身构造器中初始化 _interp；stub 仅用于驱动 twoPhaseParse
            // 的错误处理路径，不会执行 ATN 模拟
            _interp = new ParserATNSimulator(this, new ATN(ATNType.PARSER, 0),
                    new DFA[0], new PredictionContextCache());
        }

        @Override
        public String[] getTokenNames() {
            return new String[0];
        }

        @Override
        public String[] getRuleNames() {
            return new String[]{"stub"};
        }

        @Override
        public String getGrammarFileName() {
            return "Stub.g4";
        }

        @Override
        public ATN getATN() {
            return _interp.atn;
        }

        /**
         * 最小化 consume：只推进 token 流，不触碰 _ctx/parse-tree 监听逻辑
         * （stub 没有 parser 上下文）
         */
        @Override
        public Token consume() {
            Token o = getCurrentToken();
            if (o.getType() != Token.EOF) {
                getInputStream().consume();
            }
            return o;
        }
    }

    static class StubParseTreeParser extends AbstractParseTreeParser {
        @Override
        protected ParseTreeResult doParse(CharStream stream) {
            throw new UnsupportedOperationException();
        }
    }

    private static TokenStream newTokenStream() {
        CommonToken word = new CommonToken(1, "x");
        word.setStartIndex(0);
        word.setStopIndex(0);
        word.setLine(1);
        // EOF token 必须带非空文本：checkEnd.skipBlank 以 isBlank(getText()) 判定是否跳过，
        // null 文本会被视为空白并在 EOF 位置无限 consume
        CommonToken eof = new CommonToken(Token.EOF, "<EOF>");
        eof.setLine(1);
        return new BufferedTokenStream(new ListTokenSource(Arrays.asList(word, eof)));
    }

    private static StubParseTreeParser newStubParseTreeParser() {
        StubParseTreeParser parser = new StubParseTreeParser();
        parser.source = "x";
        return parser;
    }

    @Test
    public void testSllNopExceptionFallsBackToLlRetry() {
        StubAntlrParser parser = new StubAntlrParser(newTokenStream());
        AtomicInteger calls = new AtomicInteger();

        ParseTreeResult result = newStubParseTreeParser().twoPhaseParse(parser, p -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                // SLL 阶段：不消费 token，checkEnd 发现未到 EOF 抛 NopException
                return new InterpreterRuleContext();
            }
            // LL 阶段：消费至 EOF，checkEnd 通过
            p.consume();
            return new InterpreterRuleContext();
        });

        assertEquals(2, calls.get(), "SLL 阶段 NopException 必须触发 LL 重试");
        assertNotNull(result);
    }

    @Test
    public void testSllSuccessDoesNotRetry() {
        StubAntlrParser parser = new StubAntlrParser(newTokenStream());
        AtomicInteger calls = new AtomicInteger();

        ParseTreeResult result = newStubParseTreeParser().twoPhaseParse(parser, p -> {
            calls.incrementAndGet();
            p.consume();
            return new InterpreterRuleContext();
        });

        assertEquals(1, calls.get(), "SLL 一次成功时不得重试");
        assertNotNull(result);
    }

    @Test
    public void testNonNotEndProperlyNopExceptionPropagatesWithoutRetry() {
        // 词法错误等非 not-end-properly 的 NopException 不属于 SLL 预测失败，
        // 不得触发 LL 重试（否则词法错误会被半途恢复掩盖为更差的错误码）
        StubAntlrParser parser = new StubAntlrParser(newTokenStream());
        AtomicInteger calls = new AtomicInteger();

        NopException ex = assertThrows(NopException.class,
                () -> newStubParseTreeParser().twoPhaseParse(parser, p -> {
                    calls.incrementAndGet();
                    throw new NopException(AntlrErrors.ERR_ANTLR_PARSE_FAIL);
                }));

        assertEquals(AntlrErrors.ERR_ANTLR_PARSE_FAIL.getErrorCode(), ex.getErrorCode());
        assertEquals(1, calls.get(), "词法类 NopException 必须直接传播，不重试");
    }
}
