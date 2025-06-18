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
        String schemaPath = tag.getAttributeValue(key);
        return schemaPath;
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

        IXDefNode dslDefNode = getXDslDef().getRootNode();
        // 通过任意未定义的子节点名称，得到 xpl 的 xdef:unknown-tag 子节点定义
        IXDefNode xplDefNode = getXplDef().getRootNode().getChild("div");

        List<XmlTag> tags = getSelfAndParents(tag);
        tags = CollectionHelper.reverseList(tags);

        boolean xpl = false;
        XmlTagInfo tagInfo = null;
        for (int i = 0, n = tags.size(); i < n; i++) {
            XmlTag xmlTag = tags.get(i);

            if (i == 0) {
                tagInfo = new XmlTagInfo(xmlTag, def.getRootNode(), null, false, dslDefNode, def);
            } else {
                XmlTagInfo parent = tagInfo;
                String tagName = normalizeName(xmlTag.getName());

                dslDefNode = parent.getDslNodeChild(tagName);

                IXDefNode defNode = tagName.startsWith("x:") ? dslDefNode : parent.getDefNodeChild(tagName);
                if (defNode == null) {
                    defNode = xpl ? xplDefNode : null;
                }

                tagInfo = new XmlTagInfo(xmlTag,
                                         defNode,
                                         parent.getDefNode(),
                                         parent.isCustom() || parent.isSupportBody(),
                                         dslDefNode,
                                         def);

                if (isXplNode(defNode)) {
                    xpl = true;
                }
            }
        }
        return tagInfo;
    }

    public static String normalizeName(String name) {
        if (name.startsWith("xdsl:")) {
            name = "x:" + name.substring("xdsl:".length());
        } else if (name.startsWith("meta:")) {
            name = "xdef:" + name.substring("meta:".length());
        }
        return name;
    }

    static boolean isXplNode(IXDefNode defNode) {
        if (defNode == null) {
            return false;
        }
        if (defNode.getXdefValue() == null) {
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
