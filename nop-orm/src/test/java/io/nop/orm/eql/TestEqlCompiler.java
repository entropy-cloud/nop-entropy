package io.nop.orm.eql;

import io.nop.api.core.json.JSON;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.compile.ISqlCompileContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEqlCompiler extends AbstractOrmTestCase {
    @Test
    public void testAstTransform() {
        SQL sql = SQL.begin().allowUnderscoreName().sql("select o.class_id, o.studentNumber from sims_class o").end();

        IEqlAstTransformer astTransformer = new TestTransformer();
        ICompiledSql compiled = orm().getSessionFactory().compileSql("test", sql.getText(), false, astTransformer,
                false, true, false);
        System.out.println(compiled.getSql().getFormattedText());

        List<Map<String, Object>> list = orm().findAll(sql);
        System.out.println(JSON.serialize(list, true));

        Map<String, Object> item = list.get(0);
        assertTrue(item.containsKey("class_id"));
        assertTrue(item.containsKey("studentNumber"));
    }

    static class TestTransformer implements IEqlAstTransformer {
        @Override
        public void transformBeforeAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {

        }

        @Override
        public void transformAfterAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {

        }
    }
}
