/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.BizSubscription;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.mfa.MfaRequired;
import io.nop.auth.api.mfa.MfaRequiredMeta;
import io.nop.graphql.core.GraphQLErrors;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W12-impl Phase 1：@MfaRequired 注解元数据传播 + 构建期约束校验（框架层单测）。
 * <ul>
 *   <li>builder 读取注解 → GraphQLFieldDefinition.mfaRequiredMeta。</li>
 *   <li>两约束组合构建报错（fail-fast，非运行期忽略）：@MfaRequired + @BizSubscription 拒绝；
 *       @MfaRequired + @Auth(publicAccess=true) 拒绝。</li>
 *   <li>元数据拷贝触点：deepClone 与 GraphQLObjectDefinition.mergeField 两分支（replace=true/false）
 *       均不丢失 mfaRequiredMeta。</li>
 * </ul>
 */
public class TestMfaRequiredMetadata {

    @BizModel("MfaMetaBiz")
    public static class MfaMetaBizModel {
        @BizMutation
        @MfaRequired
        public String sensitiveAction(@Name("id") String id) {
            return id;
        }

        @BizQuery
        public String plainAction(@Name("id") String id) {
            return id;
        }
    }

    @BizModel("MfaSubBiz")
    public static class MfaSubscriptionBizModel {
        @BizSubscription
        @MfaRequired
        public Flow.Publisher<String> subscribeSensitive(@Name("id") String id) {
            return null;
        }
    }

    @BizModel("MfaPublicBiz")
    public static class MfaPublicBizModel {
        @BizMutation
        @MfaRequired
        @Auth(publicAccess = true)
        public String publicSensitive(@Name("id") String id) {
            return id;
        }
    }

    /** D5-F3：@MfaRequired 误标 @BizAction（内部动作不经 executor 操作级检查点）——构建期 fail-fast。 */
    @BizModel("MfaActionBiz")
    public static class MfaActionBizModel {
        @BizAction
        @MfaRequired
        public String internalSensitive(@Name("id") String id) {
            return id;
        }
    }

    /** D5-F3：@MfaRequired 误标 @BizLoader（字段装载器无操作入口）——构建期 fail-fast。 */
    @BizModel("MfaLoaderBiz")
    public static class MfaLoaderBizModel {
        @BizLoader
        @MfaRequired
        public String sensitiveField(@ContextSource String source) {
            return source;
        }
    }

    private GraphQLBizModel buildBizModel(Object bean) {
        return ReflectionBizModelBuilder.INSTANCE.build(bean, new TypeRegistry(), new GraphQLBizModels());
    }

    @Test
    public void testBuilderSetsMfaRequiredMeta() {
        GraphQLBizModel bizModel = buildBizModel(new MfaMetaBizModel());
        GraphQLFieldDefinition field = bizModel.getMutationAction("sensitiveAction");
        assertNotNull(field, "mutation action must be built");
        assertSame(MfaRequiredMeta.INSTANCE, field.getMfaRequiredMeta(),
                "@MfaRequired method must carry mfaRequiredMeta");

        // 未标注方法零介入
        GraphQLFieldDefinition plain = bizModel.getQueryAction("plainAction");
        assertNotNull(plain);
        assertNull(plain.getMfaRequiredMeta(), "plain method must not carry mfaRequiredMeta");
    }

    @Test
    public void testSubscriptionCombinationRejectedAtBuildTime() {
        NopException e = assertThrows(NopException.class,
                () -> buildBizModel(new MfaSubscriptionBizModel()));
        assertEquals(GraphQLErrors.ERR_GRAPHQL_MFA_REQUIRED_NOT_ALLOWED_ON_SUBSCRIPTION.getErrorCode(),
                e.getErrorCode(), "@MfaRequired + @BizSubscription must fail at build time (fail-fast)");
    }

    @Test
    public void testPublicAccessCombinationRejectedAtBuildTime() {
        NopException e = assertThrows(NopException.class,
                () -> buildBizModel(new MfaPublicBizModel()));
        assertEquals(GraphQLErrors.ERR_GRAPHQL_MFA_REQUIRED_NOT_ALLOWED_FOR_PUBLIC_ACCESS.getErrorCode(),
                e.getErrorCode(), "@MfaRequired + @Auth(publicAccess=true) must fail at build time (fail-fast)");
    }

    @Test
    public void testBizActionCombinationRejectedAtBuildTime() {
        // D5-F3：@MfaRequired + @BizAction 误标原被静默忽略（fail-open 错觉）——构建期显式拒绝
        NopException e = assertThrows(NopException.class,
                () -> buildBizModel(new MfaActionBizModel()));
        assertEquals(GraphQLErrors.ERR_GRAPHQL_MFA_REQUIRED_NOT_ALLOWED_ON_BIZ_ACTION.getErrorCode(),
                e.getErrorCode(), "@MfaRequired + @BizAction must fail at build time (fail-fast)");
    }

    @Test
    public void testBizLoaderCombinationRejectedAtBuildTime() {
        // D5-F3：@MfaRequired + @BizLoader 误标原被静默忽略——构建期显式拒绝
        NopException e = assertThrows(NopException.class,
                () -> buildBizModel(new MfaLoaderBizModel()));
        assertEquals(GraphQLErrors.ERR_GRAPHQL_MFA_REQUIRED_NOT_ALLOWED_ON_BIZ_LOADER.getErrorCode(),
                e.getErrorCode(), "@MfaRequired + @BizLoader must fail at build time (fail-fast)");
    }

    @Test
    public void testDeepCloneKeepsMfaRequiredMeta() {
        GraphQLBizModel bizModel = buildBizModel(new MfaMetaBizModel());
        GraphQLFieldDefinition field = bizModel.getMutationAction("sensitiveAction");
        assertNotNull(field.getMfaRequiredMeta());

        // deepClone 路径（BizObjectManager.getObjDef 等先例）不丢失元数据
        GraphQLFieldDefinition cloned = field.deepClone();
        assertSame(MfaRequiredMeta.INSTANCE, cloned.getMfaRequiredMeta(),
                "deepClone must carry mfaRequiredMeta (live gap fixed for new meta)");
    }

    /** 手工构造 field（避免共享 AST type 节点的 re-parent 限制，聚焦 meta 拷贝分支）。 */
    private GraphQLFieldDefinition field(String name, boolean withMeta) {
        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setName(name);
        if (withMeta)
            field.setMfaRequiredMeta(MfaRequiredMeta.INSTANCE);
        return field;
    }

    @Test
    public void testMergeFieldKeepsMfaRequiredMetaBothBranches() {
        // 分支一：replace=true（old 已存在，新定义带 meta → 覆盖拷贝）
        GraphQLObjectDefinition replaceTarget = new GraphQLObjectDefinition();
        replaceTarget.setName("Mutation");
        replaceTarget.addField(field("Op__a", false));
        replaceTarget.mergeField(field("Op__a", true), true);
        assertSame(MfaRequiredMeta.INSTANCE, replaceTarget.getField("Op__a").getMfaRequiredMeta(),
                "mergeField(replace=true) must copy mfaRequiredMeta onto existing field");

        // 分支二：replace=false（old 已存在且无 meta → 增量拷贝）
        GraphQLObjectDefinition keepTarget = new GraphQLObjectDefinition();
        keepTarget.setName("Mutation");
        keepTarget.addField(field("Op__a", false));
        keepTarget.mergeField(field("Op__a", true), false);
        assertSame(MfaRequiredMeta.INSTANCE, keepTarget.getField("Op__a").getMfaRequiredMeta(),
                "mergeField(replace=false) must copy mfaRequiredMeta when old field has none");

        // 分支三：old 不存在 → 整字段放入（meta 随字段保留）
        GraphQLObjectDefinition newTarget = new GraphQLObjectDefinition();
        newTarget.setName("Mutation");
        newTarget.mergeField(field("Op__a", true), false);
        assertSame(MfaRequiredMeta.INSTANCE, newTarget.getField("Op__a").getMfaRequiredMeta());
    }

    @Test
    public void testMergeDoesNotClearExistingMeta() {
        // old 有 meta、新 field 无 meta → replace=true 也不清除（null 不覆盖）
        GraphQLObjectDefinition target = new GraphQLObjectDefinition();
        target.setName("Mutation");
        target.addField(field("Op__a", true));
        target.mergeField(field("Op__a", false), true);
        assertSame(MfaRequiredMeta.INSTANCE, target.getField("Op__a").getMfaRequiredMeta(),
                "merge must not clear existing mfaRequiredMeta");
    }
}
