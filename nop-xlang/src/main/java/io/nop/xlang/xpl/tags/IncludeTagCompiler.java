/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.XLangParseBuffer;
import io.nop.xlang.xpl.XplIncludeType;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.Arrays;
import java.util.List;

import static io.nop.xlang.xpl.XplConstants.ENABLE_XPL_EXEC_NAME;
import static io.nop.xlang.xpl.XplConstants.SRC_NAME;
import static io.nop.xlang.xpl.XplConstants.TAG_NAME_NAME;
import static io.nop.xlang.xpl.XplConstants.TYPE_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrBool;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrXmlName;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrVPath;

public class IncludeTagCompiler implements IXplTagCompiler {
    public static IncludeTagCompiler INSTANCE = new IncludeTagCompiler();

    static final List<String> ATTR_NAMES = Arrays.asList(SRC_NAME, TAG_NAME_NAME, TYPE_NAME, ENABLE_XPL_EXEC_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);

        String src = requireAttrVPath(node, SRC_NAME, cp, scope);
        XplIncludeType type = XplParseHelper.getAttrEnum(node, TYPE_NAME, XplIncludeType.class, cp, scope);
        boolean enableXplExec = getAttrBool(node, ENABLE_XPL_EXEC_NAME, false);

        if (type == XplIncludeType.text) {
            IResource resource = VirtualFileSystem.instance().getResource(src);
            String text = ResourceHelper.readText(resource);
            if (enableXplExec)
                text = processXplExec(resource.location(), text, cp, scope);
            return EscapeOutputExpression.plainText(resource.location(), text);
        }

        XNode included = XModelInclude.instance().loadActiveNode(src);
        if (included != null) {
            Identifier tagName = getAttrXmlName(node, TAG_NAME_NAME, cp, scope);
            if (tagName != null) {
                included.setTagName(tagName.getName());
            }

            if (type != XplIncludeType.xpl && type != null) {
                return PrintTagCompiler.INSTANCE.parseTag(included, cp, scope);
            }

            XLangParseBuffer buf = new XLangParseBuffer();
            cp.parseTag(buf, included, scope);
            return buf.getShareScopeResult();
        }
        return null;
    }

    String processXplExec(SourceLocation loc, String text, IXplCompiler cp, IXLangCompileScope scope) {
        XLangCompileTool tool = new XLangCompileTool(scope);
        return StringHelper.renderTemplate(text, "<xpl:exec>", "</xpl:exec>", content -> {
            XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, content);
            return tool.compileTagBody(node).generateText(scope);
        });
    }
}
