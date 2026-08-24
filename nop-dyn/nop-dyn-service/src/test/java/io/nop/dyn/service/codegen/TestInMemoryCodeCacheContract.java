package io.nop.dyn.service.codegen;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.ICache;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResourceStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.dyn.service.NopDynErrors.ERR_DYN_BIZ_MODEL_NOT_EXISTS;
import static io.nop.dyn.service.NopDynErrors.ERR_DYN_MAX_BIZ_OBJECTS_EXCEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InMemoryCodeCache 的对外契约：资源store的惰性构建与miss回调、未注册bizObj的null语义、
 * 生成方法的synchronized约束（类注释声明"所有代码生成都使用synchronized保护"）、动态对象数量上限
 */
public class TestInMemoryCodeCacheContract {

    static class RecordingHook implements IDynCodeGenCacheHook {
        final Map<String, GraphQLBizModel> loadResult = new HashMap<>();
        final List<String> preparedResources = new ArrayList<>();

        @Override
        public Map<String, GraphQLBizModel> prepareLoadModule(InMemoryCodeCache cache, ModuleModel module,
                                                              io.nop.core.lang.eval.IEvalScope scope) {
            return loadResult;
        }

        @Override
        public void prepareUnloadModule(InMemoryCodeCache cache, ModuleModel module,
                                        io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareBizObject(InMemoryCodeCache cache, GraphQLBizModel bizModel, ModuleModel module,
                                     io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareOrmModel(InMemoryCodeCache cache, ModuleModel module,
                                    io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareResource(InMemoryCodeCache cache, String path, io.nop.core.lang.eval.IEvalScope scope) {
            preparedResources.add(path);
        }
    }

    static Object defaultValue(Class<?> type) {
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

    @SuppressWarnings("unchecked")
    static <T> T newProxy(Class<T> clazz, java.util.function.BiFunction<Object, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                (proxy, method, args) -> handler.apply(method.getName(), args));
    }

    /**
     * 租户初始化链只调用 addModule（内部 clearMergedStore），mergedStore 在首次 getResourceStore 前恒为 null。
     * DynCodeGen.getTenantResourceStore 若直接返回 mergedStore 字段，租户动态资源层恒为 null。
     */
    @Test
    public void testFreshCacheMergedStoreNullUntilResourceStoreBuilt() {
        RecordingHook hook = new RecordingHook();
        InMemoryCodeCache cache = new InMemoryCodeCache("t1", "/nop/templates/dyn-gen", hook);

        assertNull(cache.getMergedStore(), "新初始化的租户cache尚未构建mergedStore");

        IResourceStore store = cache.getResourceStore();
        assertNotNull(store, "getResourceStore必须惰性构建并返回非空store");
        assertNotNull(cache.getMergedStore());

        // miss时回调prepare回调；资源仍不存在则按returnNullIfNotExists语义返回null，而不是以NPE中断
        assertNull(store.getResource("/app/t1/model/UnknownObj/UnknownObj.xmeta", true));
        assertEquals(List.of("/app/t1/model/UnknownObj/UnknownObj.xmeta"), hook.preparedResources);

        // clearMergedStore后再次获取会重建
        cache.clearMergedStore();
        assertNull(cache.getMergedStore());
        assertNotNull(cache.getResourceStore());
    }

    /**
     * DynCodeGen.getTenantResourceStore 必须走 getResourceStore()（惰性构建），
     * 不能直接读 mergedStore 字段（租户路径该字段恒为 null，租户动态资源层失效）
     */
    @Test
    public void testTenantResourceStoreLazilyBuilt() {
        DynCodeGen codeGen = new DynCodeGen();
        codeGen.daoProvider = newProxy(IDaoProvider.class, (name, args) -> {
            if (name.equals("daoFor"))
                return newProxy(IEntityDao.class, (m, a) -> {
                    if (m.equals("findAllByExample"))
                        return new ArrayList<>();
                    return defaultValue(Object.class);
                });
            return defaultValue(Object.class);
        });
        codeGen.ormTemplate = newProxy(IOrmTemplate.class, (name, args) -> {
            // runInSession 的 Runnable/Function 直接执行
            for (Object arg : args) {
                if (arg instanceof Runnable)
                    ((Runnable) arg).run();
                else if (arg instanceof java.util.function.Function)
                    return ((java.util.function.Function<Object, Object>) arg).apply(null);
            }
            return null;
        });

        IResourceStore store = codeGen.getTenantResourceStore("t1");
        assertNotNull(store, "租户资源store必须惰性构建，而不是返回尚未初始化的mergedStore(null)");
    }

    /**
     * 未注册bizObj的meta查询按miss返回null，而不是NPE
     */
    @Test
    public void testGetObjMetaUnknownBizObjReturnsNull() {
        InMemoryCodeCache cache = new InMemoryCodeCache(null, "/nop/templates/dyn-gen", new RecordingHook());
        assertNull(cache.getObjMeta("UnknownObj", false));
    }

    /**
     * genBizObjFiles 是公共生成入口，null bizModel 必须抛带errorCode的NopException
     */
    @Test
    public void testGenBizObjFilesNullBizModelRejected() {
        InMemoryCodeCache cache = new InMemoryCodeCache(null, "/nop/templates/dyn-gen", new RecordingHook());
        NopException ex = assertThrows(NopException.class, () -> cache.genBizObjFiles(false, null));
        assertEquals(ERR_DYN_BIZ_MODEL_NOT_EXISTS.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 类契约：所有代码生成方法（含 genPageFile/genViewFile/getOrmModel）都必须 synchronized，
     * 共享 IEvalScope 的局部变量槽不允许交叉写入；mergedStore/dynResourceStore 必须volatile保证可见性
     */
    @Test
    public void testGenerationMethodsSynchronizedAndStoresVolatile() throws Exception {
        assertTrue(Modifier.isSynchronized(InMemoryCodeCache.class
                .getMethod("genPageFile", ModuleModel.class, GraphQLBizModel.class, String.class, boolean.class)
                .getModifiers()), "genPageFile必须synchronized");
        assertTrue(Modifier.isSynchronized(InMemoryCodeCache.class
                .getMethod("genViewFile", ModuleModel.class, GraphQLBizModel.class, boolean.class)
                .getModifiers()), "genViewFile必须synchronized");
        assertTrue(Modifier.isSynchronized(InMemoryCodeCache.class
                .getMethod("getOrmModel", ModuleModel.class, boolean.class).getModifiers()),
                "getOrmModel必须synchronized（统一 this→CHM 的锁顺序）");

        assertTrue(Modifier.isVolatile(field(InMemoryCodeCache.class, "mergedStore").getModifiers()),
                "mergedStore必须volatile");
        assertTrue(Modifier.isVolatile(field(InMemoryCodeCache.class, "dynResourceStore").getModifiers()),
                "dynResourceStore必须volatile");
    }

    static Field field(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /**
     * nop.dyn.max-biz-objects 上限必须在 addModule 入口生效
     */
    @Test
    public void testMaxBizObjectsEnforced() {
        RecordingHook hook = new RecordingHook();
        // 默认上限1000，载入1001个bizObj必须被拒绝
        for (int i = 0; i < 1001; i++) {
            hook.loadResult.put("Obj" + i, new GraphQLBizModel("Obj" + i));
        }
        InMemoryCodeCache cache = new InMemoryCodeCache(null, "/nop/templates/dyn-gen", hook);

        ModuleModel module = new ModuleModel();
        module.setModuleId("app/demo");

        NopException ex = assertThrows(NopException.class, () -> cache.addModule(module, false));
        assertEquals(ERR_DYN_MAX_BIZ_OBJECTS_EXCEED.getErrorCode(), ex.getErrorCode());

        // 上限之内正常放行（这里只验证不超限时不抛上限错误，生成本身由集成测试覆盖）
        hook.loadResult.clear();
        hook.loadResult.put("Obj1", new GraphQLBizModel("Obj1"));
        try {
            cache.addModule(module, false);
        } catch (NopException e) {
            // 模板生成依赖VFS环境，单测环境下失败可以接受，但不能是上限错误
            if (ERR_DYN_MAX_BIZ_OBJECTS_EXCEED.getErrorCode().equals(e.getErrorCode()))
                throw e;
        }
    }

    /**
     * genBizObjFiles 单对象增长路径同样受上限约束
     */
    @Test
    public void testMaxBizObjectsEnforcedOnSingleAdd() throws Exception {
        RecordingHook hook = new RecordingHook();
        InMemoryCodeCache cache = new InMemoryCodeCache(null, "/nop/templates/dyn-gen", hook);

        // 反射预置1000个已注册bizObj，再新增1个应超限
        Map<String, GraphQLBizModel> bizModels =
                (Map<String, GraphQLBizModel>) field(InMemoryCodeCache.class, "bizModels").get(cache);
        for (int i = 0; i < 1000; i++) {
            bizModels.put("Obj" + i, new GraphQLBizModel("Obj" + i));
        }

        ModuleModel module = new ModuleModel();
        module.setModuleId("app/demo");
        Map<String, ModuleModel> enabled =
                (Map<String, ModuleModel>) field(InMemoryCodeCache.class, "enabledModules").get(cache);
        enabled.put("app/demo", module);

        GraphQLBizModel bizModel = new GraphQLBizModel("NewObj");
        bizModel.setModuleId("app/demo");

        NopException ex = assertThrows(NopException.class, () -> cache.genBizObjFiles(false, bizModel));
        assertEquals(ERR_DYN_MAX_BIZ_OBJECTS_EXCEED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 租户缓存清理必须执行租户 InMemoryCodeCache 的 clear()（触发 on-unload 钩子），
     * 与共享 codeCache 的清理语义一致，而不是仅从 map 移除条目
     */
    @Test
    public void testClearForTenantRunsCacheClear() throws Exception {
        DynCodeGen codeGen = new DynCodeGen();

        AtomicInteger cleared = new AtomicInteger();
        InMemoryCodeCache tenantCacheEntry = new InMemoryCodeCache("t1", "/nop/templates/dyn-gen", new RecordingHook()) {
            @Override
            public synchronized void clear() {
                cleared.incrementAndGet();
            }
        };

        ICache<String, AtomicReference<InMemoryCodeCache>> tenantCache =
                (ICache<String, AtomicReference<InMemoryCodeCache>>) field(DynCodeGen.class, "tenantCache").get(codeGen);
        tenantCache.put("t1", new AtomicReference<>(tenantCacheEntry));

        codeGen.clearForTenant("t1");
        assertEquals(1, cleared.get(), "clearForTenant必须执行租户cache的clear()以触发on-unload钩子");
        assertNull(tenantCache.getIfPresent("t1"), "租户条目必须被移除");

        // 幂等：不存在的租户再次清理不报错
        codeGen.clearForTenant("t1");
        assertEquals(1, cleared.get());
    }
}
