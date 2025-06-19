/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.utils;

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.idea.plugin.utils.XmlPsiHelper.getXmlTag;

public class XDefPsiHelper {
    static final Logger LOG = LoggerFactory.getLogger(XDefPsiHelper.class);

    private static IXDefinition xdefDef;
    private static IXDefinition xdslDef;
    private static IXDefinition xplDef;

    public static synchronized IXDefinition getXdefDef() {
        if (xdefDef == null) {
            xdefDef = new XDefinitionParser().parseFromResource(new ClassPathResource(
                    "classpath:/_vfs/nop/schema/xdef.xdef"));
        }
        return xdefDef;
    }

    public static synchronized IXDefinition getXDslDef() {
        if (xdslDef == null) {
            xdslDef = new XDefinitionParser().parseFromResource(new ClassPathResource(
                    "classpath:/_vfs/nop/schema/xdsl.xdef"));
        }
        return xdslDef;
    }

    public static synchronized IXDefinition getXplDef() {
        if (xplDef == null) {
            xplDef = new XDefinitionParser().parseFromResource(new ClassPathResource(
                    "classpath:/_vfs/nop/schema/xpl.xdef"));
        }
        return xplDef;
    }

    public static String getSchemaPath(XmlTag tag) {
        PsiFile file = tag.getContainingFile();
        String fileExt = StringHelper.fileExt(file.getName());
        if ("xpl".equals(fileExt) || "xrun".equals(fileExt) || "xgen".equals(fileExt)) {
            return XDslConstants.XDSL_SCHEMA_XPL;
        }

        String ns = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDSL);
        String key;
        if (ns == null) {
            key = XDslKeys.DEFAULT.SCHEMA;
        } else {
            key = ns + ":schema";
        }

        return tag.getAttributeValue(key);
    }

    public static IXDefinition loadSchema(String schemaUrl) {
        try {
            IXDefinition def = SchemaLoader.loadXDefinition(schemaUrl);
            return def;
        } catch (Exception e) {
            LOG.debug("nop.load-schema-fail", e);
            return null;
        }
    }

    public static XmlTagInfo getTagInfo(PsiElement element) {
        XmlTag tag = getXmlTag(element);
        if (tag != null) {
            String schemaUrl = XDefPsiHelper.getSchemaPath(XmlPsiHelper.getRoot(tag));
            if (schemaUrl != null) {
                return XDefPsiHelper.getTagInfo(schemaUrl, tag);
            }
        }
        return null;
    }

    public static XmlTagInfo getTagInfo(String schemaUrl, XmlTag tag) {
        IXDefinition def = loadSchema(schemaUrl);
        if (def == null) {
            return null;
        }

        IXDefNode xdslDefNode = getXDslDef().getRootNode();
        // 通过任意未定义的子节点名称，得到 xpl 的 xdef:unknown-tag 子节点定义
        IXDefNode xplDefNode = getXplDef().getRootNode().getChild("any");

        List<XmlTag> tags = getSelfAndParents(tag);
        tags = CollectionHelper.reverseList(tags);

        XmlTag rootTag = tags.get(0);
        String xdefNs = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDEF);
        String xdslNs = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDSL);

        boolean xpl = false;
        XmlTagInfo tagInfo = null;
        for (int i = 0, n = tags.size(); i < n; i++) {
            XmlTag xmlTag = tags.get(i);

            if (i == 0) {
                tagInfo = new XmlTagInfo(xmlTag, def, def.getRootNode(), null, //
                                         xdslDefNode, false, xdefNs, xdslNs);
            } else {
                XmlTagInfo parentTagInfo = tagInfo;
                // Note: 对 xpl 节点的名字空间不做转换，也就是不限定其名字空间
                String tagName = xpl ? xmlTag.getName() : normalizeNamespace(xmlTag.getName(), xdefNs, xdslNs);

                xdslDefNode = parentTagInfo.getXDslDefNodeChild(tagName);

                IXDefNode defNode = tagName.startsWith("x:") ? xdslDefNode : parentTagInfo.getDefNodeChild(tagName);
                if (defNode == null) {
                    defNode = xpl ? xplDefNode : null;
                }

                tagInfo = new XmlTagInfo(xmlTag,
                                         def,
                                         defNode,
                                         parentTagInfo.getDefNode(),
                                         xdslDefNode,
                                         parentTagInfo.isCustom() || parentTagInfo.isSupportBody(),
                                         xdefNs,
                                         xdslNs);

                if (isXplNode(defNode)) {
                    xpl = true;
                }
            }
        }
        return tagInfo;
    }

    /**
     * 对 xml 的标签和属性名中的名字空间做转换，
     * 从而支持对 xdsl.xdef 和 xdef.xdef 中的节点和属性的文档显示和引用跳转
     */
    public static String normalizeNamespace(String xmlName, String xdefNs, String xdslNs) {
        // 转换 /nop/schema/xdsl.xdef 的 xdsl 名字空间
        if (xdslNs != null && !"x".equals(xdslNs) && xmlName.startsWith(xdslNs + ":")) {
            xmlName = "x:" + xmlName.substring((xdslNs + ":").length());
        }
        // 转换 /nop/schema/xdef.xdef 的 meta 名字空间
        else if (xdefNs != null && !"xdef".equals(xdefNs) && xmlName.startsWith(xdefNs + ":")) {
            xmlName = "xdef:" + xmlName.substring((xdefNs + ":").length());
        }
        return xmlName;
    }

    static boolean isXplNode(IXDefNode defNode) {
        if (defNode == null || defNode.getXdefValue() == null) {
            return false;
        }

        String stdDomain = defNode.getXdefValue().getStdDomain();
        return stdDomain.equals("xpl") || stdDomain.startsWith("xpl-");
    }

    /** 自底向上查找 <code>tag</code> 所在分支上的节点 */
    static List<XmlTag> getSelfAndParents(XmlTag tag) {
        List<XmlTag> ret = new ArrayList<>();

        while (tag != null) {
            ret.add(tag);
            tag = tag.getParentTag();
        }
        return ret;
    }
}
