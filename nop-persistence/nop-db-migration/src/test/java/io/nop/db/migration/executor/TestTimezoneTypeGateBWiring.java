/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.db.migration.executor;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.AddColumnChange;
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoErrors.ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9b（plan 32 Phase 1）：闸门 B 连通用例（接线验证，Minimum Rules #23）。
 *
 * <p>断言链：migration.xml 的 {@code type="TIMESTAMP_WITH_LOCAL_TIME_ZONE"} 属性经
 * migration.xdef `:166` 的 `!std-sql-type` 域（闸门 B =
 * {@code SimpleStdDomainHandlers.StdSqlTypeType} → {@link StdSqlType#fromStdName}）被接受并
 * 解析为 {@link ColumnDefinition#getType()}，随后进入 {@link AddColumnExecutor} 的
 * {@code stdToNativeSqlType} 调用链（{@code buildColumnType}）。h2 无 LOCAL 时区映射
 * （裁定文档 §2.1：H2 2.4.240 实测不支持 LOCAL 形态建表）⇒ 本 Phase 断言其响亮报
 * {@code nop.err.dao.dialect.std-data-type-not-supported}（无静默 VARCHAR 兜底——null-type
 * 兜底只对 type 缺失生效，本列显式携带时区类型）；Phase 3 落 oracle 映射后同链补渲染断言
 * （oracle 方言模型内存加载，裁定文档 §6.3）。
 *
 * <p>修复前红锚：时区枚举成员未落活时闸门 B 抛
 * {@code ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN}，解析失败（carrier 文件头部注释同步记载）。
 */
public class TestTimezoneTypeGateBWiring {

    private static final String MIGRATION_PATH = "/nop/db-migration/wi9b/V1.0.0__tz_column.migration.xml";
    private static final String MIGRATION_XDEF = "/nop/schema/db-migration/migration.xdef";

    private HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private ITransactionTemplate transactionTemplate;
    private IDialect dialect;
    private MigrationContext context;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        dataSource = createH2DataSource();
        jdbcTemplate = createJdbcTemplate();
        dialect = DialectManager.instance().getDialect("h2");

        context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
    }

    @AfterEach
    public void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private DbMigrationModel loadModel() {
        IResource resource = VirtualFileSystem.instance().getResource(MIGRATION_PATH);
        assertTrue(resource.exists(), "wi9b migration carrier must exist: " + MIGRATION_PATH);
        return (DbMigrationModel) new DslModelParser(MIGRATION_XDEF).parseFromResource(resource);
    }

    /**
     * 闸门 B 放行断言：xdef `!std-sql-type` 域对时区成员名不再拒绝，且解析产物携带该成员。
     */
    @Test
    public void testGateBAcceptsTimezoneStdType() {
        DbMigrationModel model = loadModel();
        assertNotNull(model);

        AddColumnChange change = (AddColumnChange) model.getChangeset().get(0);
        assertEquals("tz_wiring_table", change.getTableName());

        ColumnDefinition col = change.getColumns().get(0);
        assertEquals("tz_col", col.getName());
        assertEquals(StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE, col.getType(),
                "gate B (!std-sql-type domain) must accept the timezone std type after WI9b Phase 1");
    }

    /**
     * 接线断言（gate B → AddColumnExecutor → stdToNativeSqlType）：h2 无映射 ⇒ 响亮报错，
     * 错误码字面值 nop.err.dao.dialect.std-data-type-not-supported。
     */
    @Test
    public void testAddColumnExecutorLoudFailureWithoutMapping() {
        DbMigrationModel model = loadModel();
        AddColumnChange change = (AddColumnChange) model.getChangeset().get(0);

        AddColumnExecutor executor = new AddColumnExecutor();
        assertTrue(executor.supports(AddColumnExecutor.CHANGE_TYPE));

        NopException ex = assertThrows(NopException.class,
                () -> executor.execute(change, context, dialect));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * Phase 3 翻绿（裁定 §6.3 第 1/2 层）：闸门 B 放行的列定义进入 AddColumnExecutor 的
     * buildColumnType → stdToNativeSqlType 链后，oracle（WI9b 唯一"支持"载体方言）渲染
     * 裁定 native 文本 `TIMESTAMP WITH LOCAL TIME ZONE`（真实方言 XML 条目，非测试方言）。
     * 期望文本按 stdToNativeSqlType 链路推导（成员 (false,false) ⇒ 无 precision 渲染）。
     * 真实 Oracle 引擎建表往返 [未取证]（CI 真库仅 H2，裁定 §6.4）。
     */
    @Test
    public void testExecutorChainRendersAdjudicatedNativeTextOnOracle() {
        DbMigrationModel model = loadModel();
        AddColumnChange change = (AddColumnChange) model.getChangeset().get(0);
        ColumnDefinition col = change.getColumns().get(0);

        IDialect oracle = DialectManager.instance().getDialect("oracle");
        assertNotNull(oracle);

        String rendered = AddColumnExecutor.buildColumnType(col, oracle);
        assertEquals("TIMESTAMP WITH LOCAL TIME ZONE", rendered);
    }

    private HikariDataSource createH2DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(4);
        return ds;
    }

    private IJdbcTemplate createJdbcTemplate() {
        JdbcFactory factory = new JdbcFactory();
        transactionTemplate = factory.newTransactionTemplate(dataSource);
        return factory.newJdbcTemplate(transactionTemplate);
    }
}
