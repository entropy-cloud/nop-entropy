package io.nop.code.service.impl;

import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-15 (plan 2565, adjudication J / F2, TP-008): replaceRows' INSERT side must batch with
 * flush+clear per slice, symmetric to the existing DELETE side. A counting session proxy
 * (manual proxy, zero-mockito) observes the flush count with a low injected slice size.
 */
class TestGraphMetricInsertBatching {

    @Test
    void testInsertSideFlushesPerSlice() throws Exception {
        AtomicInteger flushCount = new AtomicInteger();
        AtomicInteger clearCount = new AtomicInteger();
        AtomicInteger saveCount = new AtomicInteger();

        // counting session proxy: delegates everything, counts flush/clear
        IOrmSession countingSession = (IOrmSession) java.lang.reflect.Proxy.newProxyInstance(
                IOrmSession.class.getClassLoader(),
                newClassInterfaces(IOrmSession.class),
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "flush":
                            flushCount.incrementAndGet();
                            return null;
                        case "clear":
                            clearCount.incrementAndGet();
                            return null;
                        default:
                            return nullish(method);
                    }
                });

        IOrmTemplate countingTemplate = (IOrmTemplate) java.lang.reflect.Proxy.newProxyInstance(
                IOrmTemplate.class.getClassLoader(),
                newClassInterfaces(IOrmTemplate.class),
                (proxy, method, args) -> {
                    if (method.getName().equals("runInSession")) {
                        java.util.function.Function<IOrmSession, Object> callback =
                                (java.util.function.Function<IOrmSession, Object>) args[0];
                        return callback.apply(countingSession);
                    }
                    return nullish(method);
                });

        ITransactionTemplate noopTxn = (ITransactionTemplate) java.lang.reflect.Proxy.newProxyInstance(
                ITransactionTemplate.class.getClassLoader(),
                newClassInterfaces(ITransactionTemplate.class),
                (proxy, method, args) -> {
                    if (method.getName().equals("runInTransaction")) {
                        java.util.function.Function<Object, Object> task =
                                (java.util.function.Function<Object, Object>) args[2];
                        return task.apply(null);
                    }
                    if (method.getName().equals("isTransactionOpened")) {
                        return true;
                    }
                    if (method.getName().equals("isQuerySpaceDefined")) {
                        return true;
                    }
                    return nullish(method);
                });

        // dao stub: no existing rows (delete side no-ops), save counted
        IEntityDao<NopCodeGraphMetric> dao = (IEntityDao<NopCodeGraphMetric>) java.lang.reflect.Proxy.newProxyInstance(
                IEntityDao.class.getClassLoader(), new Class<?>[]{IEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAllByQuery")
                            || method.getName().equals("findPageByQuery")) {
                        return new ArrayList<>();
                    }
                    if (method.getName().equals("saveEntity")) {
                        saveCount.incrementAndGet();
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return nullish(method);
                });

        IDaoProvider provider = new IDaoProvider() {
            @Override
            public java.util.Set<String> getEntityNames() {
                return java.util.Set.of();
            }

            @Override
            public String normalizeEntityName(String entityName) {
                return entityName;
            }

            @Override
            public boolean hasDao(String entityName) {
                return true;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
                return (IEntityDao<T>) dao;
            }

            @Override
            public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
                return dao(null);
            }
        };

        GraphMetricMaterializer materializer = new GraphMetricMaterializer(
                provider, new CodeCacheManager(), noopTxn, countingTemplate);
        materializer.setInsertBatchSizeForTest(2);

        List<NopCodeGraphMetric> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(new NopCodeGraphMetric());
        }

        Method m = GraphMetricMaterializer.class.getDeclaredMethod("replaceRows", String.class, List.class);
        m.setAccessible(true);
        m.invoke(materializer, "w15-idx", rows);

        assertEquals(5, saveCount.get(), "all rows must be saved");
        assertTrue(flushCount.get() >= 3, "insert side must flush per slice (5 rows / size 2 => >=3 flushes), got "
                + flushCount.get());
        assertEquals(flushCount.get(), clearCount.get(), "each flush must pair with clear (delete-side symmetry)");
    }

    private static Class<?>[] newClassInterfaces(Class<?> iface) {
        List<Class<?>> all = new ArrayList<>();
        for (Class<?> c = iface; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                all.add(i);
            }
        }
        all.add(iface);
        return all.toArray(new Class<?>[0]);
    }

    private static Object nullish(Method method) {
        Class<?> rt = method.getReturnType();
        if (!rt.isPrimitive()) return null;
        if (rt == boolean.class) return false;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        if (rt == double.class) return 0d;
        if (rt == float.class) return 0f;
        if (rt == short.class) return (short) 0;
        if (rt == byte.class) return (byte) 0;
        if (rt == char.class) return (char) 0;
        return null;
    }
}
