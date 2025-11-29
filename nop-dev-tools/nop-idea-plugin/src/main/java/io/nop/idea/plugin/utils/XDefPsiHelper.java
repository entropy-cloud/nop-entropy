/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.utils;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XDefPsiHelper {
    static final Logger LOG = LoggerFactory.getLogger(XDefPsiHelper.class);

    public static IXDefinition getXdefDef() {
        return SchemaLoader.loadXDefinition(XDslConstants.XDSL_SCHEMA_XDEF);
    }

    public static IXDefinition getXDslDef() {
        return SchemaLoader.loadXDefinition(XDslConstants.XDSL_SCHEMA_XDSL);
    }

    public static IXDefinition getXplDef() {
        return SchemaLoader.loadXDefinition(XDslConstants.XDSL_SCHEMA_XPL);
    }

    /**
     * 从根节点获取 dsl 的元模型的 vfs 路径：
     * 若节点所在文件为 <code>*.xpl/*.xrun/*.xgen</code>，
     * 则其元模型为 Xpl，返回 {@link XDslConstants#XDSL_SCHEMA_XPL}
     */
    public static String getSchemaPath(XmlTag rootTag) {
        PsiFile file = rootTag.getContainingFile();
        String fileExt = StringHelper.fileExt(file.getName());
        if ("xpl".equals(fileExt) || "xrun".equals(fileExt) || "xgen".equals(fileExt)) {
            return XDslConstants.XDSL_SCHEMA_XPL;
        }

        String ns = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDSL);
        if (ns == null) {
            ns = XDslKeys.DEFAULT.NS;
        }

        String key = ns + ":schema";
        String schemaUrl = rootTag.getAttributeValue(key);

        // Note: schema 可能为相对路径
        return XmlPsiHelper.getNopVfsAbsolutePath(schemaUrl, rootTag);
    }

    /** Note: 已加载且自身及其依赖未变更的模型，将被缓存起来，不会重复解析 */
    public static IXDefinition loadSchema(String schemaUrl) {
        try {
            return SchemaLoader.loadXDefinition(schemaUrl);
        } catch (Exception e) {
            LOG.debug("nop.load-schema-fail", e);
            return null;
        }
    }

    /** Note: 直接解析内容，且不缓存解析结果 */
    public static IXDefinition loadSchema(PsiFile file) {
        String content = file.getText();
        // Note: 解析过程中，会检查路径的有效性，需保证以 / 开头，并添加 .xdef 后缀
        IResource resource = new InMemoryTextResource("/" + file.getText().hashCode() + ".xdef", content);

        try {
            return new XDefinitionParser().parseFromResource(resource);
        } catch (Exception e) {
            LOG.debug("nop.load-schema-fail", e);
            return null;
        }
    }

    public static boolean isXplTypeNode(IXDefNode defNode) {
        String stdDomain = getDefNodeType(defNode);

        return stdDomain != null && (stdDomain.equals("xpl") || stdDomain.startsWith("xpl-"));
    }

    public static String getDefNodeType(IXDefNode defNode) {
        if (defNode == null || defNode.getXdefValue() == null) {
            return null;
        }
        return defNode.getXdefValue().getStdDomain();
    }
}
