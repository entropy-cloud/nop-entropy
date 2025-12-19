/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.psi;

import java.util.Objects;
import java.util.Set;

import com.intellij.psi.PsiElement;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.idea.plugin.lang.XLangDocumentation;
import io.nop.idea.plugin.lang.xlib.XlibTagMeta;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefSubComment;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefBodyType;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.impl.XDefinition;
import io.nop.xlang.xdef.parse.XDefTypeDeclParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XlibConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import static io.nop.idea.plugin.messages.NopPluginBundle.BUNDLE;

/**
 * 标签的{@link IXDefNode 定义节点}信息
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-10-21
 */
public class XLangTagMeta {
    private static final XDefTypeDecl STD_DOMAIN_XDEF_REF = //
            new XDefTypeDeclParser().parseFromText(null, XDefConstants.STD_DOMAIN_XDEF_REF);

    final XLangTag tag;
    final String errorMsg;

    XLangTagMeta parent;

    /**
     * 当前标签在元模型中所对应的定义节点：
     * 元模型一般由根节点上的 {@code x:schema} 指定，
     * 但 Xpl 类型节点的元模型始终为 {@code /nop/schema/xpl.xdef}
     */
    IXDefNode defNodeInSchema;
    /**
     * 当前标签所在的元模型中通过 {@code xdef:check-ns} 所设置的待校验的名字空间列表：
     * 节点或属性的名字空间若在该列表中，则其必须在元模型中显式定义
     */
    Set<String> checkNsInSchema;

    /**
     * 若当前标签在元模型文件（{@code *.xdef}）中，
     * 则用于记录当前标签在该元模型中的定义
     */
    IXDefNode defNodeInSelfSchema;
    /**
     * 若当前标签在元模型文件（{@code *.xdef}）中，
     * 则用于记录该元模型 {@code xdef:check-ns} 属性的值
     */
    Set<String> checkNsInSelfSchema;

    /**
     * 当前标签所对应的元模型 {@code /nop/schema/xdsl.xdef} 中的定义节点：
     * 所有的 DSL （包括元模型自身）均由该模型定义
     */
    IXDefNode xdslDefNode;

    XDefKeys xdefKeys;
    XDslKeys xdslKeys;

    XLangTagMeta(@NotNull XLangTag tag) {
        this(tag, null);
    }

    XLangTagMeta(@NotNull XLangTag tag, String errorMsg) {
        this.tag = tag;
        this.errorMsg = errorMsg;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        XLangTagMeta that = (XLangTagMeta) o;
        return tag.equals(that.tag)
               && Objects.equals(errorMsg, that.errorMsg)
               && Objects.equals(parent, that.parent)
               && Objects.equals(defNodeInSchema, that.defNodeInSchema)
               && Objects.equals(checkNsInSchema, that.checkNsInSchema)
               && Objects.equals(defNodeInSelfSchema, that.defNodeInSelfSchema)
               && Objects.equals(checkNsInSelfSchema, that.checkNsInSelfSchema)
               && Objects.equals(xdslDefNode, that.xdslDefNode)
               && Objects.equals(xdefKeys, that.xdefKeys)
               && Objects.equals(xdslKeys, that.xdslKeys);
    }

    public String getTagName() {
        return tag.getName();
    }

    public String getErrorMsg() {
        return this.errorMsg;
    }

    public IXDefNode getDefNodeInSchema() {
        return defNodeInSchema;
    }

    public String getDefNodeSchemaPath() {
        return XmlPsiHelper.getNopVfsPath(defNodeInSchema);
    }

    public IXDefNode getDefNodeInSelfSchema() {
        return defNodeInSelfSchema;
    }

    public IXDefNode getXDslDefNode() {
        return this.xdslDefNode;
    }

    public XDefKeys getXdefKeys() {
        return this.xdefKeys;
    }

    public XDslKeys getXdslKeys() {
        return this.xdslKeys;
    }

    /** 当前标签是否存在解析异常 */
    public boolean hasError() {
        return errorMsg != null;
    }

    /** 当前标签是否在元模型 {@code *.xdef} 中 */
    public boolean isInAnySchema() {
        return xdefKeys != null;
    }

    /** 当前标签是否在元模型 {@code /nop/schema/xdef.xdef} 中 */
    public boolean isInXdefSchema() {
        return checkNsInSelfSchema != null //
               && checkNsInSelfSchema.contains(XDefKeys.DEFAULT.NS) //
               && !XDefKeys.DEFAULT.equals(xdefKeys);
    }

    /** 当前标签是否为 Xpl 类型节点，或者其对应元模型 {@code /nop/schema/xpl.xdef} 中的定义节点 */
    public boolean isXplNode() {
        if (defNodeInSchema == null) {
            return false;
        } else if (XDefPsiHelper.isXplTypeNode(defNodeInSchema)) {
            return true;
        }

        String vfsPath = getDefNodeSchemaPath();
        if (isXplXdefFile(vfsPath)) {
            return true;
        }

        return isXlibSourceNode();
    }

    /** 当前标签是否为 xlib 的 {@code <source/>} 节点 */
    public boolean isXlibSourceNode() {
        if (defNodeInSchema == null) {
            return false;
        }

        String vfsPath = getDefNodeSchemaPath();
        // xlib.xdef 中的 source 标签被设置为 xml 类型，是因为在获取 XplLib 模型的时候会根据 xlib.xdef 来解析，
        // 但此时这个 source 段无法自动进行编译，必须结合它的 outputMode 和 attrs 配置等才能决定。
        // 因此，需将其子节点同样视为 Xpl 节点处理
        return XDslConstants.XDSL_SCHEMA_XLIB.equals(vfsPath)
               && XlibConstants.SOURCE_NAME.equals(getTagName())
               && XDefConstants.STD_DOMAIN_XML.equals(XDefPsiHelper.getDefNodeType(defNodeInSchema));
    }

    /** 当前标签是否为 {@code <xdef:unknown-tag/>} */
    public boolean isXDefUnknownTag() {
        return xdefKeys.UNKNOWN_TAG.equals(getTagName());
    }

    /**
     * 获取当前标签 {@link #getDefNodeInSchema()} 的 {@link XDefKeys#VALUE xdef:value} 定义，
     * 即，其子节点（包括文本节点）对应的{@link XDefTypeDecl 类型}
     */
    public XDefTypeDecl getXdefValue() {
        IXDefNode defNode = getDefNodeInSchema();

        return defNode != null ? defNode.getXdefValue() : null;
    }

    /** 当前标签是否允许重复 */
    public boolean canBeMultipleTag() {
        IXDefNode defNode = getDefNodeInSchema();

        return (defNode != null && defNode.isAllowMultiple()) || isXplNode();
    }

    /** 当前标签是否允许有多个子标签 */
    public boolean canHasMultipleChildTag() {
        IXDefNode defNode = getDefNodeInSchema();

        // xdef:body-type="union" 的节点只能有一个子节点
        return defNode != null && !XDefBodyType.union.equals(defNode.getXdefBodyType());
    }

    /** 检查是否允许包含指定的子标签，并返回检查结果 */
    public ChildTagAllowedMode checkChildTagAllowed(XLangTagMeta childTagMeta) {
        String xdefDefineTagName = childTagMeta.xdefKeys != null ? childTagMeta.xdefKeys.DEFINE : null;
        // 在元模型中不能在 xdef:define 中嵌套 xdef:define
        if (xdefDefineTagName != null //
            && xdefDefineTagName.equals(childTagMeta.getTagName()) //
        ) {
            XLangTag parentTag = tag;
            do {
                if (xdefDefineTagName.equals(parentTag.getName())) {
                    return ChildTagAllowedMode.can_not_be_nested_by_same_name_tag;
                }
                parentTag = parentTag.getParentTag();
            } while (parentTag != null);
        }

        boolean allowMultipleChild = canHasMultipleChildTag();
        boolean allowMultipleSelf = childTagMeta.canBeMultipleTag();
        if (allowMultipleChild && allowMultipleSelf) {
            return ChildTagAllowedMode.allowed;
        }

        String childTagName = childTagMeta.getTagName();
        for (PsiElement child : tag.getChildren()) {
            // 仅检查在指定标签之前的标签
            if (child == childTagMeta.tag) {
                break;
            } else if (!(child instanceof XLangTag)) {
                continue;
            }

            XLangTag t = (XLangTag) child;
            String tName = t.getName();
            if (!allowMultipleSelf //
                && Objects.equals(tName, childTagName) //
            ) {
                return ChildTagAllowedMode.can_not_be_multiple;
            } //
            else if (!allowMultipleChild
                     // 若已出现过定义的子标签，则不能再出现其他定义的子标签
                     && defNodeInSchema != null //
                     && defNodeInSchema.getChild(tName) != null //
                     && defNodeInSchema.getChild(childTagName) != null //
            ) {
                return ChildTagAllowedMode.only_at_most_one;
            }
        }
        return ChildTagAllowedMode.allowed;
    }

    /**
     * 获取当前标签上指定属性的{@link IXDefAttribute 属性定义}
     * <p/>
     * 其可识别 {@code xdef}、{@code x}、{@code xpl} 名字空间下已定义的属性
     */
    public IXDefAttribute getDefAttr(XLangAttribute attr) {
        String attrName = attr.getName();
        if (hasError()) {
            return getDefAttrOnNode(attr, null, attrName, null, xdefKeys, null);
        }

        String ns = StringHelper.getNamespace(attrName);
        boolean hasXDslNs = xdslKeys.NS.equals(ns);

        IXDefAttribute defAttr;
        // 取 xdsl.xdef 中声明的属性
        if (hasXDslNs) {
            defAttr = getXDslDefNodeAttr(attr);
        } else {
            defAttr = getDefNodeAttr(attr);
        }
        return defAttr;
    }

    /** 获取当前标签的 {@link #getDefNodeInSchema()} 上指定属性的定义 */
    private IXDefAttribute getDefNodeAttr(XLangAttribute attr) {
        String attrName = attr.getName();

        // 在元元模型 /nop/schema/xdef.xdef 中，名字空间为 meta 的属性均由同名但以 xdef 为名字空间的属性定义，
        // 而以 xdef 为名字空间的属性，则均由 meta:unknown-attr 定义。也即，二者形成交叉定义
        if (isInXdefSchema() && attrName.startsWith(XDefKeys.DEFAULT.NS + ':')) {
            attrName = "*";
        } else if (isInAnySchema()) {
            attrName = XLangTag.replaceXmlNs(attrName, xdefKeys.NS, XDefKeys.DEFAULT.NS);
        }

        IXDefAttribute defAttr = getDefAttrOnNode(attr,
                                                  defNodeInSchema,
                                                  attrName,
                                                  attr.getValue(),
                                                  xdefKeys,
                                                  checkNsInSchema);

        if (defAttr == null || defAttr.isUnknownAttr()) {
            XlibTagMeta xlibTag = tag.getXlibTagMeta();

            if (xlibTag != null) {
                return xlibTag.getAttribute(attrName);
            }
        }
        return defAttr;
    }

    /** 获取当前标签对应的 {@link #getXDslDefNode()} 上指定属性的定义 */
    private IXDefAttribute getXDslDefNodeAttr(XLangAttribute attr) {
        String attrName = attr.getName();
        // Note: xdsl.xdef 的属性在固定的名字空间 x 中声明
        attrName = XLangTag.replaceXmlNs(attrName, xdslKeys.NS, XDslKeys.DEFAULT.NS);

        return getDefAttrOnNode(attr, xdslDefNode, attrName, null, xdefKeys, Set.of(XDslKeys.DEFAULT.NS));
    }

    /** 获取当前标签的说明文档 */
    public XLangDocumentation getTagDocumentation() {
        if (hasError()) {
            return null;
        }

        IXDefNode defNode = null;
        String tagName = tag.getName();
        String tagNs = StringHelper.getNamespace(tagName);

        // 单独处理元模型中的 xdef:define 节点：其由父节点记录定义信息
        if (xdefKeys != null && xdefKeys.DEFINE.equals(tagName)) {
            String xdefName = tag.getAttributeValue(xdefKeys.NAME);

            if (xdefName != null && parent.defNodeInSelfSchema != null) {
                defNode = ((XDefinition) parent.defNodeInSelfSchema).getXdefDefine(xdefName);
            }
        }
        // 不在元元模型中的以 xdef 为名字空间的节点（不含 xdef:unknown-tag），显示其定义文档
        else if (!isInXdefSchema() //
                 && xdefKeys != null && xdefKeys.NS.equals(tagNs) //
                 && !xdefKeys.UNKNOWN_TAG.equals(tagName) //
        ) {
            defNode = defNodeInSchema;
        }
        // 元模型中的自定义节点，显示元模型中的文档
        else if (isInAnySchema() //
                 && !xdslKeys.NS.equals(tagNs) //
                 && !isXplNode() //
        ) {
            // Note: 可能会因元模型自身解析异常而无法得到节点定义
            defNode = defNodeInSelfSchema;
        }
        // 其余 dsl 均显示节点的定义文档
        else {
            defNode = defNodeInSchema;
        }

        if (defNode == null) {
            return null;
        }

        XlibTagMeta xlibTag = tag.getXlibTagMeta();
        if (xlibTag != null) {
            return xlibTag.getDocumentation();
        }

        XLangDocumentation doc = new XLangDocumentation(defNode);
        doc.setMainTitle(tagName);

        IXDefComment comment = defNode.getComment();
        if (comment != null) {
            doc.setSubTitle(comment.getMainDisplayName());
            doc.setDesc(comment.getMainDescription());
        }

        return doc;
    }

    /** 获取当前标签指定属性的说明文档 */
    public XLangDocumentation getAttrDocumentation(XLangAttribute attr) {
        String attrName = attr.getName();
        String attrNs = StringHelper.getNamespace(attrName);
        if ("xmlns".equals(attrNs) || hasError()) {
            return null;
        }

        String mainTitle = attrName;
        String tagName = tag.getName();

        IXDefNode defNode = null;
        Set<String> checkNsSet = checkNsInSchema;
        // 单独处理元模型中的 xdef:define 节点：其由父节点记录定义信息
        if (xdefKeys != null && xdefKeys.DEFINE.equals(tagName) //
            && !xdefKeys.NS.equals(attrNs) && !xdslKeys.NS.equals(attrNs) //
        ) {
            String xdefName = tag.getAttributeValue(xdefKeys.NAME);

            if (xdefName != null && parent.defNodeInSelfSchema != null) {
                attrName = XLangTag.replaceXmlNs(attrName, xdefKeys.NS, XDefKeys.DEFAULT.NS);
                defNode = ((XDefinition) parent.defNodeInSelfSchema).getXdefDefine(xdefName);
            }
        }
        // 元元模型中 xdef 名字空间下的属性
        else if (isInXdefSchema() && XDefKeys.DEFAULT.NS.equals(attrNs)) {
            defNode = defNodeInSelfSchema;
        }
        // x 名字空间下的属性
        else if (XDslKeys.DEFAULT.NS.equals(attrNs) || xdslKeys.NS.equals(attrNs)) {
            attrName = XLangTag.replaceXmlNs(attrName, xdslKeys.NS, XDslKeys.DEFAULT.NS);
            defNode = xdslDefNode;
            checkNsSet = Set.of(XDslKeys.DEFAULT.NS);
        } //
        else {
            attrName = XLangTag.replaceXmlNs(attrName, xdefKeys != null ? xdefKeys.NS : null, XDefKeys.DEFAULT.NS);

            // 对于元模型，优先取其自身定义节点上的属性文档
            defNode = defNodeInSelfSchema;
            if (defNode == null || defNode.getAttribute(attrName) == null) {
                defNode = defNodeInSchema;
            }
        }

        if (defNode == null) {
            return null;
        }

        IXDefAttribute defAttr = getDefAttrOnNode(attr, defNode, attrName, null, xdefKeys, checkNsSet);
        if (XLangAttribute.isNullOrErrorDefAttr(defAttr)) {
            return null;
        }

        if (defAttr.isUnknownAttr()) {
            XlibTagMeta xlibTag = tag.getXlibTagMeta();

            if (xlibTag != null) {
                return xlibTag.getAttrDocumentation(attrName);
            }
        }

        XLangDocumentation doc = new XLangDocumentation(defAttr);
        doc.setMainTitle(mainTitle);
        doc.setAdditional(defAttr instanceof XLangAttribute.XDefAttributeWithTypeAny);

        IXDefComment nodeComment = defNode.getComment();
        if (nodeComment != null) {
            IXDefSubComment attrComment = nodeComment.getSubComments().get(attrName);
            if (attrComment == null && defAttr.isUnknownAttr()) {
                attrComment = nodeComment.getSubComments().get(XDefKeys.DEFAULT.UNKNOWN_ATTR);
            }

            if (attrComment != null) {
                doc.setSubTitle(attrComment.getDisplayName());
                doc.setDesc(attrComment.getDescription());
            }
        }

        return doc;
    }

    // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

    /**
     * Note: 需要在 {@link io.nop.idea.plugin.resource.ProjectEnv#withProject ProjectEnv#withProject}
     * 中调用该函数
     */
    public static XLangTagMeta create(XLangTag tag) {
        XLangTag parentTag = tag.getParentTag();

        try {
            // 根节点
            if (parentTag == null) {
                return createForRootTag(tag);
            }
            return createForChildTag(tag, parentTag);
        } catch (Exception e) {
            String msg = ErrorMessageManager.instance().getRootCause(e).getMessage();
            return errorTag(tag, "xlang.parser.tag-meta.creating-exception", msg);
        }
    }

    protected static XLangTagMeta errorTag(
            @NotNull XLangTag tag, @NotNull @PropertyKey(resourceBundle = BUNDLE) String msgKey,
            Object @NotNull ... msgParams
    ) {
        return new XLangTagMeta(tag, NopPluginBundle.message(msgKey, msgParams));
    }

    private static XDefAttribute errorAttr(
            @NotNull XLangAttribute attr, @NotNull @PropertyKey(resourceBundle = BUNDLE) String msgKey,
            Object @NotNull ... msgParams
    ) {
        return new XLangAttribute.XDefAttributeWithError(attr.getName(), NopPluginBundle.message(msgKey, msgParams));
    }

    private static XLangTagMeta createForRootTag(XLangTag tag) {
        String tagName = tag.getName();
        String tagNs = StringHelper.getNamespace(tagName);

        String xdslNs = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDSL);
        XDslKeys xdslKeys = XDslKeys.of(xdslNs);

        // Note: 若标签在 Xpl 脚本中，则将返回 /nop/schema/xpl.xdef
        String schemaPath = XDefPsiHelper.getSchemaPath(tag);
        if (schemaPath == null) {
            return errorTag(tag, "xlang.parser.tag-meta.schema-not-specified", xdslKeys.SCHEMA, tagName);
        }

        IXDefinition schema = XDefPsiHelper.loadSchema(schemaPath);
        IXDefinition xdslSchema = XDefPsiHelper.getXDslDef();

        // 若其元模型为 /nop/schema/xdef.xdef，则其自身也为元模型（*.xdef）
        boolean inSchema = XDslConstants.XDSL_SCHEMA_XDEF.equals(schemaPath) //
                           || isXdefFile(tag.getContainingFile().getName());

        // 非 xdef/xpl 模型，全部视为 xdsl
        if (schema == null && !inSchema && !isXplXdefFile(schemaPath)) {
            schema = xdslSchema;
        }
        // 若元模型加载失败，则不做识别
        if (schema == null) {
            return errorTag(tag, "xlang.parser.tag-meta.schema-loading-failed", schemaPath);
        }

        String xdefNs = XmlPsiHelper.getXmlnsForUrl(tag, XDslConstants.XDSL_SCHEMA_XDEF);
        XDefKeys xdefKeys = XDefKeys.of(xdefNs);

        // Note: xdef 名字空间的根节点必须为 xdef.xdef 中已定义的节点
        if (inSchema && xdefKeys.NS.equals(tagNs)) {
            if (getChildXdefDefNode(schema, tagName, xdefKeys) == null) {
                return errorTag(tag,
                                "xlang.parser.tag-meta.ns-tag-not-defined-in-schema",
                                tagName,
                                XDefKeys.DEFAULT.NS,
                                XDslConstants.XDSL_SCHEMA_XDEF);
            }
        }

        IXDefNode defNodeInSchema = schema.getRootNode();
        // 如果其不在元模型中，则其根节点标签名必须与其 x:schema 所定义的根节点标签名保持一致，
        // 除非根节点被定义为 xdef:unknown-tag
        if (!inSchema && !defNodeInSchema.isUnknownTag() //
            && !defNodeInSchema.getTagName().equals(tagName) //
        ) {
            return errorTag(tag,
                            "xlang.parser.tag-meta.root-tag-name-not-match-schema-root",
                            tagName,
                            defNodeInSchema.getTagName(),
                            schemaPath);
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

        XLangTagMeta tagMeta = new XLangTagMeta(tag);
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

    private static XLangTagMeta createForChildTag(XLangTag tag, XLangTag parentTag) {
        String tagName = tag.getName();
        String tagNs = StringHelper.getNamespace(tagName);

        XLangTagMeta parentTagMeta = parentTag.getTagMeta();
        // 无定义节点
        if (parentTagMeta.hasError()) {
            return errorTag(tag, "xlang.parser.tag-meta.parent-not-defined", parentTagMeta.getTagName());
        }

        XDefKeys xdefKeys = parentTagMeta.xdefKeys;
        XDslKeys xdslKeys = parentTagMeta.xdslKeys;

        IXDefNode defNodeInSchema = null;
        // 1. 获取确定的 xdsl 节点，如 <x:gen-extends/>、<x:post-extends/>等
        if (xdslKeys.NS.equals(tagNs)) {
            String xdslTagName = XLangTag.replaceXmlNs(tagName, XDslKeys.DEFAULT.NS);
            defNodeInSchema = getChildDefNode(parentTagMeta.xdslDefNode, xdslTagName);

            if (defNodeInSchema.isUnknownTag()) {
                return errorTag(tag,
                                "xlang.parser.tag-meta.ns-tag-not-defined-in-schema",
                                tagName,
                                XDslKeys.DEFAULT.NS,
                                XDslConstants.XDSL_SCHEMA_XDSL);
            }
        }

        // 2. 若父节点为 Xpl 类型节点，则直接从 xpl.xdef 中获取节点（含带 xpl 名字空间的节点）
        if (defNodeInSchema == null && parentTagMeta.isXplNode()) {
            defNodeInSchema = getChildDefNode(XDefPsiHelper.getXplDef().getXdefUnknownTag(), tagName);

            if (XplConstants.XPL_NS.equals(tagNs) && defNodeInSchema.isUnknownTag()) {
                return errorTag(tag,
                                "xlang.parser.tag-meta.ns-tag-not-defined-in-schema",
                                tagName,
                                XplConstants.XPL_NS,
                                XDslConstants.XDSL_SCHEMA_XPL);
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
            defNodeInSchema = getChildXdefDefNode(parentTagMeta.defNodeInSchema, tagName, xdefKeys);
            if (defNodeInSchema == null) {
                return errorTag(tag,
                                "xlang.parser.tag-meta.ns-tag-not-defined-in-schema",
                                tagName,
                                XDefKeys.DEFAULT.NS,
                                XDslConstants.XDSL_SCHEMA_XDEF);
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
                if (defNodeInSchema == null || defNodeInSchema.isUnknownTag()) {
                    String schemaPath = parentTagMeta.getDefNodeSchemaPath();
                    return errorTag(tag,
                                    "xlang.parser.tag-meta.ns-tag-should-be-defined-in-schema",
                                    tagName,
                                    tagNs,
                                    schemaPath);
                }
            }
            // 否则，其定义节点必然为 xdsl 的 xdef:unknown-tag 节点
            else if (defNodeInSchema == null) {
                defNodeInSchema = parentTagMeta.xdslDefNode.getXdefUnknownTag();
            }
        }

        if (defNodeInSchema == null) {
            XDefTypeDecl xdefValue = parentTagMeta.getXdefValue();
            // Note: 对于 xjson、xml 等支持 xml 类型的节点，其子节点均未在元模型中定义，
            // 故而，将其视为 xdsl 的 xdef:unknown-tag 节点
            if (xdefValue != null) {
                if (xdefValue.isSupportBody(StdDomainRegistry.instance())) {
                    defNodeInSchema = parentTagMeta.xdslDefNode.getXdefUnknownTag();
                } else {
                    return errorTag(tag,
                                    "xlang.parser.tag-meta.parent-not-allowed-to-have-child",
                                    parentTagMeta.getTagName());
                }
            }
        }

        if (defNodeInSchema == null) {
            return errorTag(tag, "xlang.parser.tag-meta.tag-not-defined", tagName);
        }

        XLangTagMeta tagMeta = new XLangTagMeta(tag);
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

    /** 获取 {@link IXDefNode} 上指定属性的定义 */
    private static IXDefAttribute getDefAttrOnNode(
            XLangAttribute attr, IXDefNode defNode, //
            String attrName, String attrValue, //
            XDefKeys xdefKeys, Set<String> checkNsInSchema //
    ) {
        String xmlnsPrefix = "xmlns:";
        if (attrName.startsWith(xmlnsPrefix)) {
            // 忽略 xmlns:biz="biz" 形式的属性
            if (attrName.equals(xmlnsPrefix + attrValue)) {
                return null;
            }

            XDefAttribute at = new XDefAttribute();
            at.setName(attrName);
            at.setType(STD_DOMAIN_XDEF_REF);

            return at;
        }

        if (defNode == null) {
            return errorAttr(attr, "xlang.parser.tag-meta.attr-on-undefined-tag", attrName);
        }

        IXDefAttribute defAttr = defNode.getAttribute(attrName);
        if (defAttr != null) {
            return defAttr;
        }

        // 属性未显式定义
        String attrNameNs = StringHelper.getNamespace(attrName);
        if (attrNameNs != null) {
            if (checkNsInSchema == null || !checkNsInSchema.contains(attrNameNs)) {
                // 对于 xdef:check-ns 中不做校验的名字空间，且不在元模型中的，其下的属性均为任意类型
                if (xdefKeys == null) {
                    return new XLangAttribute.XDefAttributeWithTypeAny(attrName);
                }
            }
            // 对于 xdef:check-ns 中需校验的名字空间，其下属性必须显式定义
            else {
                String schemaPath = XmlPsiHelper.getNopVfsPath(defNode);
                return errorAttr(attr,
                                 "xlang.parser.tag-meta.check-ns-attr-not-defined",
                                 attrName,
                                 attrNameNs,
                                 schemaPath);
            }
        }

        // 在节点上定义了 xdef:unknown-attr
        XDefTypeDecl xdefUnknownAttrType = defNode.getXdefUnknownAttr();
        if (xdefUnknownAttrType == null) {
            return errorAttr(attr, "xlang.parser.tag-meta.attr-not-defined", attrName);
        }

        // Note: 在 IXDefNode 中，对 xdef:unknown-attr 只记录了类型，
        // 并没有 IXDefAttribute 实体（详见 XDefinitionParser#parseNode），
        // 因此，需要单独构造
        XDefAttribute xdefUnknownAttr = new XDefAttribute() {
            @Override
            public boolean isUnknownAttr() {
                return true;
            }
        };

        String xdefUnknownAttrName = Objects.requireNonNullElse(xdefKeys, XDefKeys.DEFAULT).UNKNOWN_ATTR;
        xdefUnknownAttr.setName(xdefUnknownAttrName);
        xdefUnknownAttr.setType(xdefUnknownAttrType);

        // Note: 在需要时，通过节点位置再定位具体的属性位置
        SourceLocation loc = null;
        // 在存在节点继承的情况下，选择最上层定义的同类型的 xdef:unknown-attr 属性
        IXDefNode refNode = defNode;
        while (refNode != null) {
            if (refNode.getXdefUnknownAttr() != xdefUnknownAttrType) {
                break;
            }

            loc = refNode.getLocation();
            refNode = refNode.getRefNode();
        }
        xdefUnknownAttr.setLocation(loc);

        return xdefUnknownAttr;
    }

    private static boolean isXdefFile(String filename) {
        return filename.endsWith(".xdef");
    }

    private static boolean isXplXdefFile(String filename) {
        return XDslConstants.XDSL_SCHEMA_XPL.equals(filename);
    }

    /** 获得指定标签名的子定义节点，或者 {@code xdef:unknown-tag} 定义节点 */
    private static IXDefNode getChildDefNode(IXDefNode defNode, String tagName) {
        return defNode != null ? defNode.getChild(tagName) : null;
    }

    private static IXDefNode getChildXdefDefNode(IXDefNode defNode, String tagName, XDefKeys xdefKeys) {
        String xdefTagName = XLangTag.replaceXmlNs(tagName, XDefKeys.DEFAULT.NS);
        IXDefNode childDefNode = getChildDefNode(defNode, xdefTagName);

        if (childDefNode.isUnknownTag() && !xdefKeys.UNKNOWN_TAG.equals(tagName)) {
            // 在名字空间 xx 对应的元模型 xdef.xdef 中未定义 xx:xx
            childDefNode = null;
        }
        return childDefNode;
    }

    public enum ChildTagAllowedMode {
        /** 允许 */
        allowed,
        /** 最多只能有一个子标签 */
        only_at_most_one,
        /** 子标签不能重复 */
        can_not_be_multiple,
        /** 子标签不能被同名子标签嵌套 */
        can_not_be_nested_by_same_name_tag,
    }
}
