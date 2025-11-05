package io.nop.orm.eql;

import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.parse.EqlExprASTParser;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

public class TestEqlAstParser {
    @Test
    public void testParse() {
        String sql = "select dept,sum (accessCount) from access_log where data_date = '2023-08-08' "
                + "and user ='alice' and publish_date ='11' group by dept limit 1";
        parse(sql);
    }

    private SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    @Test
    public void parseSqlExpr() {
        String sql = "a=1 or b > 3";
        SqlExpr expr = new EqlExprASTParser().parseFromText(null, sql);
        String result = expr.toSqlString();
        assertEquals("((a =  1 ) or (b >  3 ))", result);
    }
}
