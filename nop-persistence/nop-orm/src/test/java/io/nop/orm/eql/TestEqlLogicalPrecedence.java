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

/**
 * plan 2257（g4 审计发现）：EQL 逻辑运算符优先级回归——标准 SQL 语义 {@code NOT > AND > OR}
 * （NOT 只作用于紧随的比较谓词，不吞后续 AND 链）。
 *
 * <p>缺陷（修复前）：g4 {@code sqlExpr} 的 NOT 备选排在 AND/OR 之后（ANTLR 前=紧语义下 NOT
 * 最松），{@code not a = 1 and b = 'x'} 被解析为 {@code not (a=1 and b='x')}——实测编译文本
 * {@code not ( (a = 1) and (b = 'x') )}，与标准 SQL 的 {@code (not a=1) and b='x'} 不一致。
 * 修复：NOT 备选移至最前（最紧）。</p>
 */
public class TestEqlLogicalPrecedence extends AbstractOrmTestCase {

    @BeforeEach
    public void initLogicalData() {
        SimsClass e = daoProvider().daoFor(SimsClass.class).newEntity();
        e.setClassId("logic-1");
        e.setCollegeId("1");
        e.setClassName("logic");
        e.setMajorId("a");
        e.setStudentNumber(100);
        orm().save(e);
    }

    private long count(String where) {
        return orm().findLong(SQL.begin()
                .sql("select count(*) from SimsClass o where " + where).end(), 0L);
    }

    @Test
    public void testNotBindsTighterThanAnd() {
        // logic-1: studentNumber=100(classId='logic-1')；prepareData 的 classA: studentNumber=null。
        // 标准：(not(100=1)) and true → 选 logic-1；(not(null=1))=UNKNOWN → 不选 classA → count=1
        assertEquals(1L, count("not o.studentNumber = 1 and o.classId = 'logic-1'"),
                "not must bind tighter than and: (not a=1) and b='x', not not(a=1 and b='x')");
    }

    @Test
    public void testAndBindsTighterThanOr() {
        // classA 不满足 a；logic-1 满足 (studentNumber=100 and majorId='a')
        assertEquals(1L, count("o.classId = 'nope' or o.studentNumber = 100 and o.majorId = 'a'"),
                "and must bind tighter than or: a or (b and c)");
    }

    @Test
    public void testNotWithParenthesesUnchanged() {
        assertEquals(0L, count("not (o.studentNumber = 100 and o.majorId = 'a')"),
                "explicit parentheses keep not-scoping semantics");
    }
}
