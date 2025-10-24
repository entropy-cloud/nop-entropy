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
     * 在元模型 <code>/nop/schema/xdef.xdef</code> 绑定的名字空间（默认为 <code>xdef</code>）下，
     * 当前标签在该元模型中所对应的定义节点：
     * 仅当前标签在元模型文件（<code>*.xdef</code>）中且其名字空间与该元模型的名字空间相同时有效
     */
    NsDefNode defNodeInXdefNs;
    /**
     * 在元模型 <code>/nop/schema/xdsl.xdef</code> 绑定的名字空间（默认为 <code>x</code>）下，
     * 当前标签在该元模型中所对应的定义节点：所有标签的节点均由该元模型定义
     */
    NsDefNode defNodeInXdslNs;

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

    /** 当前标签是否对应元模型（<code>*.xdef</code>）定义节点 */
    public boolean isXdefDefNode() {
        return defNodeInXdefNs != null;
    }

    /** 当前标签是否对应 Xpl 定义节点 */
    public boolean isXplDefNode() {
        if (defNodeInSchema == null) {
            return false;
        } else if (XDefPsiHelper.isXplDefNode(defNodeInSchema)) {
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
        XLangTag parentTag = tag.getParentTag();

        // 根节点
        if (parentTag == null) {
            return createForRootTag(tagName, tag);
        }
        return createForChildTag(parentTag, tagName);
    }

    private static XLangTagMeta createForRootTag(String tagName, XLangTag tag) {
        // Note: 若标签在 Xpl 脚本中，则将返回 /nop/schema/xpl.xdef
        String schemaUrl = XDefPsiHelper.getSchemaPath(tag);
        if (schemaUrl == null) {
            return UNKNOWN_META;
        }

        // 若其元模型为 /nop/schema/xdef.xdef，则其自身也为元模型（*.xdef）
        boolean inSchema = XDslConstants.XDSL_SCHEMA_XDEF.equals(schemaUrl) //
                           || isXdefFile(tag.getContainingFile().getName());

        IXDefinition schema = XDefPsiHelper.loadSchema(schemaUrl);
        // 非 xdef/xpl 模型，全部视为 xdsl
        IXDefinition xdslSchema = XDefPsiHelper.getXDslDef();
        if (schema == null && !inSchema && !isXplXdefFile(schemaUrl)) {
            schema = xdslSchema;
        }
        // 若元模型加载失败，则不做识别
        if (schema == null) {
            return UNKNOWN_META;
        }

        IXDefNode defNodeInSchema = schema.getRootNode();
        // 如果其不在元模型中，则其根节点标签名必须与其 x:schema 所定义的根节点标签名保持一致，
        // 除非根节点被定义为 xdef:unknown-tag
        if (!inSchema && !defNodeInSchema.isUnknownTag() && !defNodeInSchema.getTagName().equals(tagName)) {
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

        IXDefinition xdefSchema = XDefPsiHelper.getXdefDef();
        String xdefNs = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDEF);
        NsDefNode defNodeInXdefNs = //
                inSchema ? NsDefNode.create(xdefNs, XDefKeys.DEFAULT.NS, xdefSchema) : null;

        String xdslNs = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDSL);
        NsDefNode defNodeInXdslNs = NsDefNode.create(xdslNs, XDslKeys.DEFAULT.NS, xdslSchema);

        XLangTagMeta tagMeta = new XLangTagMeta(tagName);
        tagMeta.defNodeInSchema = defNodeInSchema;
        tagMeta.checkNsInSchema = schema.getXdefCheckNs();
        tagMeta.defNodeInSelfSchema = selfSchema != null ? selfSchema.getRootNode() : null;
        tagMeta.defNodeInXdefNs = defNodeInXdefNs;
        tagMeta.defNodeInXdslNs = defNodeInXdslNs;

        return tagMeta;
    }

    private static XLangTagMeta createForChildTag(XLangTag parentTag, String tagName) {
        XLangTagMeta parentTagMeta = parentTag.getTagMeta();
        // 无定义节点
        if (parentTagMeta.isUnknown()) {
            return UNKNOWN_META;
        }

        IXDefNode defNodeInSchema = null;
        // 1. 先按名字空间匹配对应元模型中定义的节点
        // - 带 xpl 名字空间的节点的父节点只能是 Xpl 类型时才有效
        String tagNs = StringHelper.getNamespace(tagName);
        NsDefNode[] nsDefNodes = new NsDefNode[] {
                parentTagMeta.defNodeInXdefNs, parentTagMeta.defNodeInXdslNs
        };
        for (int i = 0; i < nsDefNodes.length; i++) {
            NsDefNode nsDefNode = nsDefNodes[i];
            if (nsDefNode == null) {
                continue;
            }

            NsDefNode childNsDefNode = nsDefNode.getChild(tagName);
            // 若当前标签的命名空间与相应元模型的名字空间相同，则其定义节点必须存在且确定的
            if (nsDefNode.xmlNs.equals(tagNs)) {
                if (childNsDefNode != null && !childNsDefNode.defNode.isUnknownTag()) {
                    defNodeInSchema = childNsDefNode.defNode;
                } else {
                    // TODO 在元模型 /xx/xx.xdef 中未定义 xx:xx
                    return UNKNOWN_META;
                }
            }

            // 就地更新为子节点
            nsDefNodes[i] = childNsDefNode;
        }
        NsDefNode defNodeInXdefNs = nsDefNodes[0];
        NsDefNode defNodeInXdslNs = nsDefNodes[1];

        // 2. 若父节点为 Xpl 类型节点，则直接从 xpl.xdef 中获取节点（含带 xpl 名字空间的节点）
        if (parentTagMeta.isXplDefNode()) {
            defNodeInXdefNs = null;
            defNodeInSchema = getChildDefNode(XDefPsiHelper.getXplDef().getRootNode(), tagName);
        }

        // 3. 从当前标签的父标签的定义节点中获取其定义节点
        if (defNodeInSchema == null) {
            defNodeInSchema = getChildDefNode(parentTagMeta.defNodeInSchema, tagName);
        }

        // 4. 带名字空间的节点：其在元模型中已显式定义，或者为自由增添的无约束节点
        if (tagNs != null) {
            // Note: 在 xdsl.xdef 中的定义节点，不属于元模型定义节点
            if (defNodeInXdslNs != null && defNodeInSchema == defNodeInXdslNs.defNode) {
                defNodeInXdefNs = null;
            }

            // 若为 xdef.xdef 交叉定义（自己定义自己），则取定义节点 xdef:unknown-tag
            if (defNodeInXdefNs != null //
                && !defNodeInXdefNs.xmlNs.equals(tagNs) //
                && defNodeInXdefNs.defNs.equals(tagNs) //
            ) {
                defNodeInSchema = defNodeInXdefNs.defNode;
            }
            // 需要校验的名字空间下的节点必须在其元模型中显式定义
            else if (parentTagMeta.checkNsInSchema != null //
                     && parentTagMeta.checkNsInSchema.contains(tagNs) //
            ) {
                if (defNodeInSchema != null && defNodeInSchema.isUnknownTag()) {
                    // TODO 名字空间 xx 下的标签必须在 /xx/xx.xdef 中显式定义
                    return UNKNOWN_META;
                }
            }
            // 否则，其定义节点必然为 xdsl 的 xdef:unknown-tag 节点
            else if (defNodeInSchema == null) {
                defNodeInSchema = getChildDefNode(XDefPsiHelper.getXDslDef().getRootNode(), tagName);
            }
        }

        if (defNodeInSchema == null) {
            return UNKNOWN_META;
        }

        XLangTagMeta tagMeta = new XLangTagMeta(tagName);
        tagMeta.parent = parentTagMeta;
        tagMeta.defNodeInSchema = defNodeInSchema;
        tagMeta.checkNsInSchema = parentTagMeta.checkNsInSchema;
        tagMeta.defNodeInSelfSchema = getChildDefNode(parentTagMeta.defNodeInSelfSchema, tagName);
        tagMeta.defNodeInXdefNs = defNodeInXdefNs;
        tagMeta.defNodeInXdslNs = defNodeInXdslNs;

        return tagMeta;
    }

    private static boolean isXdefFile(String filename) {
        return filename.endsWith(".xdef");
    }

    private static boolean isXplXdefFile(String filename) {
        return XDslConstants.XDSL_SCHEMA_XPL.equals(filename);
    }

    private static IXDefNode getChildDefNode(IXDefNode defNode, String tagName) {
        return defNode != null ? defNode.getChild(tagName) : null;
    }

    /** 通过名字空间引入的元模型中的定义节点 */
    private static class NsDefNode {
        /**
         * 引入 {@link #defNode} 的元模型时所绑定的名字空间，
         * 比如，在元模型 <code>/nop/schema/xdef.xdef</code> 中通过
         * <code>xmlns:meta="/nop/schema/xdef.xdef"</code> 引入 xdef 模型，
         * 则 <code>meta</code> 便为该名字空间，所有在该名字空间下的节点和属性均由该 xdef 模型定义
         */
        final String xmlNs;
        /**
         * 定义 {@link #defNode} 时所限定的名字空间，
         * 比如，在元模型 <code>/nop/schema/xdef.xdef</code> 中定义的节点和属性均被限定在
         * <code>xdef</code> 名字空间下，因此，<code>xdef</code> 即为其值
         */
        final String defNs;

        /** 定义节点 */
        final IXDefNode defNode;

        NsDefNode(String xmlNs, String defNs, IXDefNode defNode) {
            this.xmlNs = xmlNs;
            this.defNs = defNs;

            this.defNode = defNode;
        }

        public NsDefNode getChild(String childTagName) {
            IXDefNode childDefNode;
            if (childTagName.startsWith(xmlNs + ':')) {
                if (!xmlNs.equals(defNs)) {
                    childTagName = defNs + childTagName.substring(xmlNs.length());
                }
                childDefNode = getChildDefNode(defNode, childTagName);
            } else {
                childDefNode = defNode.getXdefUnknownTag();
            }

            if (childDefNode == null) {
                return null;
            }
            // Note: 复用实例，避免反复新建
            if (defNode == childDefNode) {
                return this;
            }
            return new NsDefNode(xmlNs, defNs, childDefNode);
        }

        public static NsDefNode create(String xmlNs, String defNs, IXDefinition schema) {
            if (schema == null) {
                return null;
            }

            if (StringHelper.isBlank(xmlNs)) {
                xmlNs = defNs;
            }

            return new NsDefNode(xmlNs, defNs, schema.getRootNode());
        }
    }
}
