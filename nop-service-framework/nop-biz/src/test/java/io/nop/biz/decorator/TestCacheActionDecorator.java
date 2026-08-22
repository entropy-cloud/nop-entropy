package io.nop.biz.decorator;

import io.nop.api.core.util.FutureHelper;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.cache.MapCache;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCacheActionDecorator {

    @Test
    public void testCacheHitAvoidsUnderlyingAction() {
        MapCache<Object, Object> cache = new MapCache<>("test-cache", false);
        CounterAction counter = new CounterAction();

        CacheActionDecorator decorator = new CacheActionDecorator(
                new SingleCacheProvider(cache), "test-cache", scope -> "key-1");
        IServiceAction decorated = decorator.decorate(counter);

        Object first = decorated.invoke(new Object(), null, serviceContext());
        assertEquals(1, counter.count);
        assertSame(first, decorated.invoke(new Object(), null, serviceContext()));
        // 命中缓存后不再触发底层action
        assertEquals(1, counter.count);
        assertTrue(cache.containsKey("key-1"));
    }

    @Test
    public void testCacheWriteOnMiss() {
        MapCache<Object, Object> cache = new MapCache<>("test-cache", false);
        CounterAction counter = new CounterAction();

        CacheActionDecorator decorator = new CacheActionDecorator(
                new SingleCacheProvider(cache), "test-cache", scope -> "key-1");
        IServiceAction decorated = decorator.decorate(counter);

        Object result = decorated.invoke(new Object(), null, serviceContext());
        // 回源执行后结果写入缓存
        assertSame(result, cache.get("key-1"));
    }

    @Test
    public void testNullCacheKeyBypassesCache() {
        MapCache<Object, Object> cache = new MapCache<>("test-cache", false);
        CounterAction counter = new CounterAction();

        CacheActionDecorator decorator = new CacheActionDecorator(
                new SingleCacheProvider(cache), "test-cache", scope -> null);
        IServiceAction decorated = decorator.decorate(counter);

        decorated.invoke(new Object(), null, serviceContext());
        decorated.invoke(new Object(), null, serviceContext());
        assertEquals(2, counter.count);
        assertFalse(cache.containsKey("key-1"));
    }

    @Test
    public void testAsyncResultCachedAfterCompletion() {
        MapCache<Object, Object> cache = new MapCache<>("test-cache", false);

        CacheActionDecorator decorator = new CacheActionDecorator(
                new SingleCacheProvider(cache), "test-cache", scope -> "key-1");
        IServiceAction decorated = decorator.decorate((request, selection, ctx) ->
                FutureHelper.success("async-result"));

        Object result = FutureHelper.syncGet((CompletionStage<Object>) decorated.invoke(new Object(), null, serviceContext()));
        assertEquals("async-result", result);
        assertEquals("async-result", cache.get("key-1"));
    }

    static IServiceContext serviceContext() {
        return (IServiceContext) Proxy.newProxyInstance(
                TestCacheActionDecorator.class.getClassLoader(),
                new Class[]{IServiceContext.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getEvalScope":
                                return XLang.newEvalScope();
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    }
                });
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0D;
        if (type == float.class)
            return 0F;
        if (type == short.class)
            return (short) 0;
        if (type == byte.class)
            return (byte) 0;
        if (type == char.class)
            return (char) 0;
        return null;
    }

    static class CounterAction implements IServiceAction {
        int count;

        @Override
        public Object invoke(Object request, io.nop.api.core.beans.FieldSelectionBean selection,
                             IServiceContext context) {
            count++;
            return "result-" + count;
        }
    }

    static class SingleCacheProvider implements ICacheProvider {
        private final ICache<Object, Object> cache;

        SingleCacheProvider(ICache<Object, Object> cache) {
            this.cache = cache;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> ICache<K, V> getCache(String name) {
            return (ICache<K, V>) cache;
        }

        @Override
        public void clearAllCache() {
            cache.clear();
        }
    }
}
