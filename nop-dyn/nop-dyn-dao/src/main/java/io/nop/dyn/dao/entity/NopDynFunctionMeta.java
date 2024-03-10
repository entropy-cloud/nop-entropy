/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity._gen._NopDynFunctionMeta;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.SchemaLoader;


@BizObjName("NopDynFunctionMeta")
public class NopDynFunctionMeta extends _NopDynFunctionMeta {

    public String getSourceXml() {
        String source = getSource();

        if (StringHelper.isEmpty(source))
            return null;

        if (StringHelper.maybeXml(source)) {
            return source;
        }
        return StringHelper.escapeXml(source);
    }

    public XNode getFuncMetaNode() {
        String funcMeta = getFuncMeta();
        if (StringHelper.isEmpty(funcMeta))
            return null;

        Object map = JsonTool.parse(funcMeta);

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
