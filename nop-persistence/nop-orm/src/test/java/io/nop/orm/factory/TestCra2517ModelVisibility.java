/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.core.initialize.CoreInitialization;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.impl.ProxyStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2517 R2-14 红②（C1-003，C10-003 半边）回归钉。
 * <p>volatile/顺序修复为 JMM 发布语义，行为级「缺陷态必红」不可确定性构造——采用结构钉（三点位
 * volatile 反射断言，缺陷态字段非 volatile → 红，删除 volatile 关键字即红——变异论证留痕）
 * + 行为绿钉（clearCache 后新模型可见、旧模型不再返回）。</p>
 */
public class TestCra2517ModelVisibility {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static void assertVolatile(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                clazz.getSimpleName() + "." + fieldName + " 必须为 volatile（裁定②缺陷态非 volatile → 红）");
    }

    @Test
    public void testCra2517_C1_003_modelFieldsAreVolatile() throws Exception {
        assertVolatile(DefaultOrmModelProvider.class, "ormModel");
        assertVolatile(TenantAwareOrmModelProvider.class, "ormModel");
        assertVolatile(LoadedOrmModel.class, "ormInterceptor");
    }

    /**
     * 行为绿钉：clearCache 后 getOrmModel 返回新模型（reloadModel 运维 API 发布可见性，
     * 旧模型不得无限期可见）。
     */
    @Test
    public void testCra2517_C1_003_clearCachePublishesNewModel() throws Exception {
        AtomicInteger loadCount = new AtomicInteger();
        ILoadedOrmModel m1 = ProxyStub.of(ILoadedOrmModel.class, (proxy, method, args) -> null);
        ILoadedOrmModel m2 = ProxyStub.of(ILoadedOrmModel.class, (proxy, method, args) -> null);
        Function<String, ILoadedOrmModel> loader = tenantId -> loadCount.incrementAndGet() == 1 ? m1 : m2;

        TenantAwareOrmModelProvider provider = new TenantAwareOrmModelProvider("cra2517-test", loader);
        // 隔离全量套件中其他测试残留的租户上下文（getOrmModel 在无租户路径返回缺省模型）
        io.nop.api.core.context.BaseContext isolated = new io.nop.api.core.context.BaseContext();
        isolated.executeWithContext(() -> {
            assertSame(m1, provider.getOrmModel(null));

            // reloadModel（clearCache）后必须发布新模型
            provider.clearCache();
            assertNotSame(m1, provider.getOrmModel(null),
                    "clearCache 后旧模型不得继续可见（缺陷态旧引用无限期可见 → 红）");
            assertSame(m2, provider.getOrmModel(null));
            return null;
        });
    }
}
