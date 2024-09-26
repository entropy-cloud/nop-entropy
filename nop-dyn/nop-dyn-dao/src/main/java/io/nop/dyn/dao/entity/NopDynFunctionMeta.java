/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity._gen._NopDynFunctionMeta;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xlang.xpl.XplConstants;

import java.util.Map;


@BizObjName("NopDynFunctionMeta")
public class NopDynFunctionMeta extends _NopDynFunctionMeta {

    public String getSourceXml() {
        String source = getSource();

        if (StringHelper.isEmpty(source))
            return null;

        if (!StringHelper.isEmpty(getScriptLang())) {
            return buildScriptSource();
        }

        if (StringHelper.maybeXml(source)) {
            return source;
        }
        return StringHelper.escapeXml(source);
    }

    private String buildScriptSource() {
        XNode node = XNode.make(XplConstants.TAG_C_SCRIPT);
        node.setAttr(XplConstants.LANG_NAME, getScriptLang());
        node.setAttr(XplConstants.ARGS_NAME, getArgDeclarations());
        node.setAttr(XplConstants.RETURN_TYPE_NAME, getReturnType());
        node.setContentValue(getSource());
        return node.xml();
    }

    public String getArgDeclarations() {
        StringBuilder sb = new StringBuilder();
        XNode metaNode = getFuncMetaNode();
        if (metaNode != null) {
            for (XNode argNode : metaNode.childrenByTag("arg")) {
                sb.append(argNode.getAttr("name"));
                sb.append(":");
                String type = argNode.attrText("type");
                if (StringHelper.isEmpty(type))
                    type = Object.class.getName();
                sb.append(type);
                sb.append(',');
            }
        }
        sb.append("selection:").append(FieldSelectionBean.class.getName());
        sb.append(",svcCtx:").append(IServiceContext.class.getName());
        return sb.toString();
    }

    public XNode getFuncMetaNode() {
        String funcMeta = getFuncMeta();
        if (StringHelper.isEmpty(funcMeta))
            return null;

        Map<String, Object> map = (Map<String, Object>) JsonTool.parse(funcMeta);
        map.put("type", getFunctionType());

        IObjMeta objMeta = SchemaLoader.loadXMeta(NopDynDaoConstants.XDEF_BIZ);
        IObjSchema funcSchema = getFuncSchema(objMeta);
        return new DslModelToXNodeTransformer(objMeta).transformObj(funcSchema, map);
    }

    private IObjSchema getFuncSchema(IObjMeta objMeta) {
        if (NopDynDaoConstants.FUNCTION_TYPE_LOADER.equals(getFunctionType())) {
            return objMeta.getProp("loaders")
                    .getItemSchema();
        } else {
            return objMeta.getProp("actions")
                    .getItemSchema();
        }
    }
}
