/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.psi;

import java.util.Set;

import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XlibConstants;

/**
 * 标签的{@link IXDefNode 定义节点}信息
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-10-21
 */
public class XLangTagMeta {
    private static final XLangTagMeta UNKNOWN_META = new XLangTagMeta(null);

    final String tagName;
    XLangTagMeta parent;

    /**
     * 当前标签在元模型中所对应的定义节点：
     * 元模型一般由根节点上的 <code>x:schema</code> 指定，
     * 但 Xpl 类型节点的元模型始终为 <code>/nop/schema/xpl.xdef</code>
     */
    IXDefNode defNodeInSchema;
    /**
     * 当前标签所在的元模型中通过 <code>xdef:check-ns</code> 所设置的待校验的名字空间列表：
     * 节点或属性的名字空间若在该列表中，则其必须在元模型中显式定义
     */
    Set<String> checkNsInSchema;

    /**
     * 若当前标签在元模型文件（<code>*.xdef</code>）中，
     * 则用于记录当前标签在该元模型中的定义
     */
    IXDefNode defNodeInSelfSchema;
    /**
     * 若当前标签在元模型文件（<code>*.xdef</code>）中，
     * 则用于记录该元模型 <code>xdef:check-ns</code> 属性的值
     */
    Set<String> checkNsInSelfSchema;

    /**
     * 当前标签所对应的元模型 <code>/nop/schema/xdsl.xdef</code> 中的定义节点：
     * 所有的 DSL （包括元模型自身）均由该模型定义
     */
    IXDefNode xdslDefNode;

    XDefKeys xdefKeys;
    XDslKeys xdslKeys;

    XLangTagMeta(String tagName) {
        this.tagName = tagName;
    }

    public String getTagName() {
        return tagName;
    }

    public IXDefNode getDefNodeInSchema() {
        return defNodeInSchema;
    }

    public IXDefNode getDefNodeInSelfSchema() {
        return defNodeInSelfSchema;
    }

    /** 是否未定义 */
    public boolean isUnknown() {
        return defNodeInSchema == null;
    }

    /** 当前标签是否在元模型 <code>*.xdef</code> 中 */
    public boolean isInAnySchema() {
        return xdefKeys != null;
    }

    /** 当前标签是否在元模型 <code>/nop/schema/xdef.xdef</code> 中 */
    public boolean isInXdefSchema() {
        return checkNsInSelfSchema != null //
               && checkNsInSelfSchema.contains(XDefKeys.DEFAULT.NS) //
               && !XDefKeys.DEFAULT.equals(xdefKeys);
    }

    /** 当前标签是否为 Xpl 类型节点，或者其对应元模型 <code>/nop/schema/xpl.xdef</code> 中的定义节点 */
    public boolean isXplNode() {
        if (defNodeInSchema == null) {
            return false;
        } else if (XDefPsiHelper.isXplTypeNode(defNodeInSchema)) {
            return true;
        }

        String vfsPath = XmlPsiHelper.getNopVfsPath(defNodeInSchema);
        if (isXplXdefFile(vfsPath)) {
            return true;
        }

        // xlib.xdef 中的 source 标签被设置为 xml 类型，是因为在获取 XplLib 模型的时候会根据 xlib.xdef 来解析，
        // 但此时这个 source 段无法自动进行编译，必须结合它的 outputMode 和 attrs 配置等才能决定。
        // 因此，需将其子节点同样视为 Xpl 节点处理
        return XDslConstants.XDSL_SCHEMA_XLIB.equals(vfsPath)
               && XlibConstants.SOURCE_NAME.equals(tagName)
               && XDefConstants.STD_DOMAIN_XML.equals(XDefPsiHelper.getDefNodeType(defNodeInSchema));
    }

    /**
     * Note: 需要在 {@link io.nop.idea.plugin.resource.ProjectEnv#withProject ProjectEnv#withProject}
     * 中调用该函数
     */
    public static XLangTagMeta create(XLangTag tag) {
        String tagName = tag.getName();
        String tagNs = StringHelper.getNamespace(tagName);
        XLangTag parentTag = tag.getParentTag();

        // 根节点
        if (parentTag == null) {
            return createForRootTag(tagNs, tagName, tag);
        }
        return createForChildTag(parentTag, tagNs, tagName);
    }

    private static XLangTagMeta createForRootTag(String tagNs, String tagName, XLangTag tag) {
        // Note: 若标签在 Xpl 脚本中，则将返回 /nop/schema/xpl.xdef
        String schemaUrl = XDefPsiHelper.getSchemaPath(tag);
        if (schemaUrl == null) {
            // TODO 未在根节点通过 x:schema 指定元模型路径
            return UNKNOWN_META;
        }

        IXDefinition schema = XDefPsiHelper.loadSchema(schemaUrl);
        IXDefinition xdslSchema = XDefPsiHelper.getXDslDef();

        // 若其元模型为 /nop/schema/xdef.xdef，则其自身也为元模型（*.xdef）
        boolean inSchema = XDslConstants.XDSL_SCHEMA_XDEF.equals(schemaUrl) //
                           || isXdefFile(tag.getContainingFile().getName());

        // 非 xdef/xpl 模型，全部视为 xdsl
        if (schema == null && !inSchema && !isXplXdefFile(schemaUrl)) {
            schema = xdslSchema;
        }
        // 若元模型加载失败，则不做识别
        if (schema == null) {
            // TODO 元模型 /xx/xx.xdef 加载失败
            return UNKNOWN_META;
        }

        String xdefNs = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDEF);
        String xdslNs = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDSL);
        XDefKeys xdefKeys = XDefKeys.of(xdefNs);
        XDslKeys xdslKeys = XDslKeys.of(xdslNs);

        // Note: xdef 名字空间的根节点必须为 xdef.xdef 中已定义的节点
        if (inSchema && xdefKeys.NS.equals(tagNs)) {
            if (getChildXdefDefNode(xdefKeys, schema, tagName) == null) {
                // TODO 在名字空间 xx 对应的元模型 xdef.xdef 中未定义 xx:xx
                return UNKNOWN_META;
            }
        }

        IXDefNode defNodeInSchema = schema.getRootNode();
        // 如果其不在元模型中，则其根节点标签名必须与其 x:schema 所定义的根节点标签名保持一致，
        // 除非根节点被定义为 xdef:unknown-tag
        if (!inSchema && !defNodeInSchema.isUnknownTag() //
            && !defNodeInSchema.getTagName().equals(tagName) //
        ) {
            // TODO 根节点标签名 <xx/> 与元模型定义的根节点标签名 <yy/> 不一致
            return UNKNOWN_META;
        }

        // Note: 正在编辑中的 xdef 有可能是不完整的，此时将无法解析出 IXDefinition
        IXDefinition selfSchema = null;
        if (inSchema) {
            String vfsPath = XmlPsiHelper.getNopVfsPath(tag);
            if (vfsPath != null) {
                selfSchema = XDefPsiHelper.loadSchema(vfsPath);
            }
            // 支持非标准的 vfs 资源，以适应单元测试等环境
            else {
                selfSchema = XDefPsiHelper.loadSchema(tag.getContainingFile());
            }
        }

        XLangTagMeta tagMeta = new XLangTagMeta(tagName);
        tagMeta.defNodeInSchema = defNodeInSchema;
        tagMeta.checkNsInSchema = schema.getXdefCheckNs();

        tagMeta.defNodeInSelfSchema = selfSchema != null ? selfSchema.getRootNode() : null;
        tagMeta.checkNsInSelfSchema = inSchema
                                      ? StringHelper.parseCsvSet(tag.getAttributeValue(xdefKeys.CHECK_NS))
                                      : null;

        tagMeta.xdslDefNode = xdslSchema.getRootNode();
        tagMeta.xdefKeys = inSchema ? xdefKeys : null;
        tagMeta.xdslKeys = xdslKeys;

        return tagMeta;
    }

    private static XLangTagMeta createForChildTag(XLangTag parentTag, String tagNs, String tagName) {
        XLangTagMeta parentTagMeta = parentTag.getTagMeta();
        // 无定义节点
        if (parentTagMeta.isUnknown()) {
            // TODO 父节点未定义
            return UNKNOWN_META;
        }

        XDefKeys xdefKeys = parentTagMeta.xdefKeys;
        XDslKeys xdslKeys = parentTagMeta.xdslKeys;

        IXDefNode defNodeInSchema = null;
        // 1. 获取确定的 xdsl 节点，如 <x:gen-extends/>、<x:post-extends/>等
        if (xdslKeys.NS.equals(tagNs)) {
            String xdslTagName = replaceTagNs(tagName, XDslKeys.DEFAULT.NS);
            defNodeInSchema = getChildDefNode(parentTagMeta.xdslDefNode, xdslTagName);

            if (defNodeInSchema.isUnknownTag()) {
                // TODO 在名字空间 xx 对应的元模型 xdsl.xdef 中未定义 xx:xx
                return UNKNOWN_META;
            }
        }

        // 2. 若父节点为 Xpl 类型节点，则直接从 xpl.xdef 中获取节点（含带 xpl 名字空间的节点）
        if (defNodeInSchema == null && parentTagMeta.isXplNode()) {
            defNodeInSchema = getChildDefNode(XDefPsiHelper.getXplDef().getRootNode(), tagName);

            if (XplConstants.XPL_NS.equals(tagNs) && defNodeInSchema.isUnknownTag()) {
                // TODO 在名字空间 xpl 对应的元模型 xpl.xdef 中未定义 xpl:xx
                return UNKNOWN_META;
            }
        }

        // 3. 在元元模型中，以 xdef 为名字空间的标签， 需以 meta:unknown-tag 作为其节点定义，即，交叉定义
        if (defNodeInSchema == null && parentTagMeta.isInXdefSchema() //
            && XDefKeys.DEFAULT.NS.equals(tagNs) //
        ) {
            defNodeInSchema = parentTagMeta.defNodeInSchema.getXdefUnknownTag();
        }

        // 4. 获取确定的 xdef 节点，如 <xdef:define/>、<xdef:post-parse/>等
        // - *.xdef 必然始终以 /nop/schema/xdef.xdef 为元模型
        if (defNodeInSchema == null && parentTagMeta.isInAnySchema() //
            && xdefKeys.NS.equals(tagNs) //
        ) {
            defNodeInSchema = getChildXdefDefNode(xdefKeys, parentTagMeta.defNodeInSchema, tagName);
            if (defNodeInSchema == null) {
                // TODO 在名字空间 xx 对应的元模型 xdef.xdef 中未定义 xx:xx
                return UNKNOWN_META;
            }
        }

        // 5. 从当前标签的父标签的定义节点中获取其定义节点
        if (defNodeInSchema == null) {
            defNodeInSchema = getChildDefNode(parentTagMeta.defNodeInSchema, tagName);
        }

        // 6. 带名字空间的节点需满足：其在元模型中已显式定义，或者其为无约束节点
        if (tagNs != null) {
            // 需要校验的名字空间下的节点必须在其元模型中显式定义
            if (!parentTagMeta.isInAnySchema() //
                && parentTagMeta.checkNsInSchema != null //
                && parentTagMeta.checkNsInSchema.contains(tagNs) //
            ) {
                if (defNodeInSchema != null && defNodeInSchema.isUnknownTag()) {
                    // TODO 名字空间 xx 下的标签必须在 /xx/xx.xdef 中显式定义
                    return UNKNOWN_META;
                }
            }
            // 否则，其定义节点必然为 xdsl 的 xdef:unknown-tag 节点
            else if (defNodeInSchema == null) {
                defNodeInSchema = parentTagMeta.xdslDefNode.getXdefUnknownTag();
            }
        }

        if (defNodeInSchema == null) {
            // TODO 节点未定义
            return UNKNOWN_META;
        }

        XLangTagMeta tagMeta = new XLangTagMeta(tagName);
        tagMeta.parent = parentTagMeta;
        tagMeta.defNodeInSchema = defNodeInSchema;
        tagMeta.checkNsInSchema = parentTagMeta.checkNsInSchema;

        tagMeta.defNodeInSelfSchema = getChildDefNode(parentTagMeta.defNodeInSelfSchema, tagName);
        tagMeta.checkNsInSelfSchema = parentTagMeta.checkNsInSelfSchema;

        tagMeta.xdslDefNode = getChildDefNode(parentTagMeta.xdslDefNode, tagName);
        tagMeta.xdefKeys = xdefKeys;
        tagMeta.xdslKeys = xdslKeys;

        return tagMeta;
    }

    private static boolean isXdefFile(String filename) {
        return filename.endsWith(".xdef");
    }

    private static boolean isXplXdefFile(String filename) {
        return XDslConstants.XDSL_SCHEMA_XPL.equals(filename);
    }

    private static String replaceTagNs(String tagName, String ns) {
        return ns + tagName.substring(tagName.indexOf(':'));
    }

    /** 获得指定标签名的子定义节点，或者 <code>xdef:unknown-tag</code> 定义节点 */
    private static IXDefNode getChildDefNode(IXDefNode defNode, String tagName) {
        return defNode != null ? defNode.getChild(tagName) : null;
    }

    private static IXDefNode getChildXdefDefNode(XDefKeys xdefKeys, IXDefNode defNode, String tagName) {
        String xdefTagName = replaceTagNs(tagName, XDefKeys.DEFAULT.NS);
        IXDefNode childDefNode = getChildDefNode(defNode, xdefTagName);

        if (childDefNode.isUnknownTag() && !xdefKeys.UNKNOWN_TAG.equals(tagName)) {
            // 在名字空间 xx 对应的元模型 xdef.xdef 中未定义 xx:xx
            childDefNode = null;
        }
        return childDefNode;
    }
}
