package io.nop.rule.api;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.exceptions.ErrorCode;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试：RuleService 生成的服务接口契约 —— biz 对象名、同步/异步方法对上的
 * @BizMutation/@BizQuery 操作名与 REST 路径一致，以及错误码定义的语义。
 */
public class TestRuleServiceContract {

    @Test
    public void testBizModelNameIsRuleService() {
        BizModel bizModel = RuleService.class.getAnnotation(BizModel.class);
        assertNotNull(bizModel, "RuleService 必须标注 @BizModel");
        assertEquals("RuleService", bizModel.value());
    }

    @Test
    public void testExecuteRuleMethodsShareMutationNameAndPath() {
        for (Method method : RuleService.class.getMethods()) {
            if (!method.getName().startsWith("executeRule"))
                continue;
            BizMutation mutation = method.getAnnotation(BizMutation.class);
            assertNotNull(mutation, method.getName() + " 必须标注 @BizMutation");
            assertEquals("executeRule", mutation.value(),
                    method.getName() + " 的 biz 操作名必须统一为 executeRule");
            Path path = method.getAnnotation(Path.class);
            assertNotNull(path, method.getName() + " 必须标注 REST 路径");
            assertEquals("/r/RuleService__executeRule", path.value(),
                    method.getName() + " 的 REST 路径必须与 biz 操作名对应");
        }
    }

    @Test
    public void testGetRuleMetaMethodsShareQueryNameAndPath() {
        for (Method method : RuleService.class.getMethods()) {
            if (!method.getName().startsWith("getRuleMeta"))
                continue;
            BizQuery query = method.getAnnotation(BizQuery.class);
            assertNotNull(query, method.getName() + " 必须标注 @BizQuery");
            assertEquals("getRuleMeta", query.value(),
                    method.getName() + " 的 biz 操作名必须统一为 getRuleMeta");
            Path path = method.getAnnotation(Path.class);
            assertNotNull(path);
            assertEquals("/r/RuleService__getRuleMeta", path.value(),
                    method.getName() + " 的 REST 路径必须与 biz 操作名对应");
        }
    }

    @Test
    public void testErrorCodesFollowRuleNamespaceAndCarryArgs() {
        ErrorCode nameNotUnique = RuleApiErrors.ERR_RULE_NAME_NOT_UNIQUE;
        assertEquals("nop.err.rule.rule-name-not-unique", nameNotUnique.getErrorCode(),
                "规则错误码必须落在 nop.err.rule 命名空间下");
        assertTrue(nameNotUnique.getDescription().contains("{ruleName}"),
                "规则名不唯一错误模板必须携带 ruleName 占位符");
        assertEquals(RuleApiErrors.ARG_RULE_NAME, "ruleName");
        assertEquals(RuleApiErrors.ARG_LOC2, "loc2");
    }
}
