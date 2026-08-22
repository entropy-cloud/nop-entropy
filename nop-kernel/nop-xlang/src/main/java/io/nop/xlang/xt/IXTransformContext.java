/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xt.core.IXtTransformOutput;
import io.nop.xlang.xt.model.XtTransformModel;

import java.util.Map;
import java.util.Set;

public interface IXTransformContext extends IXPathContext<XNode> {

    XtTransformModel getTransformModel();

    IXTransformRule getTemplate(String id);

    /**
     * 返回 mapping 中按 tagName 编译好的规则；未命中 match 时返回 default 规则。
     * mapping 本身不存在时抛出 ERR_XT_MAPPING_NOT_FOUND。
     */
    IXTransformRule getCompiledRuleForTag(String mappingId, String tagName);

    /**
     * @deprecated 改用 {@link #getCompiledRuleForTag(String, String)}
     */
    @Deprecated
    IXTransformRule getRuleForTag(String mappingId, String tagName);

    IXtTransformOutput getOutput();

    Object getVariable(String name);

    void setVariable(String name, Object value);

    Map<String, Object> getParameters();

    /**
     * 模板循环引用检测状态。所有 child context 共享同一份 visited state。
     */
    Set<String> getVisitedTemplates();

    /**
     * mapping 循环引用检测状态。所有 child context 共享同一份 visited state。
     */
    Set<String> getVisitedMappings();

    IXTransformContext childContext(XNode newNode);

    IXTransformContext childContext(XNode newNode, XNode newOutput);
}
