package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysChangeLog;
import io.nop.sys.dao.entity.NopSysTag;
import io.nop.sys.dao.log.OrmEntityChangeLogInterceptor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 审计 [P2]/[P3]：审计日志拦截器在无上下文线程写审计实体时 NPE（消息消费分发/
 * 后台定时任务不建立 IContext）；postUpdate 未跳过 version 列与 postSave 行为不一致。
 * 测试通过 test delta 给 NopSysTag 打 tagSet="audit" 启用拦截器。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestOrmEntityChangeLogInterceptor extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @BeforeAll
    public static void enableAuditInterceptor() {
        // nopOrmEntityChangeLogInterceptor 的 feature:on 在无配置的测试环境下默认关闭，
        // 必须显式开启才会被装配进 MultiOrmInterceptor
        AppConfig.getConfigProvider().assignConfigValue("nop.orm.audit.enabled", true);
    }

    /**
     * [P2] 无上下文线程（模拟消息消费分发线程）更新审计实体不得抛 NPE，审计记录正常落库。
     */
    @Test
    public void testAuditUpdateOnContextlessThreadWritesChangeLog() throws Exception {
        saveTag("audit-contextless");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertNull(ContextProvider.currentContext(), "worker thread must start without an IContext");
                updateTagDescription("audit-contextless", "v2");
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "audit-contextless-worker");
        worker.start();
        worker.join(60_000);

        assertNull(failure.get(),
                "audit logging on a context-less thread must not throw: " + failure.get());

        List<String> propNames = updateChangeLogPropNames("audit-contextless");
        assertTrue(propNames.contains("description"),
                "description change must be audited, got logs: " + propNames);
    }

    /**
     * [P3] postUpdate 跳过 version 列：每次乐观锁更新不得额外产生 propName=version 的变更记录。
     */
    @Test
    public void testPostUpdateAuditSkipsVersionColumn() {
        saveTag("audit-version-skip");
        String versionColumnName = updateTagDescription("audit-version-skip", "v2");

        List<String> propNames = updateChangeLogPropNames("audit-version-skip");
        assertTrue(propNames.contains("description"), "description change must be audited, got logs: " + propNames);
        assertFalse(propNames.contains(versionColumnName),
                "optimistic-lock version column must not be audited as a change, got logs: " + propNames);
    }

    /**
     * [P2] 直接驱动 initChangeLog：无上下文线程（消息消费分发/后台定时任务在flush阶段
     * 读取上下文）不得抛 NPE，appId 应回退应用名、operatorId 置空。
     * 注意实体必须在主线程构造——工作线程内的 DAO 调用会自动建立上下文，掩盖问题。
     */
    @Test
    public void testInitChangeLogToleratesContextlessThread() throws Exception {
        ExposedInterceptor interceptor = new ExposedInterceptor();
        NopSysTag tag = daoProvider.daoFor(NopSysTag.class).newEntity();
        tag.setName("audit-init-log");
        NopSysChangeLog log = daoProvider.daoFor(NopSysChangeLog.class).newEntity();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertNull(ContextProvider.currentContext(), "worker thread must start without an IContext");
                interceptor.fireInitChangeLog(log, NopSysDaoConstants.OPERATION_UPDATE, tag);

                assertEquals(AppConfig.appName(), log.getAppId(), "appId must fall back to the app name");
                assertEquals(NopSysDaoConstants.OPERATION_UPDATE, log.getOperationName());
                assertEquals("system", log.getOperatorId(), "operatorId must fall back to 'system' (mandatory column)");
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "audit-init-contextless-worker");
        worker.start();
        worker.join(60_000);

        assertNull(failure.get(), "initChangeLog on a context-less thread must not throw: " + failure.get());
    }

    static class ExposedInterceptor extends OrmEntityChangeLogInterceptor {
        void fireInitChangeLog(NopSysChangeLog log, String defaultOpName, IOrmEntity entity) {
            initChangeLog(log, defaultOpName, entity);
        }
    }

    private void saveTag(String name) {
        IEntityDao<NopSysTag> dao = daoProvider.daoFor(NopSysTag.class);
        ormTemplate.runInSession(() -> {
            NopSysTag tag = dao.newEntity();
            tag.setName(name);
            tag.setDescription("v1");
            tag.setCreatedBy("test");
            tag.setCreateTime(Timestamp.from(Instant.now()));
            tag.setUpdatedBy("test");
            tag.setUpdateTime(Timestamp.from(Instant.now()));
            dao.saveEntity(tag);
        });
    }

    /**
     * @return 乐观锁版本列的数据库列名（用于断言其不出现在审计记录中）
     */
    private String updateTagDescription(String name, String newDescription) {
        IEntityDao<NopSysTag> dao = daoProvider.daoFor(NopSysTag.class);
        ormTemplate.runInSession(() -> {
            NopSysTag tag = findByName(dao, name);
            assertNotNull(tag, "tag must exist before update");
            tag.setDescription(newDescription);
            dao.updateEntity(tag);
        });
        NopSysTag tag = findByName(dao, name);
        int versionPropId = tag.orm_entityModel().getVersionPropId();
        return tag.orm_entityModel().getColumnByPropId(versionPropId, false).getName();
    }

    private NopSysTag findByName(IEntityDao<NopSysTag> dao, String name) {
        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq(NopSysTag.PROP_NAME_name, name));
        return dao.findFirstByQuery(query);
    }

    private List<String> updateChangeLogPropNames(String tagName) {
        IEntityDao<NopSysTag> dao = daoProvider.daoFor(NopSysTag.class);
        NopSysTag tag = findByName(dao, tagName);
        assertNotNull(tag, "tag must exist: " + tagName);

        IEntityDao<NopSysChangeLog> logDao = daoProvider.daoFor(NopSysChangeLog.class);
        return logDao.findAll().stream()
                .filter(log -> tag.orm_idString().equals(log.getObjId()))
                .filter(log -> NopSysDaoConstants.OPERATION_UPDATE.equals(log.getOperationName()))
                .map(NopSysChangeLog::getPropName)
                .collect(Collectors.toList());
    }
}
