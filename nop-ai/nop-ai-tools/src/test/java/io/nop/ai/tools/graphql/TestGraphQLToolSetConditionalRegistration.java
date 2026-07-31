package io.nop.ai.tools.graphql;

import io.nop.ai.core.api.tool.IAiChatToolSet;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.ResourceHelper;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.ioc.model.BeanConditionModel;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeansModel;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-MA1-016: nopGraphQLToolSet bean 条件注册验证。
 * <p>
 * 模型级验证（xdef 语法 + BeanConditionModel 语义与仓库既有实例一致性）+ 容器接线验证
 * （有 nopGraphQLEngine → 注册；无 → 跳过且装配不失败）。
 * <p>
 * 容器说明：app 容器自动加载各模块 autoconfig 指定的 beans（nop-biz → biz-defaults
 * 提供 nopGraphQLEngine；nop-ai-core → ai-defaults，其 ChatServiceImpl 按类型装配
 * IHttpClient，由 test 依赖 nop-http-client-jdk 提供）。ai-tools-defaults 不在
 * autoconfig 中，经 testBeansFile 追加到 app 容器。
 */
@NopTestConfig(testBeansFile = "/nop/ai/beans/ai-tools-defaults.beans.xml")
public class TestGraphQLToolSetConditionalRegistration extends JunitBaseTestCase {

    private static final String AI_TOOLS_BEANS = "/nop/ai/beans/ai-tools-defaults.beans.xml";

    private static BeansModel loadBeansModel(String path) {
        return (BeansModel) new DslModelParser("/nop/schema/beans.xdef").parseFromVirtualPath(path);
    }

    private static BeanModel findBean(BeansModel beansModel, String id) {
        List<BeanModel> beans = beansModel.getBeans();
        for (BeanModel bean : beans) {
            if (id.equals(bean.getId()))
                return bean;
        }
        return null;
    }

    @Test
    public void testBeansXmlParsesWithCondition() {
        // xdef 解析即语法验证：<ioc:condition><on-bean> 是 beans.xdef BeanConditionModel 的子元素
        BeansModel model = loadBeansModel(AI_TOOLS_BEANS);
        BeanModel toolSet = findBean(model, "nopGraphQLToolSet");
        assertNotNull(toolSet, "nopGraphQLToolSet bean must exist");

        BeanConditionModel condition = toolSet.getIocCondition();
        assertNotNull(condition, "nopGraphQLToolSet must carry an ioc:condition");
        assertNotNull(condition.getOnBean(), "condition must declare on-bean");
        assertTrue(condition.getOnBean().contains("nopGraphQLEngine"),
                "on-bean must reference the bean id nopGraphQLEngine (not the interface IGraphQLEngine)");
        assertNull(condition.getMissingBean(), "condition must not use missing-bean");
    }

    @Test
    public void testOnBeanConditionConsistentWithRepoInstances() {
        // 仓库既有实例（biz-defaults nopCrudBizInitializer on-bean nopDaoProvider）
        BeansModel bizModel = loadBeansModel("/nop/biz/beans/biz-defaults.beans.xml");
        BeanModel crudInitializer = findBean(bizModel, "nopCrudBizInitializer");
        assertNotNull(crudInitializer);
        BeanConditionModel crudCondition = crudInitializer.getIocCondition();
        assertNotNull(crudCondition);
        assertTrue(crudCondition.getOnBean().contains("nopDaoProvider"));

        // 仓库既有实例（biz-defaults nopObjDictLoader on-bean nopOrmTemplate）
        BeanModel objDictLoader = findBean(bizModel, "nopObjDictLoader");
        assertNotNull(objDictLoader);
        BeanConditionModel dictCondition = objDictLoader.getIocCondition();
        assertNotNull(dictCondition);
        assertTrue(dictCondition.getOnBean().contains("nopOrmTemplate"));

        // 一致性核对：本 bean 的条件与仓库实例同为 BeanConditionModel 结构（同一 xdef 语义面）
        // （rpc-cluster-defaults 的 missing-bean 实例不在本模块 test classpath，无法解析断言；
        //  其 on-bean/missing-bean 语义已由容器级测试验证——BeanConditionEvaluator 行为）
        BeanConditionModel ours = findBean(loadBeansModel(AI_TOOLS_BEANS), "nopGraphQLToolSet").getIocCondition();
        assertNotNull(ours.getOnBean());
        assertEquals(1, ours.getOnBean().size());
    }

    @Test
    public void testContainerRegistersToolSetWithEngine() {
        // 有 nopGraphQLEngine（biz-defaults 注册）→ 条件满足 → bean 注册且可注入
        assertNotNull(engine);
        assertNotNull(toolSet);
        assertTrue(toolSet.getToolNames().isEmpty(),
                "default graphql-tool-names is empty so the tool set has no functions");
    }

    @Test
    public void testContainerWithoutEngineSkipsToolSet() {
        // 防御性：自定义仅含 ai-tools-defaults.beans.xml 的装配集（无 nopGraphQLEngine）
        // → 容器装配不失败，nopGraphQLToolSet 被条件跳过，其他 bean 正常注册
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(ResourceHelper.resolve(AI_TOOLS_BEANS));
        IBeanContainerImplementor container = builder.build("graphql-toolset-no-engine");
        container.start();
        try {
            assertFalse(container.containsBean("nopGraphQLToolSet"),
                    "without nopGraphQLEngine the conditional bean must not be registered");
            assertTrue(container.containsBean("nopFileToolBizModel"),
                    "unconditional beans must still be registered");
            assertTrue(container.containsBean("nopSequentialThinkingBizModel"));
        } finally {
            container.stop();
        }
    }

    @Inject
    @Named("nopGraphQLToolSet")
    IAiChatToolSet toolSet;

    @Inject
    IGraphQLEngine engine;
}
