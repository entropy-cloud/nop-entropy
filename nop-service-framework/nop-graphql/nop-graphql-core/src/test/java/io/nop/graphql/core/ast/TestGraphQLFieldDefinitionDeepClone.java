/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.auth.api.mfa.MfaRequiredMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * deepClone必须随克隆传播审批/MFA元数据与operationName：修复前mfaRequiredMeta被拷贝而
 * makerCheckerMeta/operationName被遗漏（注释却声称"对齐makerCheckerMeta先例"），
 * 克隆体一旦参与执行会静默丢失maker-checker审批拦截。
 */
public class TestGraphQLFieldDefinitionDeepClone {
    @Test
    public void testDeepCloneCopiesRuntimeAssemblyFields() {
        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setName("myAction");

        MfaRequiredMeta mfa = MfaRequiredMeta.INSTANCE;
        field.setMfaRequiredMeta(mfa);

        BizMakerCheckerMeta makerChecker = new BizMakerCheckerMeta("tryMyAction", "cancelMyAction");
        field.setMakerCheckerMeta(makerChecker);
        field.setOperationName("MyObj__myAction");

        GraphQLFieldDefinition clone = field.deepClone();

        assertSame(mfa, clone.getMfaRequiredMeta());
        assertSame(makerChecker, clone.getMakerCheckerMeta(), "maker-checker meta must survive deepClone");
        assertEquals("MyObj__myAction", clone.getOperationName(), "operationName must survive deepClone");
        assertNull(clone.getFetcher(), "execution bindings (fetcher/tryAction/serviceAction) are deliberately not cloned");
        assertNull(clone.getTryAction());
        assertNull(clone.getServiceAction());
    }
}
