/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;

public class SchemaLoader {

    public static IObjMeta loadXMeta(String path) {
        if (StringHelper.isEmpty(path))
            return null;
        IObjMeta meta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel(path,
                XLangConstants.MODEL_TYPE_XMETA);
        return meta;
    }
    public static IXDefinition loadXDefinition(String path) {
        if (StringHelper.isEmpty(path))
            return null;
        IXDefinition xdef = (IXDefinition) ResourceComponentManager.instance().loadComponentModel(path,
                XLangConstants.MODEL_TYPE_XDEF);
        return xdef;
    }

    public static IObjMeta parseXMetaFromResource(IResource resource) {
        return (IObjMeta) ResourceComponentManager.instance().parseComponentModel(resource, XLangConstants.MODEL_TYPE_XMETA);
    }

    public static void validateNode(XNode node, IXDefNode defNode, boolean checkRootName) {
        new XDslValidator(XDslKeys.of(node)).validate(node, defNode, checkRootName);
    }
}