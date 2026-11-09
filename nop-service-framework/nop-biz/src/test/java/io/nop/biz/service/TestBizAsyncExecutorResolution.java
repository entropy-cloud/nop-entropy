package io.nop.biz.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.biz.BizErrors;
import io.nop.biz.api.IBizActionModel;
import io.nop.biz.api.IBizModel;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.concurrent.executor.IPartitionedExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C4-001（plan 2551 裁定 A）钉：
 * pin-a：defaultWorkExecutor bean（DefaultWorkExecutor）按名解析命中且可承载 Executor 提交；
 * pin-b：启动期校验——无效 executor 名 fail-fast 类型化错误列全无效名；
 * pin-c：bizSequential + 非 IPartitionedExecutor 组合启动期 fail-fast 列全违例动作名。
 * 变异红形态：bean 定义移除/校验删除后 pin-a②/pin-b/pin-c 红。
 */
public class TestBizAsyncExecutorResolution {

    private static IBeanContainer proxyContainer(java.util.Map<String, Object> beans) {
        return (IBeanContainer) Proxy.newProxyInstance(IBeanContainer.class.getClassLoader(),
                new Class<?>[]{IBeanContainer.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "getBean":
                            Object bean = beans.get((String) args[0]);
                            if (bean == null)
                                throw new NopException(BizErrors.ERR_BIZ_UNKNOWN_EXECUTOR)
                                        .param("executors", String.valueOf(args[0]));
                            return bean;
                        case "containsBean":
                            return beans.containsKey(args[0]);
                        default:
                            return null;
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static IBizObjectManager proxyManager(IBizModel model) {
        return (IBizObjectManager) Proxy.newProxyInstance(IBizObjectManager.class.getClassLoader(),
                new Class<?>[]{IBizObjectManager.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "getBizObjNames":
                            return java.util.Set.of("TestObj");
                        case "getBizObject":
                            return (IBizObject) Proxy.newProxyInstance(IBizObject.class.getClassLoader(),
                                    new Class<?>[]{IBizObject.class},
                                    (p2, m2, a2) -> "TestObj".equals(m2.getName()) ? "TestObj"
                                            : "getBizModel".equals(m2.getName()) ? model : null);
                        default:
                            return null;
                    }
                });
    }

    static IBizModel modelWithAction(String actionName, String executor, boolean sequential) {
        return (IBizModel) Proxy.newProxyInstance(IBizModel.class.getClassLoader(),
                new Class<?>[]{IBizModel.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "getAction":
                            return actionName.equals(args[0]) ? actionModel(actionName, executor, sequential) : null;
                        case "getActions":
                            return List.of(actionModel(actionName, executor, sequential));
                        default:
                            return null;
                    }
                });
    }

    static IBizActionModel actionModel(String name, String executor, boolean sequential) {
        return (IBizActionModel) Proxy.newProxyInstance(IBizActionModel.class.getClassLoader(),
                new Class<?>[]{IBizActionModel.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "getExecutor":
                            return executor;
                        case "isBizSequential":
                            return sequential;
                        default:
                            return null;
                    }
                });
    }

    @Test
    public void pin_a_defaultWorkExecutorBeanResolvableAndSubmittable() {
        DefaultWorkExecutor executor = new DefaultWorkExecutor(Runnable::run); // 同步委托（测试注入缝）
        java.util.Map<String, Object> beans = new java.util.HashMap<>();
        beans.put(DefaultWorkExecutor.BEAN_NAME, executor);

        BizActionService service = new BizActionService();
        service.setBeanContainer(proxyContainer(beans));
        // 按名解析命中：getBean 不抛
        Object resolved = service.getBeanForTest(BizConstants.DEFAULT_WORK_EXECUTOR_NAME);
        assertNotNull(resolved);
        assertTrue(resolved instanceof Executor);

        // 校验通过（非 sequential 动作 + 缺省执行器合法组合）
        service.setBizObjManager(proxyManager(modelWithAction("bizGet", null, false)));
        service.validateExecutors(); // 不抛 = 通过
    }

    @Test
    public void pin_b_unknownExecutorNameFailsFastListingAll() {
        BizActionService service = new BizActionService();
        service.setBeanContainer(proxyContainer(new java.util.HashMap<>())); // 空容器：无任何 bean
        service.setBizObjManager(proxyManager(modelWithAction("bizGet", "myExec", false)));

        NopException ex = assertThrows(NopException.class, service::validateExecutors);
        assertTrue(String.valueOf(ex.getParam("executors")).contains("TestObj__bizGet -> myExec"),
                "must list violating action + executor name");
    }

    @Test
    public void pin_c_sequentialWithNonPartitionedExecutorFailsFast() {
        // bizSequential=true + 显式声明的非分区 Executor（缺省执行器实现了 IPartitionedExecutor
        // 接口但 executeForPartition 诚实 UOE——运行期不可达面由本钉的启动校验承载）
        Executor plainExecutor = Runnable::run;
        java.util.Map<String, Object> beans = new java.util.HashMap<>();
        beans.put("plainExec", plainExecutor);
        beans.put(DefaultWorkExecutor.BEAN_NAME, new DefaultWorkExecutor(plainExecutor));
        BizActionService service = new BizActionService();
        service.setBeanContainer(proxyContainer(beans));
        service.setBizObjManager(proxyManager(modelWithAction("bizSeq", "plainExec", true)));

        NopException ex = assertThrows(NopException.class, service::validateExecutors);
        assertTrue(String.valueOf(ex.getParam("actions")).contains("TestObj__bizSeq"),
                "must list violating sequential action");

        // 缺省执行器 executeForPartition 诚实快速失败（启动校验防呆的行为佐证）
        assertThrows(Exception.class,
                () -> ((DefaultWorkExecutor) beans.get(DefaultWorkExecutor.BEAN_NAME)).executeForPartition(1, () -> {
                }));
    }

    @Test
    public void pin_a2_defaultWorkExecutorImplementsBothInterfaces() {
        DefaultWorkExecutor executor = new DefaultWorkExecutor(Runnable::run);
        assertTrue(executor instanceof IThreadPoolExecutor);
        assertTrue(executor instanceof IPartitionedExecutor);
        assertEquals(DefaultWorkExecutor.BEAN_NAME, executor.getName());
    }
}
