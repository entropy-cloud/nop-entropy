package io.nop.biz.service;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.biz.api.IBizActionModel;
import io.nop.biz.api.IBizModel;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static io.nop.biz.BizErrors.ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * callActionAsync对未知action名与无xbiz的纯Java biz model必须抛携带bizObjName/actionName的
 * 语义化异常。修复前actionModel为null时在getWorkExecutorBean处抛无上下文的NPE。
 */
public class TestBizActionService {

    @Test
    public void testUnknownActionThrowsBizError() {
        // getAction对未知的action名返回null
        IBizModel bizModel = fakeBizModel("doFindPage");
        BizActionService service = newService(fakeActor("TestObj", bizModel, null));

        NopException ex = assertThrows(NopException.class,
                () -> service.callActionAsync("TestObj", "typoAction", new ApiRequest<>()));
        assertEquals(ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION.getErrorCode(), ex.getErrorCode());
        assertEquals("TestObj", ex.getParam("bizObjName"));
        assertEquals("typoAction", ex.getParam("actionName"));
    }

    @Test
    public void testNoBizModelThrowsBizError() {
        // 纯Java注解注册的biz model没有xbiz模型（getBizModel()==null）
        BizActionService service = newService(fakeActor("JavaOnlyObj", null, null));

        NopException ex = assertThrows(NopException.class,
                () -> service.callActionAsync("JavaOnlyObj", "someAction", new ApiRequest<>()));
        assertEquals(ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION.getErrorCode(), ex.getErrorCode());
        assertEquals("JavaOnlyObj", ex.getParam("bizObjName"));
        assertEquals("someAction", ex.getParam("actionName"));
    }

    // ==================== fixture ====================

    static BizActionService newService(IBizObject actor) {
        BizActionService service = new BizActionService();
        service.setBizObjManager((IBizObjectManager) Proxy.newProxyInstance(
                TestBizActionService.class.getClassLoader(), new Class[]{IBizObjectManager.class},
                (p, m, a) -> "getBizObject".equals(m.getName()) ? actor : defaultValue(m.getReturnType())));
        service.setBeanContainer(fakeBeanContainer(null));
        return service;
    }

    /**
     * @param knownAction 唯一已知的action名，getAction对其他名字返回null
     */
    static IBizModel fakeBizModel(String knownAction) {
        return (IBizModel) Proxy.newProxyInstance(
                TestBizActionService.class.getClassLoader(), new Class[]{IBizModel.class},
                (p, m, a) -> {
                    if ("getAction".equals(m.getName()) && knownAction.equals(a[0]))
                        return actionModel(knownAction);
                    if ("getActions".equals(m.getName()))
                        return java.util.Collections.emptyList();
                    return defaultValue(m.getReturnType());
                });
    }

    static IBizActionModel actionModel(String name) {
        return (IBizActionModel) Proxy.newProxyInstance(
                TestBizActionService.class.getClassLoader(), new Class[]{IBizActionModel.class},
                (p, m, a) -> {
                    if ("getName".equals(m.getName()))
                        return name;
                    if ("isBizSequential".equals(m.getName()))
                        return false;
                    return defaultValue(m.getReturnType());
                });
    }

    /**
     * @param bizModel getBizModel()的返回值，null模拟纯Java biz model
     * @param action   getActionModel()的返回值
     */
    static IBizObject fakeActor(String bizObjName, IBizModel bizModel, IBizActionModel action) {
        return (IBizObject) Proxy.newProxyInstance(
                TestBizActionService.class.getClassLoader(), new Class[]{IBizObject.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getBizObjName":
                            return bizObjName;
                        case "getBizModel":
                            return bizModel;
                        case "getActionModel":
                            return action;
                        case "invoke":
                            return null;
                        default:
                            return defaultValue(m.getReturnType());
                    }
                });
    }

    static IBeanContainer fakeBeanContainer(Object bean) {
        return (IBeanContainer) Proxy.newProxyInstance(
                TestBizActionService.class.getClassLoader(), new Class[]{IBeanContainer.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()))
                        return bean;
                    return defaultValue(m.getReturnType());
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
}
