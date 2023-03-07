/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.INeedInit;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.json.DslBeanModelParser;
import io.nop.xlang.xdsl.json.DslXNodeToJsonTransformer;
import io.nop.xlang.xmeta.SchemaLoader;

public class DslModelParser extends AbstractDslParser<IComponentModel> {
    private boolean dynamic;
    /**
     * 为编辑器提供数据，此时只解析数字类型和boolean类型，其他类型都作为字符串返回
     */
    private boolean forEditor;

    public DslModelParser(String requiredSchema) {
        this.setRequiredSchema(requiredSchema);
    }

    public DslModelParser() {
    }

    public DslModelParser dynamic(boolean dynamic) {
        this.dynamic = dynamic;
        return this;
    }

    public DslModelParser forEditor(boolean forEditor) {
        this.forEditor = forEditor;
        return this;
    }

    public IComponentModel parseWithXDef(IXDefinition xdef, XNode node) {
        setXdef(xdef);
        SchemaLoader.validateNode(node, xdef.getRootNode(), false);
        return doParseNode(node);
    }

    @Override
    protected IComponentModel doParseNode(XNode node) {
        IXDefinition xdef = getXdef();
        if (dynamic || forEditor) {
            Object obj = new DslXNodeToJsonTransformer(forEditor, xdef, getCompileTool()).parseObject(node);
            return (IComponentModel) obj;
        }

        // if (xdef.getXdefBeanClass() == null)
        // throw new NopException(ERR_XDEF_NO_BEAN_CLASS_ATTR)
        // .source(xdef)
        // .param(ARG_NODE, node);

        // IClassModel classModel = ReflectionManager.instance().loadClassModel(xdef.getBeanClass());
        // IComponentModel model = BeanTool.buildBean(obj, classModel.getType());
        IComponentModel model = (IComponentModel) new DslBeanModelParser(false, xdef, getCompileTool())
                .transformToObject(node);
        if (model instanceof IXDslModel) {
            IXDslModel dslModel = (IXDslModel) model;
            dslModel.setXdslSchema(xdef.resourcePath());
            dslModel.setImportExprs(getImportExprs());
        }
        if (model instanceof INeedInit)
            ((INeedInit) model).init();
        return model;
    }
}
