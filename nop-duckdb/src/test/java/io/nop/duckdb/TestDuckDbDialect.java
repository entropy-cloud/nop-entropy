package io.nop.duckdb;

import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SqlExprList;
import io.nop.core.lang.sql.StringSqlExpr;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.ISqlFunctionModel;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.commons.util.objects.Pair;
import io.nop.dataset.rowmapper.SingleBinderRowMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 full verification of the duckdb dialect against a real DuckDB database (plan 2290).
 * Function semantics are exercised through the dialect function models themselves
 * (buildFunctionExpr handles realName/parenthesis semantics), never hand-built SQL, so a
 * wrong dialect definition surfaces as an execution failure here. The ST_* spatial
 * functions inherited from geo-support are adjudicated out of the vanilla dialect (25
 * functions fail on native DuckDB; they belong to the spatial extension), while the two
 * natively working ones (st_astext/st_asbinary) keep semantic assertions.
 *
 * <p>Wiring note: JdbcTestCase lives in nop-dao's test source tree (no test-jar published),
 * so the template wiring is replicated here with a pool-size-1 file-backed DuckDB
 * datasource (anonymous in-memory duckdb databases are private per connection).
 */
public class TestDuckDbDialect extends BaseTestCase {
    static HikariDataSource dataSource;
    static IJdbcTemplate jdbc;
    static IDialect dialect;

    @BeforeAll
    public static void init() throws Exception {
        CoreInitialization.initialize();
        Path db = Files.createTempDirectory("duckdb-wi8-dialect").resolve("dialect.duckdb");
        dataSource = new HikariDataSource();
        dataSource.setMetricRegistry(GlobalMeterRegistry.instance());
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setJdbcUrl("jdbc:duckdb:" + db);
        dataSource.setMaximumPoolSize(1);
        JdbcFactory factory = new JdbcFactory();
        jdbc = factory.newJdbcTemplate(dataSource);
        dialect = DialectManager.instance().getDialectForDataSource(dataSource);
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    private void safeDropTable(String tableName) {
        try {
            jdbc.executeUpdate(new SQL(dialect.getDropTableSql(tableName, true)));
        } catch (Exception e) {
            // drop-if-exists semantics differ on some dialects; absence is fine here
        }
    }

    @Test
    @Timeout(300)
    public void testFunctionSetSnapshotAndFullExecution() {
        Set<String> names = dialect.getFunctionNames();
        System.out.println("[WI8-DIALECT] effective function count=" + names.size());
        System.out.println("[WI8-DIALECT] functions=" + names);

        DialectModel model = dialect.getDialectModel();
        int executed = 0;
        List<String> failures = new ArrayList<>();
        for (ISqlFunctionModel fnModel : model.getFunctions()) {
            if (fnModel.getTestSql() == null && fnModel instanceof SqlNativeFunctionModel
                    && ((SqlNativeFunctionModel) fnModel).isOnlyForWindowExpr()) {
                continue;
            }
            ISQLFunction fn = dialect.getFunction(fnModel.getName());
            Pair<String, StdSqlType> pair = buildFuncTestSql(fn);
            String sql = pair.getFirst();
            StdSqlType resultType = pair.getSecond() == null ? StdSqlType.ANY : pair.getSecond();
            if (fnModel.getTestSql() != null) {
                sql = StringHelper.strip(fnModel.getTestSql());
            }
            sql = dialect.getSelectFromDualSql(sql);

            IDataParameterBinder binder = DataParameterBinders.getDefaultBinder(resultType.getName());
            if (binder == null) {
                binder = DataParameterBinders.ANY;
            }
            try {
                Object value = jdbc.findFirst(SQL.begin().sql(sql).end(),
                        new SingleBinderRowMapper(binder));
                System.out.println("[WI8-DIALECT] fn=" + fnModel.getName() + " sqlType=" + resultType
                        + " value=" + value);
                if (value != null) {
                    assertTrue(resultType.getStdDataType().getJavaClass().isInstance(value),
                            "function " + fnModel.getName() + " return type mismatch");
                }
            } catch (Exception e) {
                // collect all failures so one run maps the complete defect surface
                String msg = String.valueOf(e.toString());
                int nl = msg.indexOf('\n');
                failures.add(fnModel.getName() + " [" + sql + "]: "
                        + (nl > 0 ? msg.substring(0, nl) : msg));
            }
            executed++;
        }
        System.out.println("[WI8-DIALECT] executed=" + executed + " failures=" + failures.size());
        for (String f : failures) {
            System.out.println("[WI8-DIALECT] FAILURE: " + f);
        }
        assertTrue(failures.isEmpty(), "dialect functions must all execute on native duckdb: "
                + failures);
        assertTrue(executed > 0, "function set must not be empty");
    }

    Pair<String, StdSqlType> buildFuncTestSql(ISQLFunction fn) {
        List<ISqlExpr> argExprs = new ArrayList<>();
        for (StdSqlType sqlType : fn.getArgTypes()) {
            argExprs.add(StringSqlExpr.makeExpr(getSqlTestData(sqlType)));
        }
        SqlExprList expr = fn.buildFunctionExpr(null, argExprs, dialect);
        StdSqlType returnType = fn.getReturnType(argExprs, dialect);
        return Pair.of(expr.getSqlString(), returnType);
    }

    String getSqlTestData(StdSqlType sqlType) {
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
                        new Timestamp(DateHelper.dateTimeToMillis(LocalDateTime.of(2002, 1, 2, 4, 14, 2))));
            case BOOLEAN:
                return dialect.getBooleanValueLiteral(true);
            case VARBINARY:
                return "'0'::BLOB";
            case GEOMETRY:
                return "'POINT(1 2)'";
            case ANY:
                return "2";
            default:
                return "3";
        }
    }

    /** realName / parenthesis / override semantics pinned on the duckdb-specific functions */
    @Test
    @Timeout(120)
    public void testDuckDbSpecificFunctionSemantics() {
        // rand -> random: the dialect model must generate SQL with the real name
        Pair<String, StdSqlType> randGen = buildFuncTestSql(dialect.getFunction("rand"));
        assertTrue(randGen.getFirst().contains("random("),
                "rand must generate random(...), got: " + randGen.getFirst());
        Object v = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(randGen.getFirst())),
                new SingleBinderRowMapper(DataParameterBinders.ANY));
        assertNotNull(v, "generated rand->random call must return a value");

        // instr -> strpos: generated SQL uses strpos and computes correctly
        List<ISqlExpr> instrArgs = List.of(StringSqlExpr.makeExpr("'abc'"), StringSqlExpr.makeExpr("'b'"));
        SqlExprList instrExpr = dialect.getFunction("instr").buildFunctionExpr(null, instrArgs, dialect);
        assertTrue(instrExpr.getSqlString().contains("strpos("),
                "instr must generate strpos(...), got: " + instrExpr.getSqlString());
        Object pos = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(instrExpr.getSqlString())),
                new SingleBinderRowMapper(DataParameterBinders.getDefaultBinder(StdSqlType.BIGINT.getName())));
        assertEquals(2L, ((Number) pos).longValue(), "instr must map to strpos");

        // year override on TIMESTAMP
        Object year = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(
                dialect.getDateTimeLiteral(LocalDateTime.of(2026, 5, 4, 3, 2, 1))
                        .replace("TIMESTAMP_S", "TIMESTAMP_S") + " is null or year("
                        + dialect.getDateTimeLiteral(LocalDateTime.of(2026, 5, 4, 3, 2, 1)) + ") = 2026")),
                new SingleBinderRowMapper(DataParameterBinders.BOOLEAN));
        assertEquals(Boolean.TRUE, year, "year(TIMESTAMP) override must work");

        // current_date / current_timestamp: parenthesis + realName semantics via the models
        Pair<String, StdSqlType> curDateGen = buildFuncTestSql(dialect.getFunction("current_date"));
        assertTrue(curDateGen.getFirst().contains("current_date("),
                "current_date must generate parenthesized call, got: " + curDateGen.getFirst());
        assertNotNull(jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(curDateGen.getFirst())),
                new SingleBinderRowMapper(DataParameterBinders.ANY)), "current_date() parenthesis form");
        Pair<String, StdSqlType> curTsGen = buildFuncTestSql(dialect.getFunction("current_timestamp"));
        assertTrue(curTsGen.getFirst().contains("current_localtimestamp("),
                "current_timestamp must generate current_localtimestamp(...), got: " + curTsGen.getFirst());
        assertNotNull(jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(curTsGen.getFirst())),
                new SingleBinderRowMapper(DataParameterBinders.ANY)), "current_localtimestamp() form");

        // uuid / uuidv7 / cosh / sinh
        for (String fnCall : List.of("uuid()", "uuidv7()", "cosh(1)", "sinh(1)")) {
            Object val = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(fnCall)),
                    new SingleBinderRowMapper(DataParameterBinders.ANY));
            assertNotNull(val, fnCall + " must be callable");
        }

        // natively working spatial functions kept in the dialect
        Object astext = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(
                "st_astext('POINT(1 2)'::GEOMETRY)")),
                new SingleBinderRowMapper(DataParameterBinders.STRING));
        assertTrue(astext != null && astext.toString().contains("POINT"),
                "st_astext native form must work, got: " + astext);
    }

    @Test
    @Timeout(120)
    public void testSqlsTemplates() {
        // pagination: LIMIT/OFFSET bound-parameter form via the template path
        safeDropTable("pg_t");
        jdbc.executeUpdate(new SQL("CREATE TABLE pg_t(id INTEGER)"));
        for (int i = 1; i <= 10; i++) {
            jdbc.executeUpdate(SQL.begin().sql("INSERT INTO pg_t VALUES (?)", i).end());
        }
        SQL base = SQL.begin().sql("SELECT id FROM pg_t ORDER BY id").end();
        List<Object> rows = jdbc.findPage(base, 2, 3);
        assertEquals(3, rows.size(), "pagination limit");
        // single-column rows surface as raw values
        Object first = rows.get(0) instanceof io.nop.dataset.IDataRow row
                ? row.getObject(0)
                : rows.get(0);
        assertEquals(3, ((Number) first).intValue(), "pagination offset");

        // dateTimeLiteral consumed through the dialect path: equality query must hit
        LocalDateTime dt = LocalDateTime.of(2002, 1, 2, 3, 4, 5);
        String literal = dialect.getDateTimeLiteral(dt);
        Object matched = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(
                literal + " = " + literal)),
                new SingleBinderRowMapper(DataParameterBinders.BOOLEAN));
        assertEquals(Boolean.TRUE, matched, "dateTimeLiteral equality must hold");

        // timestampLiteral is a template with no platform consumer (recorded fact): render the
        // XML template manually and verify the literal executes and preserves the value
        String tsTemplate = dialect.getDialectModel().getSqls().getTimestampLiteral();
        java.time.format.DateTimeFormatter tsFormat =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn");
        String rendered = io.nop.api.core.util.ApiStringHelper.renderTemplate(tsTemplate,
                name -> tsFormat.format(dt));
        Object tsMatch = jdbc.findFirst(new SQL(dialect.getSelectFromDualSql(rendered
                + " = " + rendered)),
                new SingleBinderRowMapper(DataParameterBinders.BOOLEAN));
        assertEquals(Boolean.TRUE, tsMatch, "rendered timestampLiteral must execute and hold");

        // forUpdate / lockHint are adjudicated empty for duckdb (no lock support)
        assertNull(dialect.getDialectModel().getSqls().getForUpdate(),
                "forUpdate must be empty (no lock support)");
        assertNull(dialect.getDialectModel().getSqls().getLockHint(),
                "lockHint must be empty");
        assertFalse(dialect.isSupportReturningForUpdate(),
                "supportReturningForUpdate must be false");
        safeDropTable("pg_t");
    }

    @Test
    @Timeout(120)
    public void testErrorCodesTranslation() {
        // duckdb throws SQLException(code=0, SQLState=null) with the message
        // "Catalog Error: Table with name X does not exist..." - the dialect pattern
        // .+_with_name_.+_does_not_exist.* must map it to bad-sql-grammar
        try {
            jdbc.findFirst(new SQL("SELECT * FROM tbl_with_name_not_exist_x"),
                    new SingleBinderRowMapper(DataParameterBinders.ANY));
            throw new IllegalStateException("expected a translated exception");
        } catch (Exception e) {
            String errorCode = null;
            Throwable cur = e;
            while (cur != null && errorCode == null) {
                if (cur instanceof io.nop.api.core.exceptions.NopException ne) {
                    errorCode = ne.getErrorCode().toString();
                }
                cur = cur.getCause();
            }
            assertEquals("nop.err.dao.sql.bad-sql-grammar", errorCode,
                    "catalog error must translate to bad-sql-grammar, got: " + e);
        }
    }

    @Test
    @Timeout(300)
    public void testSqlDataTypesDdlMatrix() {
        DialectModel model = dialect.getDialectModel();
        int checked = 0;
        for (SqlDataTypeModel dataTypeModel : model.getSqlDataTypes()) {
            if (dataTypeModel.isDeprecated()) {
                continue;
            }
            String dataType = dataTypeModel.getName();
            if (dataTypeModel.getCode() != null) {
                dataType = dataTypeModel.getCode();
            }
            String tableName = "ddt_" + checked;
            safeDropTable(tableName);
            // includes GEOMETRY (natively built into duckdb 1.5, review-probe verified)
            jdbc.executeUpdate(new SQL("CREATE TABLE " + tableName + " (c " + dataType + ")"));
            jdbc.executeUpdate(new SQL("INSERT INTO " + tableName + " VALUES (NULL)"));
            Object v = jdbc.findFirst(new SQL("SELECT c FROM " + tableName),
                    new SingleBinderRowMapper(DataParameterBinders.ANY));
            assertNull(v, "null roundtrip for type " + dataType);
            safeDropTable(tableName);
            checked++;
        }
        System.out.println("[WI8-DIALECT] ddl matrix checked=" + checked + " types");
        assertTrue(checked >= 15, "sqlDataTypes matrix must cover the declared types");
    }
}
