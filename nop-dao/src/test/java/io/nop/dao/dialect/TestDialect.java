/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.StringSqlExpr;
import io.nop.dao.dialect.function.NativeSQLFunction;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.core.resource.ResourceHelper;
import io.nop.dataset.IDataRow;
import io.nop.dataset.rowmapper.SingleBinderRowMapper;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.ISqlFunctionModel;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.dao.jdbc.JdbcTestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDialect extends JdbcTestCase {

    public TestDialect() {
        maxPoolSize = 1;
    }

    @DisplayName("检查关键词转义")
    @Test
    public void testKeywords() {
        IDialect dialect = DialectManager.instance().getDialectForDataSource(getDataSource());
        safeDropTable("test_tbl");
        String text = ResourceHelper.readText(testResource("/check_keywords.txt"));
        text = StringHelper.replace(text, "\n", ",");
        Set<String> data = ConvertHelper.toCsvSet(text, NopException::new);

        Set<String> set = Collections.newSetFromMap(new CaseInsensitiveMap<>());
        set.addAll(data);

        List<List<String>> chunks = CollectionHelper.splitChunk(set, 100);

        for (List<String> chunk : chunks) {
            String sql = "create table test_tbl(" + StringHelper.join(chunk.stream()
                    .map(keyword -> dialect.escapeSQLName(keyword) + " "
                            + dialect.stdToNativeSqlType(StdSqlType.VARCHAR, 100, 0))
                    .collect(Collectors.toList()), ",") + ")";
            jdbc().executeUpdate(new SQL(sql));
            String select = "select " + StringHelper.join(chunk.stream()
                    .map(keyword -> "o." + dialect.escapeSQLName(keyword) + " as " + dialect.escapeSQLName(keyword))
                    .collect(Collectors.toList()), ",") + " from test_tbl o";
            jdbc().findAll(new SQL(select));
            jdbc().executeUpdate(new SQL(dialect.getDropTableSql("test_tbl", false)));
        }
    }

    @DisplayName("检查数据类型定义")
    @Test
    public void testDataTypes() {
        IDialect dialect = DialectManager.instance().getDialectForDataSource(getDataSource());
        safeDropTable("test_dt");

        for (SqlDataTypeModel dataTypeModel : dialect.getDialectModel().getSqlDataTypes()) {
            if(dataTypeModel.isDeprecated())
                continue;

            String dataType = getDataTypeMaxPrecision(dataTypeModel);
            String sql = "create table test_dt ( f1 " + dataType + ")";

            jdbc().executeUpdate(new SQL(sql));
            jdbc().executeUpdate(new SQL(dialect.getDropTableSql("test_dt", false)));

            if (dataTypeModel.getPrecision() != null && !Boolean.FALSE.equals(dataTypeModel.getAllowPrecision())) {
                // 如果长度超出限制则应该报错
                sql = "create table test_dt( f1 " + getDataTypeError(dataTypeModel) + ")";
                try {
                    jdbc().executeUpdate(new SQL(sql));
                    if(!dataTypeModel.isAllowExceedPrecision())
                        assertTrue(false, sql);
                } catch (NopException e) {
                    e.printStackTrace();
                }

                safeDropTable("test_dt");
            }
        }
    }

    private String getDataTypeMaxPrecision(SqlDataTypeModel dataTypeModel) {
        String dataType = dataTypeModel.getName();
        if (dataTypeModel.getCode() != null)
            dataType = dataTypeModel.getCode();
        if (dataTypeModel.getPrecision() != null && !Boolean.FALSE.equals(dataTypeModel.getAllowPrecision())) {
            if (dataTypeModel.getScale() != null) {
                dataType = dataType + "(" + dataTypeModel.getPrecision() + "," + dataTypeModel.getScale() + ")";
            } else {
                dataType = dataType + "(" + dataTypeModel.getPrecision() + ")";
            }
        }
        return dataType;
    }

    private String getDataTypeError(SqlDataTypeModel dataTypeModel) {
        String dataType = dataTypeModel.getName();
        if (dataTypeModel.getPrecision() != null) {
            if (dataTypeModel.getScale() != null) {
                dataType = dataType + "(" + (dataTypeModel.getPrecision() + 1) + "," + dataTypeModel.getScale() + ")";
            } else {
                dataType = dataType + "(" + (dataTypeModel.getPrecision() + 1) + ")";
            }
        }
        return dataType;
    }

    @DisplayName("检查创建sequence、字面量转义等特殊SQL")
    @Test
    public void testSqls() {
        IDialect dialect = DialectManager.instance().getDialectForDataSource(getDataSource());
        if (dialect.isSupportSequence()) {
            String createSeq = dialect.getCreateSequenceSql("test_sequence", 1, 10);
            jdbc().executeUpdate(new SQL(createSeq));

            String seqNextVal = dialect.getSequenceNextValSql("test_sequence");
            assertEquals(Long.valueOf(1), jdbc().findLong(new SQL(seqNextVal), 0L));
            assertEquals(Long.valueOf(11), jdbc().findLong(new SQL(seqNextVal), 0L));

            // sqlserver 的drop sequence返回-1
            jdbc().executeUpdate(new SQL(dialect.getDropSequenceSql("test_sequence")));
        }

        LocalDate date = LocalDate.of(2002, 1, 2);
        LocalDateTime dateTime = LocalDateTime.of(2002, 1, 2, 1, 1);
        Timestamp timestamp = new Timestamp(DateHelper.dateTimeToMillis(LocalDateTime.of(2002, 1, 2, 1, 3, 4)));

        checkDual(date, StdSqlType.DATE, dialect.getDateLiteral(date), dialect);
        checkDual(dateTime, StdSqlType.DATETIME, dialect.getDateTimeLiteral(dateTime), dialect);
        checkDual(timestamp, StdSqlType.TIMESTAMP, dialect.getTimestampLiteral(timestamp), dialect);

        assertNotNull(jdbc().getDbCurrentTimestamp(null));

        String str = "\"':-+/|=*&^%$#@!~?><,.|{}()~!";
        checkDual(str, StdSqlType.VARCHAR, dialect.getStringLiteral(str), dialect);

        str = "\\\\A\\\"`";
        checkDual(str, StdSqlType.VARCHAR, dialect.getStringLiteral(str), dialect);
    }

    private void checkDual(Object v, StdSqlType sqlType, String sql, IDialect dialect) {
        Object ret = selectFromDual(sqlType, sql, dialect);
        assertEquals(v, ret);
    }

    private Object selectFromDual(StdSqlType sqlType, String sql, IDialect dialect) {
        IDataParameterBinder binder = DataParameterBinders.getDefaultBinder(sqlType.getName());
        return jdbc().findFirst(new SQL(dialect.getSelectFromDualSql(sql)), new SingleBinderRowMapper(binder));
    }

    @DisplayName("检查Dialect中的函数定义")
    @Test
    public void testFunctions() {
        IDialect dialect = DialectManager.instance().getDialectForDataSource(getDataSource());
        DialectModel dialectModel = dialect.getDialectModel();
        for (ISqlFunctionModel fnModel : dialectModel.getFunctions()) {
            if(fnModel.getTestSql() == null){
                if(fnModel instanceof SqlNativeFunctionModel && ((SqlNativeFunctionModel) fnModel).isOnlyForWindowExpr())
                    continue;
            }

            ISQLFunction fn = dialect.getFunction(fnModel.getName());
            Pair<String, StdSqlType> pair = buildFuncTestSql(fn, dialect);
            StdSqlType resultType = pair.getSecond();
            if (resultType == null)
                resultType = StdSqlType.ANY;

            String sql = pair.getFirst();
            // 如果指定了testSql，则直接测试testSql
            if (fnModel.getTestSql() != null) {
                sql = StringHelper.strip(fnModel.getTestSql());
            }

            sql = dialect.getSelectFromDualSql(sql);

            IDataParameterBinder resultBinder = DataParameterBinders.getDefaultBinder(resultType.getName());
            if (resultBinder == null)
                resultBinder = DataParameterBinders.ANY;
            Object value = jdbc().findFirst(SQL.begin().sql(sql).end(), new SingleBinderRowMapper(resultBinder));
            System.out.println("sqlType=" + resultType + ",value=" + value);
            if (value != null)
                assertTrue(resultType.getStdDataType().getJavaClass().isInstance(value));
        }
    }

    Pair<String, StdSqlType> buildFuncTestSql(ISQLFunction fn, IDialect dialect) {
        List<ISqlExpr> argExprs = new ArrayList<>();
        for (StdSqlType sqlType : fn.getArgTypes()) {
            argExprs.add(StringSqlExpr.makeExpr(getSqlTestData(sqlType, dialect)));
        }
        SqlExprList expr = fn.buildFunctionExpr(null, argExprs, dialect);
        StdSqlType returnType = fn.getReturnType(argExprs, dialect);
        return Pair.of(expr.getSqlString(), returnType);
    }

    String getSqlTestData(StdSqlType sqlType, IDialect dialect) {
        switch (sqlType) {
            case NUMERIC:
            case DOUBLE:
            case FLOAT:
            case INTEGER:
            case SMALLINT:
            case BIGINT:
                return "1";
            case VARCHAR:
                return "'a'";
            case DATE:
                return dialect.getDateLiteral(LocalDate.of(2002, 1, 2));
            case DATETIME:
                return dialect.getDateTimeLiteral(LocalDateTime.of(2002, 1, 2, 14, 1));
            case TIMESTAMP:
                return dialect.getTimestampLiteral(
                        new Timestamp(DateHelper.dateTimeToMillis(LocalDateTime.of(2002, 1, 2, 4, 14, 2, 4))));
            case ANY:
                return "2";
            case BOOLEAN:
                return dialect.getBooleanValueLiteral(true);
            case VARBINARY:
                return "'0'"; // dialect.getHexValueLiteral(ByteString.of("abc".getBytes()));
            default:
                return "3";
        }
    }

    @DisplayName("测试Clob")
    @Test
    public void testClob() {
        safeDropTable("test_tbl");
        IDialect dialect = getDialect();
        int size = 1024 * 1024 * 2;
        SQLDataType dataType = dialect.stdToNativeSqlType(StdSqlType.VARCHAR, size, 0);
        String sql = "create table test_tbl(c " + dataType + ")";
        jdbc().executeUpdate(new SQL(sql));

        jdbc().executeUpdate(SQL.begin().sql("insert into test_tbl(c)values(?)", "ss").end());

        String text = buildLargeText(size);
        jdbc().executeUpdate(SQL.begin().sql("insert into test_tbl(c)values(?)", text).end());

        jdbc().findAll(new SQL("select c from test_tbl"), new SingleBinderRowMapper(DataParameterBinders.STRING));
        safeDropTable("test_tbl");
    }

    @DisplayName("测试Blob")
    @Test
    public void testBlob() {
        safeDropTable("test_tbl");
        IDialect dialect = getDialect();
        int size = 1024 * 1024 * 2;
        SQLDataType dataType = dialect.stdToNativeSqlType(StdSqlType.VARBINARY, size, 0);
        String sql = "create table test_tbl(c " + dataType + ")";
        jdbc().executeUpdate(new SQL(sql));

        jdbc().executeUpdate(SQL.begin().sql("insert into test_tbl(c)values(?)", ByteString.of("ss".getBytes())).end());

        ByteString data = buildLargeBytes(size);
        jdbc().executeUpdate(SQL.begin().sql("insert into test_tbl(c)values(?)", data).end());

        jdbc().findAll(new SQL("select c from test_tbl"), new SingleBinderRowMapper(DataParameterBinders.BYTE_STRING));
        safeDropTable("test_tbl");
    }

    String buildLargeText(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append('A');
        }
        return sb.toString();
    }

    ByteString buildLargeBytes(int size) {
        ByteArrayOutputStream os = new ByteArrayOutputStream(size);
        for (int i = 0; i < size; i++) {
            os.write('A');
        }
        return ByteString.of(os.toByteArray());
    }

    @DisplayName("测试分页")
    @Test
    public void testPagination() {
        safeDropTable("test_tbl");
        jdbc().executeUpdate(new SQL("create table test_tbl(a varchar(10))"));
        for (int i = 0; i < 100; i++) {
            jdbc().executeUpdate(
                    SQL.begin().sql("insert into test_tbl(a)values(?)", StringHelper.leftPad(i + "", 2, '0')).end());
        }

        SQL sql = new SQL("select a from test_tbl order by a");
        List<String> list = jdbc().findPage(sql, 0, 10);
        assertEquals("00", list.get(0));
        assertEquals("09", list.get(9));

        list = jdbc().findPage(sql, 10, 10);
        assertEquals("10", list.get(0));
        assertEquals("19", list.get(9));
    }

    @DisplayName("没有设定长度的VARCHAR对应于CLOB")
    @Test
    public void testClobType() {
        IDialect dialect = DialectManager.instance().getDialect("oracle");
        SQLDataType type = dialect.stdToNativeSqlType(StdSqlType.VARCHAR, 0, 0);
        assertEquals("CLOB", type.getName());
    }

    @Test
    public void testDate() {
        jdbc().executeUpdate(new SQL("create table test(d DATE)"));
        for (int d = 3000; d >= 1800; d--) {
            LocalDate date = LocalDate.of(d, 2, 3);
            jdbc().executeUpdate(SQL.begin().sql("insert into test(d)values(?)", date).end());
            jdbc().executeQuery(new SQL("select * from test"), ds -> {
                IDataRow row = ds.next();
                assertEquals(date, row.getLocalDate(0));
                return null;
            });
            jdbc().executeUpdate(new SQL("delete from test"));
        }
    }
}
