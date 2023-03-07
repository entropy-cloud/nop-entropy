/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.xlib.XplTagLib;

public class LibTagCompiler implements IXplTagCompiler {
    public static final LibTagCompiler INSTANCE = new LibTagCompiler();

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {

        IXDefinition xdef = SchemaLoader.loadXDefinition(XDslConstants.XDSL_SCHEMA_XLIB);

        XNode libNode = XNode.make("lib");
        node.forEachAttr(libNode::setAttr);
        libNode.makeChild("tags").appendChildren(node.cloneChildren());
        libNode.setAttr("ext:local", true);

        // String namespace = XDslParseHelper.requireAttrPropName(node, NAMESPACE_NAME);
        DslModelParser parser = new DslModelParser(XDslConstants.XDSL_SCHEMA_XLIB);
        parser.setCompileTool(new XLangCompileTool(scope));
        XplTagLib lib = (XplTagLib) parser.parseWithXDef(xdef, libNode);
        lib.setDefaultNamespace(XLangConstants.LOCAL_NAMESPACE);
        scope.addLib(XLangConstants.LOCAL_NAMESPACE, lib);

        return null;
    }
}
