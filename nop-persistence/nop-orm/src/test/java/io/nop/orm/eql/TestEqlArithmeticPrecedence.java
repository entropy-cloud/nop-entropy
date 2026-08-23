/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.app.SimsClass;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2256：EQL 算术优先级回归测试。
 *
 * <p>缺陷（修复前）：g4 {@code sqlExpr_bit} 备选顺序 {@code | & << >> + - * / % ^} 在 ANTLR
 * "前=紧"语义下与标准优先级相反（{@code |} 最紧、{@code /} 接近最松），导致多个 {@code /}
 * 夹 {@code +}/{@code -} 的表达式被静默重排——{@code 8/2+4/2} 编译为 {@code (8/2+4)/2}=4
 * （标准 6）、{@code 10-2+3} 编译为 {@code 10-(2+3)}=5（标准 11，因 {@code +} 错误地紧于
 * {@code -}）。修复：备选序改为标准序（前=紧：{@code ^ * / % + - << >> & |}），与
 * {@code SqlOperator} 打印优先级表（MySQL 式）对齐。
 *
 * <p>断言两层：H2 执行值（端到端语义）+ 编译 SQL 文本分组（无跨语义括号）。
 */
public class TestEqlArithmeticPrecedence extends AbstractOrmTestCase {

    @BeforeEach
    public void initArithData() {
        SimsClass entity = daoProvider().daoFor(SimsClass.class).newEntity();
        entity.setClassId("arith-1");
        entity.setCollegeId("1");
        entity.setClassName("arith");
        entity.setMajorId("a");
        entity.setStudentNumber(100);
        orm().save(entity);
    }

    private long value(String expr) {
        return orm().findLong(SQL.begin()
                .sql("select " + expr + " as v from SimsClass o where o.classId='arith-1'").end(), 0L);
    }

    private String compiledText(String expr) {
        return orm().getSessionFactory()
                .compileSql("arith", "select " + expr + " as v from SimsClass o where o.classId='arith-1'", true)
                .getSql().getText().replaceAll("\\s+", " ").trim();
    }

    @Test
    public void testDivisionAcrossPlusIsStandard() {
        assertEquals(6L, value("8 / 2 + 4 / 2"), "8/2 + 4/2 must be (8/2)+(4/2)=6, not (8/2+4)/2");
        assertEquals(3L, value("1 * 2 / 3 + 4 * 5 / 6"), "1*2/3 + 4*5/6 must be 0+3=3");
        assertEquals(6L, value("8 / 2 + 2"), "single division stays standard");
    }

    @Test
    public void testPlusMinusSameLevelLeftAssociative() {
        assertEquals(11L, value("10 - 2 + 3"), "10-2+3 must be (10-2)+3=11, not 10-(2+3)=5");
        assertEquals(75L, value("100 - 20 - 5"), "same-operator left associative");
        assertEquals(7L, value("1 + 2 * 3"), "multiply binds tighter than plus");
    }

    @Test
    public void testMulDivModSameLevelLeftAssociative() {
        assertEquals(8L, value("8 / 2 * 2"), "8/2*2 must be (8/2)*2=8, same-level left associative");
        assertEquals(3L, value("2 * 3 / 2"), "2*3/2 must be (2*3)/2=3");
        assertEquals(20L, value("100 / 5 % 30"), "100/5%30 must be (100/5)%30=20");
    }

    @Test
    public void testColumnMixedArithmetic() {
        assertEquals(50L, value("o.studentNumber * 2 / 10 + o.studentNumber * 3 / 10"),
                "100*2/10 + 100*3/10 must be 20+30=50, not ((100*2)/(10+100*3))/10");
    }

    @Test
    public void testShiftLooserThanPlus() {
        // H2 不支持 << 运算符，只断言编译分组：+（70）紧于 <<（80），2+1 需作为 << 的右操作数整体加括号
        String text = compiledText("1 << 2 + 1");
        assertTrue(text.contains("<< ( 2 + 1 )"),
                "plus binds tighter than shift: 1 << (2+1), got: " + text);
    }

    @Test
    public void testCompiledTextHasNoCrossSemanticParentheses() {
        // 打印器对同级/更紧孩子可能加保守括号（语义无害），但绝不能出现跨语义重排括号
        String text = compiledText("8 / 2 + 4 / 2");
        assertTrue(!text.contains("( 8 / 2 + 4 )"),
                "compiled text must not regroup into (8/2+4)/2: " + text);
        assertTrue(text.contains("/ 2 +") || text.contains("/ 2 ) +"),
                "plus must stay at top level between the two divisions: " + text);
    }
}
