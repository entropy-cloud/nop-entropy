package io.nop.metadata.service.connection;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P2-08（2026-08-23 审计）回归：JDBC 白名单允许 {@code jdbc:h2:file:} 且无路径约束。
 *
 * <p>缺陷机制：AR-02 白名单有意放行 {@code jdbc:h2:file:}（本地文件模式），但
 * {@code jdbc:h2:file:/any/path/db} 可指向文件系统任意位置——不存在则创建（H2 未强制 IFEXISTS），
 * 存在则打开读写。INIT/RUNSCRIPT 等 URL 参数侧已拦，文件库本身的建库/读写不受限；持有数据源
 * 创建权限的账号可在服务器任意可写路径落盘 DB 文件（结合 expression 通道/sourceSql 可读写其内容）。
 *
 * <p>修复（纵深，对齐审计建议"降为显式配置开启"）：默认拒绝 {@code jdbc:h2:file:}；仅当配置
 * {@code nop.metadata.datasource.h2-file-allowed-dirs}（绝对目录前缀，逗号分隔）且路径落在
 * 允许目录内时放行；相对路径与含 {@code ..} 的路径 fail-closed 拒绝。
 *
 * <p>包级 seam（validateJdbcUrl）直测。mutate-fail：回退为无路径约束时默认拒绝用例
 * 不抛异常 → assertThrows 失败。
 */
public class TestH2FileProtocolPathAllowList {

    private static final String CFG_KEY = "nop.metadata.datasource.h2-file-allowed-dirs";

    /** 默认（未配置允许目录）：jdbc:h2:file: 整体拒绝，reason 指引配置键。 */
    @Test
    public void testH2FileBlockedByDefaultWithoutConfig() {
        MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();
        NopException ex = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:h2:file:/data/meta/db"),
                "jdbc:h2:file: must be denied by default (no allowed-dirs configured)");
        assertEqualsBlocked(ex);
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains(CFG_KEY),
                "reason must point to the config key '" + CFG_KEY + "': " + reason);
    }

    /** 配置允许目录后：目录内路径放行，目录外路径拒绝。 */
    @Test
    public void testH2FileAllowedOnlyWithinConfiguredDirs() throws Exception {
        MetaDataSourceConnectionProcessor service = serviceWithAllowedDirs("/data/h2dbs,/var/lib/meta");

        assertDoesNotThrow(() -> service.validateJdbcUrl("jdbc:h2:file:/data/h2dbs/sales"),
                "path inside allowed dir must pass");
        assertDoesNotThrow(() -> service.validateJdbcUrl("jdbc:h2:file:/var/lib/meta/tenant1/db;CACHE_SIZE=8192"),
                "path inside allowed dir with H2 settings suffix must pass");

        NopException ex = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:h2:file:/etc/meta/db"),
                "path outside allowed dirs must be denied");
        assertEqualsBlocked(ex);

        // 前缀字面碰撞不得放行（/data/h2dbs-evil 不是 /data/h2dbs 子路径）
        NopException ex2 = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:h2:file:/data/h2dbs-evil/db"),
                "sibling dir sharing a string prefix must be denied (path-boundary check)");
        assertEqualsBlocked(ex2);
    }

    /** 路径遍历/相对路径 fail-closed 拒绝。 */
    @Test
    public void testH2FilePathTraversalAndRelativePathsDenied() throws Exception {
        MetaDataSourceConnectionProcessor service = serviceWithAllowedDirs("/data/h2dbs");

        NopException ex = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:h2:file:/data/h2dbs/../../etc/db"),
                "traversal segment '..' must be denied");
        assertEqualsBlocked(ex);

        NopException ex2 = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:h2:file:relative/db"),
                "relative path (process CWD dependent) must be denied");
        assertEqualsBlocked(ex2);

        NopException ex3 = assertThrows(NopException.class,
                () -> service.validateJdbcUrl("jdbc:h2:file:~/meta/db"),
                "home-relative path must be denied");
        assertEqualsBlocked(ex3);
    }

    /** h2:mem 不受影响（本地内存模式，无文件面）。 */
    @Test
    public void testH2MemUnaffected() {
        MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();
        assertDoesNotThrow(() -> service.validateJdbcUrl("jdbc:h2:mem:testdb"));
    }

    private static void assertEqualsBlocked(NopException ex) {
        assertTrue(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode().equals(ex.getErrorCode()),
                "must throw ERR_DATASOURCE_JDBC_URL_BLOCKED, got: " + ex.getErrorCode());
    }

    /**
     * 反射设置允许目录配置字段（类在未修复代码上无该字段 → NoSuchFieldException，
     * 属新增配置面测试的预期红形态；主红锚点见 testH2FileBlockedByDefaultWithoutConfig）。
     */
    private static MetaDataSourceConnectionProcessor serviceWithAllowedDirs(String csv) throws Exception {
        MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();
        java.lang.reflect.Field f = MetaDataSourceConnectionProcessor.class
                .getDeclaredField("h2FileAllowedDirsCsv");
        f.setAccessible(true);
        f.set(service, csv);
        return service;
    }
}
