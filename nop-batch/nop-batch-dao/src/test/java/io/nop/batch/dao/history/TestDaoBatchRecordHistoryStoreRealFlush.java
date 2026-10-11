package io.nop.batch.dao.history;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.history.IBatchHistoryStoreModel;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1-3.4-002 钉定测试升级（plan 2382）：真 IEntityDao flush 校验——
 * 既有 Proxy 测试不触发 ORM mandatory 校验（审计「Proxy 盲区」）。
 * saveProcessed 落库行必须通过 mandatory 校验（retryCount/batchSize/handleStatus 非空），
 * 且行真实持久化可回读。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoBatchRecordHistoryStoreRealFlush extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testSaveProcessedPassesMandatoryValidationAndPersists() {
        IEntityDao<NopBatchRecordResult> dao = daoProvider.dao(NopBatchRecordResult.class.getName());
        IBatchHistoryStoreModel model = new IBatchHistoryStoreModel() {
            @Override
            public IEvalFunction getRecordKeyExpr() {
                return (t, args, scope) -> String.valueOf(args[0]);
            }

            @Override
            public IEvalFunction getRecordInfoExpr() {
                return null;
            }

            @Override
            public boolean isOnlySaveLastError() {
                return false;
            }

            @Override
            public Object prop_get(String propName) {
                return null;
            }

            @Override
            public boolean prop_has(String propName) {
                return false;
            }
        };
        DaoBatchRecordHistoryStore<String> store = new DaoBatchRecordHistoryStore<>(dao, model);

        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        taskCtx.setTaskId("real-flush-task");
        // saveProcessed 仅消费 getTaskId/getEvalScope——IBatchChunkContext 以 Proxy 委托
        // （接口抽象方法多，匿名类不可行；未消费方法返回 null 即可）
        IBatchChunkContext chunkCtx = (IBatchChunkContext) java.lang.reflect.Proxy.newProxyInstance(
                TestDaoBatchRecordHistoryStoreRealFlush.class.getClassLoader(),
                new Class[]{IBatchChunkContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getTaskId":
                            return taskCtx.getTaskId();
                        case "getEvalScope":
                            return taskCtx.getEvalScope();
                        default:
                            return null;
                    }
                });

        store.saveProcessed(Arrays.asList("k1", "k2", "k3"), null, chunkCtx);

        // flush 触发 ORM mandatory 校验——retryCount/batchSize/handleStatus 缺失即抛
        // ERR_ORM_MANDATORY_PROP_IS_NULL（P1-3.4-002 修复前此处必失败）
        ormTemplate.runInSession(session -> {
            session.flush();
            return null;
        });

        // 回读验证真实持久化
        NopBatchRecordResult probe = dao.newEntity();
        probe.setBatchTaskId("real-flush-task");
        List<NopBatchRecordResult> rows = dao.findAllByExample(probe);
        assertEquals(3, rows.size(), "three processed records must be persisted");
        for (NopBatchRecordResult row : rows) {
            assertNotNull(row.getRetryCount());
            assertNotNull(row.getBatchSize());
            assertNotNull(row.getHandleStatus());
            assertEquals("real-flush-task", row.getBatchTaskId());
            assertEquals(0, row.getResultStatus());
        }
    }
}
