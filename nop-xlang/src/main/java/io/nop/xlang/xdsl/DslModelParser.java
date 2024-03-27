/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.core.resource.component.version.VersionedName;
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

    private boolean disableInit;

    private boolean ignoreUnknown;

    private String resolveInDir;

    public DslModelParser(String requiredSchema) {
        this.setRequiredSchema(requiredSchema);
    }

    public DslModelParser() {
    }

    public String getResolveInDir() {
        return resolveInDir;
    }

    public DslModelParser resolveInDir(String dir) {
        this.setResolveInDir(dir);
        return this;
    }

    public void setResolveInDir(String resolveInDir) {
        if (resolveInDir != null && resolveInDir.startsWith("/") && !resolveInDir.endsWith("/")) {
            resolveInDir = resolveInDir + "/";
        }
        this.resolveInDir = resolveInDir;
    }

    public DslModelParser ignoreUnknown(boolean ignoreUnknown) {
        this.ignoreUnknown = ignoreUnknown;
        return this;
    }

    public DslModelParser disableInit(boolean disableInit) {
        this.disableInit = disableInit;
        return this;
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

    protected IComponentModel initModelName(IComponentModel model) {
        if (resolveInDir == null)
            return model;

        IXDefinition def = getXdef();
        if (def.getXdefModelNameProp() == null)
            return model;

        String path = getResourcePath();
        if (StringHelper.isEmpty(path))
            return model;

        VersionedName versionedName = ResourceVersionHelper.parseVersionedName(path, resolveInDir, true);
        if (def.getXdefModelNameProp() != null) {
            BeanTool.setProperty(model, def.getXdefModelNameProp(), versionedName.getName());
        }
        if (def.getXdefModelVersionProp() != null) {
            BeanTool.setProperty(model, def.getXdefModelVersionProp(), versionedName.getVersion());
        }
        return model;
    }

    @Override
    protected IComponentModel doParseNode(XNode node) {
        return initModelName(doParseNode0(node));
    }

    protected IComponentModel doParseNode0(XNode node) {
        IXDefinition xdef = getXdef();
        if (dynamic || forEditor) {
            Object obj = new DslXNodeToJsonTransformer(forEditor, xdef, getCompileTool()).ignoreUnknown(ignoreUnknown).parseObject(node);
            return (IComponentModel) obj;
        }

        // if (xdef.getXdefBeanClass() == null)
        // throw new NopException(ERR_XDEF_NO_BEAN_CLASS_ATTR)
        // .source(xdef)
        // .param(ARG_NODE, node);

        // IClassModel classModel = ReflectionManager.instance().loadClassModel(xdef.getBeanClass());
        // IComponentModel model = BeanTool.buildBean(obj, classModel.getType());
        Object model = new DslBeanModelParser(false, xdef, getCompileTool()).ignoreUnknown(ignoreUnknown)
                .transformToObject(node);
        if (model instanceof IXDslModel) {
            IXDslModel dslModel = (IXDslModel) model;
            dslModel.setXdslSchema(xdef.resourcePath());
            dslModel.setImportExprs(getImportExprs());
        }

        if (!disableInit && model instanceof INeedInit)
            ((INeedInit) model).init();
        return (IComponentModel) model;
    }
}
