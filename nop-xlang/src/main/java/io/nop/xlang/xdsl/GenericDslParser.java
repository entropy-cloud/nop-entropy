/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.json.DslXNodeToJsonTransformer;

/**
 * 根据xdef元模型定义将xml解析为模型对象。
 */
public class GenericDslParser extends AbstractDslParser<DynamicObject> {
    /**
     * 为编辑器提供数据，此时只解析数字类型和boolean类型，其他类型都作为字符串返回
     */
    private boolean forEditor;

    public GenericDslParser forEditor(boolean forEditor) {
        this.forEditor = forEditor;
        return this;
    }

    @Override
    protected DynamicObject doParseNode(XNode node) {
        IXDefinition xdef = getXdef();

        return (DynamicObject) new DslXNodeToJsonTransformer(forEditor, xdef, getCompileTool()).parseObject(node);
    }
}