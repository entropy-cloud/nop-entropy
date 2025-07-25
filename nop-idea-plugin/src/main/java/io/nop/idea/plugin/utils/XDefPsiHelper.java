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
import io.nop.core.resource.impl.ClassPathResource;
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

    private static IXDefinition xdefDef;
    private static IXDefinition xdslDef;
    private static IXDefinition xplDef;

    private static IXDefinition loadDef(String path) {
        return new XDefinitionParser().parseFromResource(new ClassPathResource("classpath:/_vfs" + path));
    }

    public static synchronized IXDefinition getXdefDef() {
        if (xdefDef == null) {
            xdefDef = loadDef(XDslConstants.XDSL_SCHEMA_XDEF);
        }
        return xdefDef;
    }

    public static synchronized IXDefinition getXDslDef() {
        if (xdslDef == null) {
            xdslDef = loadDef(XDslConstants.XDSL_SCHEMA_XDSL);
        }
        return xdslDef;
    }

    public static synchronized IXDefinition getXplDef() {
        if (xplDef == null) {
            xplDef = loadDef(XDslConstants.XDSL_SCHEMA_XPL);
        }
        return xplDef;
    }

    public static String getXDslNamespace(XmlTag tag) {
        XmlTag rootTag = XmlPsiHelper.getRoot(tag);
        String ns = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDSL);

        if (ns == null) {
            ns = XDslKeys.DEFAULT.NS;
        }
        return ns;
    }

    /** 从根节点获取 dsl 的元模型的 vfs 路径 */
    public static String getSchemaPath(XmlTag rootTag) {
        PsiFile file = rootTag.getContainingFile();
        String fileExt = StringHelper.fileExt(file.getName());
        if ("xpl".equals(fileExt) || "xrun".equals(fileExt) || "xgen".equals(fileExt)) {
            return XDslConstants.XDSL_SCHEMA_XPL;
        }

        String ns = getXDslNamespace(rootTag);
        String key = ns + ":schema";

        String schemaUrl = rootTag.getAttributeValue(key);
        // Note: schema 可能为相对路径
        return XmlPsiHelper.getNopVfsAbsolutePath(schemaUrl, rootTag);
    }

    public static IXDefinition loadSchema(String schemaUrl) {
        try {
            return SchemaLoader.loadXDefinition(schemaUrl);
        } catch (Exception e) {
            LOG.debug("nop.load-schema-fail", e);
            return null;
        }
    }

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

    public static boolean isXplDefNode(IXDefNode defNode) {
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
