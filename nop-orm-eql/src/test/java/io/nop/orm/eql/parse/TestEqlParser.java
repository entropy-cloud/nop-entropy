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
import io.nop.orm.eql.ast.SqlProgram;
import org.junit.jupiter.api.Test;

public class TestEqlParser {
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
}
