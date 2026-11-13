/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.app.SimsCollege;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C2-007（plan 2553 裁定 A，R1-Major-1 原语勘正）钉：换代旧模型 close()——
 * 末 session decRef 归零延迟销毁。
 *
 * <p>路径：真实测试布景（AbstractOrmTestCase 缺省构建 DefaultOrmModelProvider，
 * 经 SessionFactoryImpl.reloadModel() → provider.clearCache() 触发换代）。
 * 布景：先开 session（tryIncRefIfOpen 持有旧模型引用），再 reloadModel——旧模型
 * close() 只置 needClose、refCount>0 延迟销毁（拒新引用但服务在途 session）；
 * 末个在途 session close 时 decRef 归零触发 doClose。</p>
 *
 * <p>断言面（needClose/销毁经公开 API 可观察，refCount 为 private final 字段，
 * 反射读取）：(a) 换代后新模型可见、旧模型不再由 provider 返回；(b) 换代瞬间旧模型
 * 拒绝新引用（tryIncRefIfOpen=false）但 refCount==1（延迟销毁窗口，缺陷态立即销毁
 * 或永不置 needClose → 红）；(c) 末 session 关闭后 refCount==0；(d) 新模型可正常
 * 解析 persister、可再开 session。</p>
 */
public class TestModelProviderDelayedClose extends AbstractOrmTestCase {

    private static int refCountOf(LoadedOrmModel model) throws Exception {
        Field field = LoadedOrmModel.class.getDeclaredField("refCount");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(model)).get();
    }

    @Test
    public void testClearCacheOldModelDelayedDestroyAfterLastSessionClose() throws Exception {
        SessionFactoryImpl impl = (SessionFactoryImpl) sessionFactory;

        ILoadedOrmModel oldModel = impl.getLoadedOrmModel();

        // 在途 session 持有旧模型引用（tryIncRefIfOpen -> refCount=1）
        IOrmSession session = impl.openSession(false);

        // 换代：provider.clearCache() 对旧模型调 close()（needClose 标记 + 延迟销毁）
        ILoadedOrmModel newModel = impl.reloadModel();
        assertNotSame(oldModel, newModel, "换代后必须发布新模型");
        assertSame(newModel, impl.getLoadedOrmModel(), "provider 不得继续返回旧模型");

        // 延迟销毁窗口：旧模型拒新引用，但仍有在途引用（refCount==1），未销毁
        LoadedOrmModel oldLoaded = (LoadedOrmModel) oldModel;
        assertFalse(oldModel.tryIncRefIfOpen(), "已 close 的旧模型必须拒绝新引用（needClose 置位）");
        assertEquals(1, refCountOf(oldLoaded), "在途 session 存活期间旧模型不得销毁（refCount 保持 1）");

        // 末 session 关闭：decRef 归零 -> doClose
        session.close();
        assertEquals(0, refCountOf(oldLoaded), "末 session decRef 后旧模型引用归零（延迟销毁触发）");

        // 新模型功能完好：persister 可解析，可再开 session
        assertNotNull(newModel.getEntityPersister(SimsCollege.class.getName()), "新模型 persister 可用");
        IOrmSession reopened = impl.openSession(false);
        reopened.close();
    }
}
