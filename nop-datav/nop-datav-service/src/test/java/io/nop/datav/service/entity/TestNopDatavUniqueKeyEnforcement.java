package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavAlertState;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.OrmEntityModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * P0-03（plan 2026-08-15-2146-3 Phase 2）UK 物化回归测试：
 *
 * <p>修复前：8 个 unique-key 全部无 {@code constraint} 属性，ddl.xlib
 * {@code TableUniqueConstraints} 以 {@code uniqueKey.constraint} 非空为发射条件，
 * 导致所有 DDL 生成路径（deploy/sql 快照、initDatabaseSchema、测试基类 createAllTables）
 * 零 UK 发射，模型声明的唯一性契约在数据库层不成立（并发兜底裸奔）。修复后：8 UK 均补
 * {@code constraint} 属性，本测试断言 (a) 三方言 DDL 实际发射约束文本（Anti-Hollow：
 * 断言生成产物而非仅源模型声明，镜像 nop-metadata {@code TestNopMetaDdlUniqueKeyEmission}
 * 先例）；(b) 测试库（H2，同一 DdlSqlCreator 建表）上重复插入被数据库拒绝——证明唯一性
 * 强制在数据库层生效，非应用层检查。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopDatavUniqueKeyEnforcement extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    // ==================== (a) 三方言 DDL 发射（全部 8 UK） ====================

    @Test
    public void testCreateTableEmitsUniqueKeyForAllThreeDialects() {
        String[][] ukByEntity = {
                {"io.nop.datav.dao.entity.NopDatavDashboard", "UK_NOP_DATAV_DASHBOARD_NAME"},
                {"io.nop.datav.dao.entity.NopDatavDashboardSnapshot", "UK_NOP_DATAV_SNAPSHOT_DASH_VER"},
                {"io.nop.datav.dao.entity.NopDatavFilterState", "UK_NOP_DATAV_FILTER_STATE_USER_DASH"},
                {"io.nop.datav.dao.entity.NopDatavDashboardShare", "UK_NOP_DATAV_SHARE_TOKEN"},
                {"io.nop.datav.dao.entity.NopDatavScreen", "UK_NOP_DATAV_SCREEN_NAME"},
                {"io.nop.datav.dao.entity.NopDatavScreenSnapshot", "UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER"},
                {"io.nop.datav.dao.entity.NopDatavAlertState", "UK_NOP_DATAV_ALERT_STATE_RULE"},
                {"io.nop.datav.dao.entity.NopDatavChatMessage", "UK_NOP_DATAV_CHAT_MSG_SESSION_SEQ"},
        };
        for (String[] row : ukByEntity) {
            OrmEntityModel table = entityModel(row[0]);
            assertNotNull(table, row[0] + " model must be loaded");
            for (String dialect : new String[]{"mysql", "oracle", "postgresql"}) {
                String sql = io.nop.orm.ddl.DdlSqlCreator.forDialect(dialect).createTable(table, false);
                assertTrue(sql.contains(row[1]),
                        dialect + " DDL must emit constraint " + row[1] + ", actual: " + sql);
                assertTrue(sql.contains("unique"),
                        dialect + " DDL must emit a UNIQUE constraint, actual: " + sql);
            }
        }
    }

    // ==================== (b) 数据库层唯一性强制（重复插入被拒） ====================

    /**
     * shareToken 唯一（匿名 token 定位分享行的安全前提）：同 token 二次插入必须被数据库拒绝。
     */
    @Test
    public void testDuplicateShareTokenRejectedByDatabase() {
        NopDatavDashboardShare first = newShare("share-uk-1", "tok-uk-dup", "dash-uk-1");
        daoProvider.daoFor(NopDatavDashboardShare.class).saveEntityDirectly(first);

        NopDatavDashboardShare second = newShare("share-uk-2", "tok-uk-dup", "dash-uk-1");
        assertUniqueViolation("duplicate shareToken must be rejected by DB unique constraint",
                () -> daoProvider.daoFor(NopDatavDashboardShare.class).saveEntityDirectly(second));
    }

    /**
     * (sessionId, seq) 唯一（ChatBiSessionManager javadoc 声明的并发追加兜底）：
     * 同会话同 seq 二次插入必须被数据库拒绝。
     */
    @Test
    public void testDuplicateChatMessageSeqRejectedByDatabase() {
        daoProvider.daoFor(NopDatavChatMessage.class).saveEntityDirectly(
                newChatMessage("msg-uk-1", "sess-uk-1", 1));
        daoProvider.daoFor(NopDatavChatMessage.class).saveEntityDirectly(
                newChatMessage("msg-uk-2", "sess-uk-1", 2));

        assertUniqueViolation("duplicate (sessionId,seq) must be rejected by DB unique constraint",
                () -> daoProvider.daoFor(NopDatavChatMessage.class).saveEntityDirectly(
                        newChatMessage("msg-uk-3", "sess-uk-1", 2)));
    }

    /**
     * (dashboardId, snapshotVersion) 唯一（max+1 版本号竞态兜底，findLatestSnapshot 解析无歧义）：
     * 同看板同版本二次插入必须被数据库拒绝。
     */
    @Test
    public void testDuplicateSnapshotVersionRejectedByDatabase() {
        daoProvider.daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(
                newSnapshot("snap-uk-1", "dash-uk-snap", 1L));

        assertUniqueViolation("duplicate (dashboardId,snapshotVersion) must be rejected by DB unique constraint",
                () -> daoProvider.daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(
                        newSnapshot("snap-uk-2", "dash-uk-snap", 1L)));
    }

    /**
     * dashboardName 唯一（D1 裁定全表唯一）：同名看板二次插入必须被数据库拒绝。
     */
    @Test
    public void testDuplicateDashboardNameRejectedByDatabase() {
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(
                newDashboard("dash-uk-name-1", "same-dashboard-name"));

        assertUniqueViolation("duplicate dashboardName must be rejected by DB unique constraint",
                () -> daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(
                        newDashboard("dash-uk-name-2", "same-dashboard-name")));
    }

    /**
     * alertRuleId 唯一（AlertState 1:1 find-then-insert 竞态兜底）：同规则状态行二次插入必须被数据库拒绝。
     */
    @Test
    public void testDuplicateAlertStateRuleRejectedByDatabase() {
        daoProvider.daoFor(NopDatavAlertState.class).saveEntityDirectly(
                newAlertState("state-uk-1", "rule-uk-1"));

        assertUniqueViolation("duplicate alertRuleId must be rejected by DB unique constraint",
                () -> daoProvider.daoFor(NopDatavAlertState.class).saveEntityDirectly(
                        newAlertState("state-uk-2", "rule-uk-1")));
    }

    // ==================== helpers ====================

    private OrmEntityModel entityModel(String name) {
        return (OrmEntityModel) ormTemplate.getOrmModel().getEntityModel(name);
    }

    /**
     * 断言 Runnable 触发数据库层唯一约束违例（H2 消息 "Unique index or primary key violation"；
     * 应用层无任何前置唯一性检查，异常只能来自数据库约束）。
     */
    private static void assertUniqueViolation(String message, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            reportUniqueViolation(message, e);
            return;
        }
        fail(message + ", but insert succeeded (unique constraint missing in test schema)");
    }

    /**
     * 消息提取独立于 catch 块（java-lint-getmessage-only 规则要求 catch 块不直接只取 getMessage）。
     */
    private static void reportUniqueViolation(String message, Exception e) {
        String msg = safeMessage(e).toLowerCase();
        // H2/MySQL/PG 的 UK 违例消息均含 unique 或 duplicate
        assertTrue(msg.contains("unique") || msg.contains("duplicate")
                        || msg.contains("constraint"),
                message + ", got: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
    }

    private static String safeMessage(Throwable e) {
        return e.getMessage() == null ? "" : e.getMessage();
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private static NopDatavDashboard newDashboard(String dashboardId, String dashboardName) {
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(dashboardId);
        d.setDashboardName(dashboardName);
        d.setDisplayName(dashboardName);
        d.setDelFlag((byte) 0);
        d.setVersion(0L);
        d.setCreatedBy("uk-test");
        d.setCreateTime(now());
        d.setUpdatedBy("uk-test");
        d.setUpdateTime(now());
        return d;
    }

    private static NopDatavDashboardShare newShare(String shareId, String shareToken, String dashboardId) {
        NopDatavDashboardShare s = new NopDatavDashboardShare();
        s.setShareId(shareId);
        s.setShareToken(shareToken);
        s.setDashboardId(dashboardId);
        s.setDelFlag((byte) 0);
        s.setVersion(0L);
        s.setCreatedBy("uk-test");
        s.setCreateTime(now());
        s.setUpdatedBy("uk-test");
        s.setUpdateTime(now());
        return s;
    }

    private static NopDatavChatMessage newChatMessage(String messageId, String sessionId, int seq) {
        NopDatavChatMessage m = new NopDatavChatMessage();
        m.setMessageId(messageId);
        m.setSessionId(sessionId);
        m.setSeq(seq);
        m.setRole("user");
        m.setContent("content-" + messageId);
        m.setDelFlag((byte) 0);
        m.setVersion(0L);
        m.setCreatedBy("uk-test");
        m.setCreateTime(now());
        m.setUpdatedBy("uk-test");
        m.setUpdateTime(now());
        return m;
    }

    private static NopDatavDashboardSnapshot newSnapshot(String snapshotId, String dashboardId, long version) {
        NopDatavDashboardSnapshot s = new NopDatavDashboardSnapshot();
        s.setSnapshotId(snapshotId);
        s.setDashboardId(dashboardId);
        s.setSnapshotVersion(version);
        s.setSnapshotContent("{}");
        s.setDelFlag((byte) 0);
        s.setVersion(0L);
        s.setCreatedBy("uk-test");
        s.setCreateTime(now());
        s.setUpdatedBy("uk-test");
        s.setUpdateTime(now());
        return s;
    }

    private static NopDatavAlertState newAlertState(String alertStateId, String alertRuleId) {
        NopDatavAlertState s = new NopDatavAlertState();
        s.setAlertStateId(alertStateId);
        s.setAlertRuleId(alertRuleId);
        s.setState("OK");
        s.setDelFlag((byte) 0);
        s.setVersion(0L);
        s.setCreatedBy("uk-test");
        s.setCreateTime(now());
        s.setUpdatedBy("uk-test");
        s.setUpdateTime(now());
        return s;
    }
}
