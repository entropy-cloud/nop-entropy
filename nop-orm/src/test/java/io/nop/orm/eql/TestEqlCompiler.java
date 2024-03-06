package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.compile.ISqlCompileContext;
import org.junit.jupiter.api.Test;

public class TestEqlCompiler extends AbstractOrmTestCase {
    @Test
    public void testAstTransform() {
        SQL sql = new SQL("select o from SimsClass o");

        IEqlAstTransformer astTransformer = new TestTransformer();
        ICompiledSql compiled = orm().getSessionFactory().compileSql("test", sql.getText(), false, astTransformer, false);
        System.out.println(compiled.getSql().getFormattedText());
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
