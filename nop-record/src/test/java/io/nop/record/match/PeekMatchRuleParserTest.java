package io.nop.record.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeekMatchRuleParserTest {

    @Test
    void testLexerHexToken() {
        String input = "0x1A2B";
        PeekMatchRuleParser.Lexer lexer = new PeekMatchRuleParser.Lexer(input);
        List<PeekMatchRuleParser.Token> tokens = lexer.getTokens();

        assertEquals(2, tokens.size());
        assertEquals(PeekMatchRuleParser.TokenType.HEX, tokens.get(0).type);
        assertEquals("0x1A2B", tokens.get(0).value);
        assertArrayEquals(new byte[]{0x1A, 0x2B}, tokens.get(0).bytes);
    }

    @Test
    void testLexerStringToken() {
        String input = "'TEST_STRING'";
        PeekMatchRuleParser.Lexer lexer = new PeekMatchRuleParser.Lexer(input);
        List<PeekMatchRuleParser.Token> tokens = lexer.getTokens();

        assertEquals(2, tokens.size());
        assertEquals(PeekMatchRuleParser.TokenType.STRING, tokens.get(0).type);
        assertEquals("TEST_STRING", tokens.get(0).value);
    }

    @Test
    void testLexerNumberToken() {
        String input = "12345";
        PeekMatchRuleParser.Lexer lexer = new PeekMatchRuleParser.Lexer(input);
        List<PeekMatchRuleParser.Token> tokens = lexer.getTokens();

        assertEquals(2, tokens.size());
        assertEquals(PeekMatchRuleParser.TokenType.NUMBER, tokens.get(0).type);
        assertEquals("12345", tokens.get(0).value);
    }

    @Test
    void testLexerIdentifierToken() {
        String input = "GET and or TEST_IDENTIFIER";
        PeekMatchRuleParser.Lexer lexer = new PeekMatchRuleParser.Lexer(input);
        List<PeekMatchRuleParser.Token> tokens = lexer.getTokens();

        assertEquals(5, tokens.size());
        assertEquals(PeekMatchRuleParser.TokenType.GET, tokens.get(0).type);
        assertEquals(PeekMatchRuleParser.TokenType.AND, tokens.get(1).type);
        assertEquals(PeekMatchRuleParser.TokenType.OR, tokens.get(2).type);
        assertEquals(PeekMatchRuleParser.TokenType.IDENTIFIER, tokens.get(3).type);
        assertEquals("TEST_IDENTIFIER", tokens.get(3).value);
    }

    @Test
    void testParserSimpleCondition() {
        String input = "GET(1,2) == 'TEST' => TYPE1";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(1, matchRule.rules.size());
        PeekMatchRuleParser.Condition condition = (PeekMatchRuleParser.Condition) matchRule.rules.get(0).condition;
        assertEquals(1, condition.offset);
        assertEquals(2, condition.length);
        assertEquals("==", condition.operator);
        assertEquals("TEST", condition.value);
        assertEquals("TYPE1", matchRule.rules.get(0).resultType);
    }

    @Test
    void testParserHexCondition() {
        String input = "GET(3,4) == 0xABCD => TYPE2";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(1, matchRule.rules.size());
        PeekMatchRuleParser.Condition condition = (PeekMatchRuleParser.Condition) matchRule.rules.get(0).condition;
        assertEquals(3, condition.offset);
        assertEquals(4, condition.length);
        assertEquals("==", condition.operator);
        assertEquals("0xABCD", condition.value);
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD}, condition.bytes);
        assertEquals("TYPE2", matchRule.rules.get(0).resultType);
    }

    @Test
    void testParserAndExpression() {
        String input = "GET(1,2) == 'A' and GET(3,4) == 'B' => TYPE3";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(1, matchRule.rules.size());
        PeekMatchRuleParser.LogicalExpr expr = (PeekMatchRuleParser.LogicalExpr) matchRule.rules.get(0).condition;
        assertEquals("and", expr.operator);

        PeekMatchRuleParser.Condition left = (PeekMatchRuleParser.Condition) expr.left;
        assertEquals(1, left.offset);
        assertEquals(2, left.length);
        assertEquals("==", left.operator);
        assertEquals("A", left.value);

        PeekMatchRuleParser.Condition right = (PeekMatchRuleParser.Condition) expr.right;
        assertEquals(3, right.offset);
        assertEquals(4, right.length);
        assertEquals("==", right.operator);
        assertEquals("B", right.value);
    }

    @Test
    void testParserOrExpression() {
        String input = "GET(1,2) == 'A' or GET(3,4) == 'B' => TYPE4";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(1, matchRule.rules.size());
        PeekMatchRuleParser.LogicalExpr expr = (PeekMatchRuleParser.LogicalExpr) matchRule.rules.get(0).condition;
        assertEquals("or", expr.operator);
    }

    @Test
    void testParserNotExpression() {
        String input = "!GET(1,2) == 'A' => TYPE5";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(1, matchRule.rules.size());
        PeekMatchRuleParser.NotExpr notExpr = (PeekMatchRuleParser.NotExpr) matchRule.rules.get(0).condition;
        assertTrue(notExpr.expr instanceof PeekMatchRuleParser.Condition);
    }

    @Test
    void testParserParenthesizedExpression() {
        String input = "(GET(1,2) == 'A' or GET(3,4) == 'B') and GET(5,6) == 'C' => TYPE6";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(1, matchRule.rules.size());
        PeekMatchRuleParser.LogicalExpr andExpr = (PeekMatchRuleParser.LogicalExpr) matchRule.rules.get(0).condition;
        assertEquals("and", andExpr.operator);

        PeekMatchRuleParser.LogicalExpr orExpr = (PeekMatchRuleParser.LogicalExpr) andExpr.left;
        assertEquals("or", orExpr.operator);
    }

    @Test
    void testParserMultipleRules() {
        String input = "GET(1,2) == 'A' => TYPE1\nGET(3,4) == 'B' => TYPE2";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        PeekMatchRuleParser.MatchRule matchRule = (PeekMatchRuleParser.MatchRule) rule;

        assertEquals(2, matchRule.rules.size());
        assertEquals("TYPE1", matchRule.rules.get(0).resultType);
        assertEquals("TYPE2", matchRule.rules.get(1).resultType);
    }

    @Test
    void testMatchFunction() {
        String input = "GET(1,2) == 'A' => TYPE1\n" +
                "GET(3,4) == 0x1234 => TYPE2\n" +
                "GET(1,2) == 'A' and GET(3,4) == 0x1234 => TYPE3";

        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);

        // 测试第一个规则匹配
        TestChecker checker1 = new TestChecker("A", 0);
        assertEquals("TYPE1", rule.match(checker1));

        // 测试第二个规则匹配
        TestChecker checker2 = new TestChecker(null, 0x1234);
        assertEquals("TYPE2", rule.match(checker2));

        // 测试多个规则匹配时返回第一个匹配的规则
        TestChecker checker3 = new TestChecker("A", 0x1234);
        assertEquals("TYPE1", rule.match(checker3)); // 修改期望值为TYPE1

        // 测试无匹配
        TestChecker checker4 = new TestChecker("B", 0);
        assertNull(rule.match(checker4));
    }

    @Test
    void testNotExpressionEvaluation() {
        String input = "!GET(1,2) == 'A' => TYPE1";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);

        // Test when condition would be true without NOT
        TestChecker checker1 = new TestChecker("A", 0);
        assertNull(rule.match(checker1));

        // Test when condition would be false without NOT
        TestChecker checker2 = new TestChecker("B", 0);
        assertEquals("TYPE1", rule.match(checker2));
    }

    @Test
    void testComplexExpressionEvaluation() {
        String input = "(GET(1,2) == 'A' or GET(3,4) == 0x1234) and !GET(5,6) == 'X' => TYPE1";
        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);

        // Test case where all conditions are met
        TestChecker checker1 = new TestChecker("A", 0x1234);
        assertEquals("TYPE1", rule.match(checker1));

        // Test case where one of the OR conditions is met but NOT condition fails
        TestChecker checker2 = new TestChecker("X", 0x1234);
        assertNull(rule.match(checker2));
    }

    @Test
    void testToStringRoundTrip() {
        String input = "GET(11,8) == 'FILE_TRAILER' and GET(12,8) == 0x3f0908 => TR01\n" +
                "GET(5,4) == 'HEADER' or GET(10,2) == 0xffff => TR02\n" +
                "!(GET(1,2) == 'AB' and GET(3,4) == 'CD') or GET(5,6) == 0xEF => TR03\n" +
                "(GET(1,1) == 'A' or GET(1,1) == 'B') and !GET(2,1) == 0x58 => TR04\n" +
                "GET(0,3) == 0x0ABC => TR05";

        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);
        String regenerated = rule.toString();

        // 忽略大小写比较十六进制值
        String normalizedOriginal = input.replaceAll("0x([0-9a-fA-F]+)", "0x$1").toUpperCase();
        String normalizedRegenerated = regenerated.replaceAll("0x([0-9a-fA-F]+)", "0x$1").toUpperCase();

        assertEquals(normalizedOriginal, normalizedRegenerated);
    }

    @Test
    void testInvalidSyntaxThrowsException() {
        // Missing arrow
        assertThrows(IllegalArgumentException.class, () -> {
            PeekMatchRuleParser.parseMatchRule("GET(1,2) == 'A' TYPE1");
        });

        // Missing closing parenthesis
        assertThrows(IllegalArgumentException.class, () -> {
            PeekMatchRuleParser.parseMatchRule("GET(1,2 == 'A' => TYPE1");
        });

        // Invalid hex format
        assertThrows(IllegalArgumentException.class, () -> {
            PeekMatchRuleParser.parseMatchRule("GET(1,2) == 0xZZ => TYPE1");
        });
    }


    static class TestChecker implements IPeekMatchConditionChecker {
        private final String expectedStr;
        private final int expectedHex;

        public TestChecker() {
            this(null, 0);
        }

        public TestChecker(String expectedStr, int expectedHex) {
            this.expectedStr = expectedStr;
            this.expectedHex = expectedHex;
        }

        @Override
        public boolean matchCondition(IPeekMatchCondition condition) {
            if (expectedStr != null && expectedStr.equals(condition.getValue())) {
                return true;
            }
            if (condition.getBytes() != null) {
                int value = 0;
                for (byte b : condition.getBytes()) {
                    value = (value << 8) | (b & 0xFF);
                }
                return value == expectedHex;
            }
            return false;
        }
    }


    @Test
    public void testWildcardRule() {
        String input = "GET(0,2) == 'PK' => zip\n" +
                "* => unknown";

        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);

        // 创建一个模拟的检查器
        IPeekMatchConditionChecker checker = new IPeekMatchConditionChecker() {
            @Override
            public boolean matchCondition(IPeekMatchCondition condition) {
                // 模拟 GET(0,2) == 'PK' 返回 false
                return false;
            }
        };

        // 测试不匹配 PK 的情况，应该返回 unknown
        assertEquals("unknown", rule.match(checker));

        // 测试匹配 PK 的情况
        checker = new IPeekMatchConditionChecker() {
            @Override
            public boolean matchCondition(IPeekMatchCondition condition) {
                // 模拟 GET(0,2) == 'PK' 返回 true
                return true;
            }
        };

        assertEquals("zip", rule.match(checker));
    }

    @Test
    public void testWildcardOnlyRule() {
        String input = "* => default";

        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);

        // 创建一个模拟的检查器
        IPeekMatchConditionChecker checker = new IPeekMatchConditionChecker() {
            @Override
            public boolean matchCondition(IPeekMatchCondition condition) {
                // 这个方法不会被调用，因为规则是通配符
                return false;
            }
        };

        // 应该总是返回 default
        assertEquals("default", rule.match(checker));
    }

    @Test
    public void testMultipleRulesWithWildcard() {
        String input = "GET(0,4) == 'Rar!' => rar\n" +
                "GET(0,2) == 'PK' => zip\n" +
                "* => unknown";

        IPeekMatchRule rule = PeekMatchRuleParser.parseMatchRule(input);

        // 测试匹配 Rar! 的情况
        IPeekMatchConditionChecker rarChecker = new IPeekMatchConditionChecker() {
            @Override
            public boolean matchCondition(IPeekMatchCondition condition) {
                return condition.getOffset() == 0 && condition.getLength() == 4 &&
                        "Rar!".equals(condition.getValue());
            }
        };
        assertEquals("rar", rule.match(rarChecker));

        // 测试匹配 PK 的情况
        IPeekMatchConditionChecker zipChecker = new IPeekMatchConditionChecker() {
            @Override
            public boolean matchCondition(IPeekMatchCondition condition) {
                return condition.getOffset() == 0 && condition.getLength() == 2 &&
                        "PK".equals(condition.getValue());
            }
        };
        assertEquals("zip", rule.match(zipChecker));

        // 测试不匹配任何特定规则的情况
        IPeekMatchConditionChecker unknownChecker = new IPeekMatchConditionChecker() {
            @Override
            public boolean matchCondition(IPeekMatchCondition condition) {
                return false;
            }
        };
        assertEquals("unknown", rule.match(unknownChecker));
    }
}