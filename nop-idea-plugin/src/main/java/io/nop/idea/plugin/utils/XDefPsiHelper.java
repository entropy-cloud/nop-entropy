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
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefKeys;
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
            String prefix = XDslKeys.DEFAULT.X_NS_PREFIX;
            ns = prefix.substring(0, prefix.length() - 1);
        }
        return ns;
    }

    public static String getXDefNamespace(XmlTag tag) {
        XmlTag rootTag = XmlPsiHelper.getRoot(tag);
        String ns = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDEF);

        if (ns == null) {
            ns = XDefKeys.DEFAULT.NS;
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
        IResource resource = new InMemoryTextResource('/' + file.getVirtualFile().getName() + ".xdef", content);

        try {
            return new XDefinitionParser().parseFromResource(resource);
        } catch (Exception e) {
            LOG.debug("nop.load-schema-fail", e);
            return null;
        }
    }

    public static XmlTagInfo getTagInfo(PsiElement element) {
        XmlTag tag = getXmlTag(element);
        if (tag == null) {
            return null;
        }

        String schemaUrl = getSchemaPath(XmlPsiHelper.getRoot(tag));
        if (schemaUrl != null) {
            return getTagInfo(schemaUrl, tag);
        }
        return null;
    }

    public static XmlTagInfo getTagInfo(String schemaUrl, XmlTag tag) {
        // 在对应解析 xml 标签的元模型节点时，需要注意以下几点
        // - 若根节点的 `x:schema` 为 `/nop/schema/xdef.xdef`，则其在定义某类 DSL 的元模型，
        //   若引用的是其他 xdef，则其是在定义某个具体的 DSL 模型
        // - 在普通的 XDef 元模型中，必须在根节点固定定义 `xdef` 和 `x` 名字空间：
        //   - `xmlns:x="/nop/schema/xdsl.xdef"`
        //   - `xmlns:xdef="/nop/schema/xdef.xdef"`
        //   其中，以 `xdef` 为名字空间的节点和属性，用于定义 DSL 的结构，而以
        //   `x` 为名字空间的节点和属性，则用于定义差量规则
        // - 而在普通的 DSL 模型中，必须在根节点固定定义 `x` 名字空间：
        //   - `xmlns:x="/nop/schema/xdsl.xdef"`
        //   其中，以 `x` 为名字空间的节点和属性，则用于定义差量规则
        // - `/nop/schema/xdef.xdef` 本身的定义是**自举**的，其描述的是所有元模型的结构。
        //   其以 `meta` 作为名字空间来定义其 DSL 结构，
        //   而以 `xdef` 为名字空间的属性和节点，则为其 DSL 的**元属性**和**元节点**，
        //   用于声明其 DSL 模型所包含的属性和节点类型
        // - `/nop/schema/xdsl.xdef` 自身的定义也是**自举**的，其描述的是所有 DSL 模型的结构。
        //   其以 `xdsl` 作为名字空间，对其进行差量控制（这里主要为指定其 `schema` 为 `/nop/schema/xdef.xdef`）
        // - `/nop/schema/xpl.xdef` 为 Xpl 类型节点的元模型，且以 `xpl` 为其固定的名字空间
        IXDefinition def = loadSchema(schemaUrl);
        if (def == null) {
            return null;
        }

        IXDefNode xdslDefNode = getXDslDef().getRootNode();

        List<XmlTag> tags = getSelfAndParents(tag);
        tags = CollectionHelper.reverseList(tags);

        XmlTag rootTag = tags.get(0);
        String xdefNs = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDEF);
        String xdslNs = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDSL);

        boolean xpl = false;
        boolean xlibDsl = XDslConstants.XDSL_SCHEMA_XLIB.equals(schemaUrl);
        XmlTagInfo tagInfo = null;
        for (int i = 0, n = tags.size(); i < n; i++) {
            XmlTag xmlTag = tags.get(i);

            if (i == 0) {
                tagInfo = new XmlTagInfo(xmlTag, null, def, def.getRootNode(), //
                                         xdslDefNode, xdefNs, xdslNs);
            } else {
                XmlTagInfo parentTagInfo = tagInfo;
                String tagName = normalizeNamespace(xmlTag.getName(), xdefNs, xdslNs);

                xdslDefNode = parentTagInfo.getXDslDefNodeChild(tagName);

                IXDefNode defNode;
                // Note: 只有不在 xdsl.xdef 中，且以 x 为名字空间的节点，才使用 xdsl 节点定义，
                // 否则，保持在 XDef 元模型的节点定义
                if (tagName.startsWith("x:") && "x".equals(xdslNs)) {
                    defNode = xdslDefNode;
                }
                // Xpl 节点始终采用 xpl.xdef 元模型
                else if (xpl) {
                    // 通过任意未定义的子节点名称，得到 xpl 的 xdef:unknown-tag 子节点定义
                    defNode = getXplDef().getRootNode().getChild("any");
                } else {
                    defNode = parentTagInfo.getDefNodeChild(tagName);
                }

                tagInfo = new XmlTagInfo(xmlTag, parentTagInfo, def, defNode, xdslDefNode, xdefNs, xdslNs);

                if (isXplNode(defNode)) {
                    xpl = true;
                }
                // xlib.xdef 中的 source 标签设置为 xml 类型，是因为在获取 XplLib 模型的时候会根据 xlib.xdef 来解析，
                // 但此时这个 source 段无法自动进行编译，必须结合它的 outputMode 和 attrs 配置等才能决定。
                // 因此，将其子节点同样视为 xpl 节点处理
                else if (!xpl && xlibDsl && "source".equals(tagName) //
                         && "xml".equals(getDefNodeType(defNode)) //
                         && parentTagInfo.getDefNode().isUnknownTag() //
                         && "tags".equals(parentTagInfo.getParentDefNode().getTagName()) //
                ) {
                    xpl = true;
                }
            }
        }
        return tagInfo;
    }

    /** 确保 `xmlName` 的 `xdef.xdef`、`xdsl.xdef` 对应的名字空间始终为 `xdef` 和 `x` */
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
        String stdDomain = getDefNodeType(defNode);

        return stdDomain != null && (stdDomain.equals("xpl") || stdDomain.startsWith("xpl-"));
    }

    static String getDefNodeType(IXDefNode defNode) {
        if (defNode == null || defNode.getXdefValue() == null) {
            return null;
        }
        return defNode.getXdefValue().getStdDomain();
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
