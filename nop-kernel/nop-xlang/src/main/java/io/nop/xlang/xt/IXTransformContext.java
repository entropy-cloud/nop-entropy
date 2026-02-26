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
import io.nop.xlang.xt.model.XtMappingModel;
import io.nop.xlang.xt.model.XtTransformModel;

import java.util.Map;

public interface IXTransformContext extends IXPathContext<XNode> {

    XtTransformModel getTransformModel();

    IXTransformRule getTemplate(String id);

    XtMappingModel getMapping(String id);

    IXTransformRule getRuleForTag(String mappingId, String tagName);

    IXtTransformOutput getOutput();

    Object getVariable(String name);

    void setVariable(String name, Object value);

    Map<String, Object> getParameters();

    IXTransformContext childContext(XNode newNode);

    IXTransformContext childContext(XNode newNode, XNode newOutput);
}
