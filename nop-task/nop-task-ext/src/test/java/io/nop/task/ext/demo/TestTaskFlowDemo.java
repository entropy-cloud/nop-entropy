package io.nop.task.ext.demo;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.execute.RuleManager;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTaskFlowDemo {

    protected ITaskFlowManager taskFlowManager;
    protected IRuleManager ruleManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        // taskFlowManager和ruleManager都是单例对象，应该由IoC容器统一管理
        ITaskFlowManager taskManager = new TaskFlowManagerImpl();
        this.taskFlowManager = taskManager;

        RuleManager ruleManager = new RuleManager();
        this.ruleManager = ruleManager;
    }

    /**
     * 与solon-flow的对比
     */
    @Test
    public void testDiscount01() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/demo/task/discount-01.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        BookOrder bookOrder = new BookOrder();
        bookOrder.setOriginalPrice(500.0);

        taskRt.setInput("order", bookOrder);

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        assertEquals(400.0, outputs.get("realPrice"));

        assertEquals(400.0, bookOrder.getRealPrice());
    }


    /**
     * 与testDiscount01的区别在于任务使用yaml格式来定义
     */
    @Test
    public void testDiscount01ForYaml() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/demo/task/discount-01.task.yaml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        BookOrder bookOrder = new BookOrder();
        bookOrder.setOriginalPrice(500.0);

        taskRt.setInput("order", bookOrder);

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        assertEquals(400.0, outputs.get("realPrice"));

        assertEquals(400.0, bookOrder.getRealPrice());
    }

    /**
     * NopTask可以使用xml格式来表达，也可以使用Yaml格式来表达
     */
    @Test
    public void testTaskXmlToYaml() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/demo/task/discount-01.task.xml");
        Object model = new DslModelParser().forEditor(true).parseFromResource(resource);
        System.out.println(JsonTool.serializeToYaml(model));
    }

    /**
     * 使用规则模型计算discount
     */
    @Test
    public void testDiscountRuleXml() {
        BookOrder bookOrder = new BookOrder();
        bookOrder.setOriginalPrice(500.0);

        IRuleRuntime ruleRt = ruleManager.newRuleRuntime();
        ruleRt.setInput("order", bookOrder);
        ruleRt.setCollectLogMessage(true);

        IExecutableRule rule = ruleManager.loadRuleFromPath("/nop/demo/rule/discount.rule.xml");
        Map<String, Object> outputs = rule.executeForOutputs(ruleRt);
        assertEquals(100.0, outputs.get("discount"));
        System.out.println(JsonTool.serialize(ruleRt.getLogMessages(), true));
    }

    @Test
    public void testDiscountRuleExcel() {
        BookOrder bookOrder = new BookOrder();
        bookOrder.setOriginalPrice(500.0);

        IRuleRuntime ruleRt = ruleManager.newRuleRuntime();
        ruleRt.setInput("order", bookOrder);

        IExecutableRule rule = ruleManager.loadRuleFromPath("/nop/demo/rule/discount.rule.xlsx");
        Map<String, Object> outputs = rule.executeForOutputs(ruleRt);
        assertEquals(100.0, outputs.get("discount"));
    }

    @Test
    public void testDiscountTaskRule() {
        ITask task = taskFlowManager.loadTaskFromPath("/nop/demo/task/discount-rule.task.xml");
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        BookOrder bookOrder = new BookOrder();
        bookOrder.setOriginalPrice(500.0);

        taskRt.setInput("order", bookOrder);

        Map<String, Object> outputs = task.execute(taskRt).syncGetOutputs();
        assertEquals(400.0, outputs.get("realPrice"));

        assertEquals(400.0, bookOrder.getRealPrice());
    }
}
