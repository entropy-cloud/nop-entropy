/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.json.JSON;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

public class TestEqlParser extends BaseTestCase {
    @Test
    public void testParse() {
        JSON.registerProvider(JsonTool.instance());
        String sql = "select o.dept.name from test.AuthUser o where o.roleId in (select r from Role r)";
        SqlProgram program = parse(sql);
        System.out.println(JSON.serialize(program, true));
    }

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    @Test
    public void testJoin() {
        String sql = "select o from MyEntity o left join OtherTable b on a.id = b.id";
        SqlProgram program = parse(sql);
        program.toSQL().dump();
    }

    @Test
    public void testUnionAll() {
        String sql = "(select t.a from t) union all (select v.a from v) union all (select c from m where m.id=3)";
        SqlProgram program = parse(sql);
        program.toSQL().dump();
    }

    @Test
    public void testNullLiteral() {
        String sql = "select null as a from t";
        System.out.println(parse(sql).toSQL().getText());
    }

    @Test
    public void testSubQuery(){
        String sql = "  select (\n" +
                "    select \n" +
                "      sum(am.amount)\n" +
                "      from attachment_manages am\n" +
                "      where (am.contract_id = ct.contract_id) and (am.delete_flag =  0 )\n" +
                "  ) as  amount from t";
        parse(sql).toSQL().dump();
    }

    @Test
    public void testComplexSql() {
        String sql1 = attachmentText("bb002.sql");
        System.out.println(parse(sql1).toSQL().getText());

        String sql2 = attachmentText("formatted.sql");
        System.out.println(parse(sql2).toSQL());
    }
}
