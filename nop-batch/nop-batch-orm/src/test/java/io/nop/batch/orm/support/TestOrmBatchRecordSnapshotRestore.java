package io.nop.batch.orm.support;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.dao.api.IDaoEntity;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1-3.4-001 判别测试（plan 2382）：OrmBatchRecordSnapshotBuilder.restore 必须返回
 * 恢复后的完整记录列表（返回值替换 retry 集——AbstractRetryBatchConsumer/RetryConsumeHelper）。
 * 修复前 ret 从未 add → 恒空列表 → 失败 chunk 记录被静默丢弃。
 */
public class TestOrmBatchRecordSnapshotRestore {

    @SuppressWarnings("unchecked")
    private static <T extends IOrmEntity> T newEntityProxy() {
        return (T) Proxy.newProxyInstance(TestOrmBatchRecordSnapshotRestore.class.getClassLoader(),
                new Class[]{IOrmEntity.class, IDaoEntity.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "orm_initedValues":
                            return new HashMap<String, Object>();
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "IOrmEntityProxy";
                        default:
                            return null;
                    }
                });
    }

    @Test
    public void testRestoreReturnsRestoredItems() {
        IOrmSession session = (IOrmSession) Proxy.newProxyInstance(
                TestOrmBatchRecordSnapshotRestore.class.getClassLoader(),
                new Class[]{IOrmSession.class},
                (proxy, method, args) -> null);
        IOrmTemplate ormTemplate = (IOrmTemplate) Proxy.newProxyInstance(
                TestOrmBatchRecordSnapshotRestore.class.getClassLoader(),
                new Class[]{IOrmTemplate.class},
                (proxy, method, args) -> method.getName().equals("currentSession") ? session : null);

        OrmBatchRecordSnapshotBuilder<IOrmEntity> builder = new OrmBatchRecordSnapshotBuilder<>(ormTemplate, false);

        List<IOrmEntity> items = Arrays.asList(newEntityProxy(), newEntityProxy(), newEntityProxy());
        IBatchChunkContext ctx = (IBatchChunkContext) Proxy.newProxyInstance(
                TestOrmBatchRecordSnapshotRestore.class.getClassLoader(),
                new Class[]{IBatchChunkContext.class},
                (proxy, method, args) -> null);

        var snapshot = builder.buildSnapshot(items, ctx);
        Collection<IOrmEntity> restored = snapshot.restore(items, ctx);

        assertNotNull(restored, "restore must return a list");
        assertEquals(items.size(), restored.size(),
                "restore must return ALL restored items (P1-3.4-001: retry-set replacement semantics) — "
                        + "pre-fix this was always empty");
    }
}
