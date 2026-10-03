/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.sys.dao.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归覆盖 wi7#2（plan 2306 项 19）：SysDictLoader.loadDict 不得忽略调用方传入的
 * locale 参数——返回的 DictBean.locale 必须忠实反映请求，null 时回退平台默认 locale。
 */
public class TestSysDictLoaderLocale {

    @SuppressWarnings("unchecked")
    private static <T> T stubDao(Class<?> entityClass) {
        return (T) Proxy.newProxyInstance(TestSysDictLoaderLocale.class.getClassLoader(),
                new Class[]{IEntityDao.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("findAllByQuery"))
                            return Collections.emptyList();
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class)
                            return false;
                        if (rt.isPrimitive())
                            return 0;
                        return null;
                    }
                });
    }

    private static IDaoProvider stubDaoProvider() {
        return (IDaoProvider) Proxy.newProxyInstance(TestSysDictLoaderLocale.class.getClassLoader(),
                new Class[]{IDaoProvider.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("daoFor"))
                            return stubDao((Class<?>) args[0]);
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class)
                            return false;
                        if (rt.isPrimitive())
                            return 0;
                        return null;
                    }
                });
    }

    @Test
    public void testLoadDictHonorsLocaleParam() {
        SysDictLoader loader = new SysDictLoader();
        loader.daoProvider = stubDaoProvider();

        DictBean bean = loader.loadDict("en-US", "sys:demo", null);
        assertEquals("en-US", bean.getLocale(), "loadDict 必须使用调用方传入的 locale");

        DictBean bean2 = loader.loadDict(null, "sys:demo", null);
        assertEquals(I18nMessageManager.instance().getDefaultLocale(), bean2.getLocale(),
                "locale 为 null 时回退平台默认");
    }
}
