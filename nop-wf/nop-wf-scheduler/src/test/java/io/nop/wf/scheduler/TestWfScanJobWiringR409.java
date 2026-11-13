package io.nop.wf.scheduler;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.job.api.config.LocalJobConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.49 R4-09（plan 2542）W-04（1.5-C4-003；C10-010 宿主）装配置信钉。
 *
 * <p>红④：修复前两 job.yaml 的 {@code invoker.bean} 为 {@code wfTaskScanner}，与实际 bean id
 * {@code nopWfTaskScanner}（app-scheduler.beans.xml）失配——启用即每次 fire 抛
 * {@code ERR_JOB_BEAN_NOT_FOUND}，超时升级/提醒能力 100% 失停。本钉以三条腿消费真实解析链
 * 判定（装配断言，禁造宿主面 fire 生命周期断言——FAILED 终态语义归 nop-job-C7-006）。
 */
public class TestWfScanJobWiringR409 extends BaseTestCase {

    private static final String DUE_JOB = "/nop/job/conf/wf-due-task-scan.job.yaml";
    private static final String REMIND_JOB = "/nop/job/conf/wf-remind-task-scan.job.yaml";
    private static final String BEANS_XML = "/nop/wf/beans/app-scheduler.beans.xml";

    @Test
    public void dueJobInvokerBeanResolvesToContainerBean() {
        assertJobWiring(DUE_JOB, "scanDueTasks");
    }

    @Test
    public void remindJobInvokerBeanResolvesToContainerBean() {
        assertJobWiring(REMIND_JOB, "scanRemindTasks");
    }

    private void assertJobWiring(String jobPath, String expectedMethod) {
        // 腿 1：经 nop-job-local 模型类真实解析 job.yaml（LocalJobConfig=生产加载器同型）
        IResource resource = classpathResource("_vfs" + jobPath);
        assertTrue(resource.exists(), "job.yaml should exist: " + jobPath);
        LocalJobConfig jobConfig = JsonTool.loadDeltaBeanFromResource(resource, LocalJobConfig.class);
        assertNotNull(jobConfig.getInvoker(), "invoker should be configured: " + jobPath);

        String invokerBean = jobConfig.getInvoker().getBean();
        String invokerMethod = jobConfig.getInvoker().getMethod();
        assertEquals(expectedMethod, invokerMethod, "invoker.method of " + jobPath);
        assertNotNull(invokerBean, "invoker.bean of " + jobPath);

        // 腿 2：invoker.bean 必须存在于容器 bean id 集（修复前 wfTaskScanner 全仓零命中）
        Set<String> beanIds = loadBeanIds();
        assertTrue(beanIds.contains(invokerBean),
                "invoker.bean '" + invokerBean + "' of " + jobPath + " is not a container bean id " + beanIds);

        // 腿 3：invoker.method 必须是该 bean 对应类上的 public 方法（反射真实可达）
        Class<?> beanClass = resolveBeanClass(invokerBean);
        assertNotNull(beanClass, "cannot resolve class for bean '" + invokerBean + "'");
        Method method = findPublicMethod(beanClass, invokerMethod);
        assertNotNull(method, "bean '" + invokerBean + "' has no public method '" + invokerMethod + "'");
    }

    private Set<String> loadBeanIds() {
        XNode node = XNode.parseFromResource(classpathResource("_vfs" + BEANS_XML));
        Set<String> ids = new LinkedHashSet<>();
        for (XNode bean : beanNodes(node)) {
            String id = bean.attrText("id");
            if (id != null)
                ids.add(id);
        }
        assertTrue(ids.contains("nopWfTaskScanner"),
                "app-scheduler.beans.xml should declare nopWfTaskScanner, actual=" + ids);
        return ids;
    }

    private Class<?> resolveBeanClass(String beanId) {
        XNode node = XNode.parseFromResource(classpathResource("_vfs" + BEANS_XML));
        for (XNode bean : beanNodes(node)) {
            if (!beanId.equals(bean.attrText("id")))
                continue;
            String className = bean.attrText("class");
            if (className == null)
                return null;
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                fail("bean '" + beanId + "' class not found: " + className);
            }
        }
        return null;
    }

    private static List<XNode> beanNodes(XNode root) {
        List<XNode> result = new ArrayList<>();
        for (XNode child : root.getChildren()) {
            if ("bean".equals(child.getTagNameWithoutNs()))
                result.add(child);
        }
        return result;
    }

    private Method findPublicMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
