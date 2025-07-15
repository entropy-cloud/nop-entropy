package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.parse.XDefTypeDeclParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xpl.xlib.XlibConstants;
import org.jetbrains.annotations.NotNull;

/**
 * {@link XNode} 标签（其名字含名字空间）
 * <p/>
 * 负责识别标签、属性、属性值的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangTag extends XmlTagImpl {
    private static final XDefTypeDecl STD_DOMAIN_XDEF_REF = new XDefTypeDeclParser().parseFromText(null,
                                                                                                   XDefConstants.STD_DOMAIN_XDEF_REF);

    private SchemaMeta schemaMeta;

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getElementType() + "('" + getName() + "')";
    }

    @Override
    public XLangTag getParentTag() {
        return (XLangTag) super.getParentTag();
    }

    public XLangTag getRootTag() {
        XLangTag tag = this;

        do {
            XLangTag parent = tag.getParentTag();
            if (parent == null) {
                return tag;
            }
            tag = parent;
        } while (true);
    }

    @Override
    public void clearCaches() {
        this.schemaMeta = null;

        super.clearCaches();
    }

    @Override
    public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
        // TODO 通过 CachedValuesManager.getCachedValue 缓存结果，并支持在依赖项变更时丢弃缓存结果
//        if (hints == PsiReferenceService.Hints.NO_HINTS) {
//            return CachedValuesManager.getCachedValue(this,
//                                                      () -> CachedValueProvider.Result.create(getReferencesImpl(
//                                                                                                      PsiReferenceService.Hints.NO_HINTS),
//                                                                                              PsiModificationTracker.MODIFICATION_COUNT,
//                                                                                              externalResourceModificationTracker(
//                                                                                                      myTag))).clone();
//        }

        // TODO 合并 xml 与 xlang 的引用
        return super.getReferences(hints);
    }

    /**
     * 获取当前标签所在的元模型
     *
     * @see SchemaMeta#schemaDef
     */
    private IXDefinition getSchemaDef() {
        return getSchemaMeta().schemaDef;
    }

    /**
     * 获取当前标签在{@link #getSchemaDef() 元模型}中对应的节点
     *
     * @see SchemaMeta#schemaDefNode
     */
    public IXDefNode getSchemaDefNode() {
        return getSchemaMeta().schemaDefNode;
    }

    /**
     * 获取当前标签上指定属性在元模型中的定义
     * <p/>
     * 在元元模型 xdef.xdef 中，名字空间 <code>meta</code> 的属性均由同名的以
     * <code>xdef</code> 为名字空间的属性定义，而以 <code>xdef</code>
     * 为名字空间的属性，则均由 <code>meta:unknown-attr</code> 定义。
     * 即，二者形成交叉定义
     */
    public IXDefAttribute getSchemaDefNodeAttr(String attrName) {
        // 在元元模型中，以 xdef 为名字空间的属性，需迫使其以 meta:unknown-attr 作为其属性定义
        if (isInXDefXDef() && attrName.startsWith(XDefKeys.DEFAULT.NS + ':')) {
            attrName = "*";
        }
        // xdef.xdef 的属性在固定的名字空间 x 中声明
        else {
            attrName = changeNamespace(attrName, getXDefKeys().NS, XDefKeys.DEFAULT.NS);
        }

        return getXDefNodeAttr(getSchemaDefNode(), attrName);
    }

    /**
     * 获取当前标签在 xdsl.xdef 中对应的节点
     *
     * @see SchemaMeta#xdslDefNode
     */
    public IXDefNode getXDslDefNode() {
        return getSchemaMeta().xdslDefNode;
    }

    /** 获取当前标签上指定属性在 xdsl.xdef 中的定义 */
    public IXDefAttribute getXDslDefNodeAttr(String attrName) {
        // Note: xdsl.xdef 的属性在固定的名字空间 x 中声明
        attrName = changeNamespace(attrName, getXDslKeys().NS, XDslKeys.DEFAULT.NS);

        return getXDefNodeAttr(getXDslDefNode(), attrName);
    }

    /** @see SchemaMeta#selfDef */
    public IXDefinition getSelfDef() {
        return getSchemaMeta().selfDef;
    }

    /** @see SchemaMeta#selfDefNode */
    public IXDefNode getSelfDefNode() {
        return getSchemaMeta().selfDefNode;
    }

    /** 获取 {@link #getSelfDefNode()} 上指定的属性 */
    public IXDefAttribute getSelfDefNodeAttr(String attrName) {
        return getXDefNodeAttr(getSelfDefNode(), attrName);
    }

    /** @see SchemaMeta#xdefKeys */
    public XDefKeys getXDefKeys() {
        return getSchemaMeta().xdefKeys;
    }

    /** @see SchemaMeta#xdslKeys */
    public XDslKeys getXDslKeys() {
        return getSchemaMeta().xdslKeys;
    }

    /** 当前标签是否在元元模型 xdef.xdef 中 */
    private boolean isInXDefXDef() {
        IXDefinition def = getSelfDef();
        XDefKeys xdefKeys = getXDefKeys();

        // Note: 在单元测试中只能基于内容做判断，而不是 vfs 路径
        return def != null //
               && def.getXdefCheckNs().contains(XDefKeys.DEFAULT.NS) //
               && !XDefKeys.DEFAULT.equals(xdefKeys);
    }

    /** 当前标签是否在 DSL 元模型 xdsl.xdef 中 */
    private boolean isInXDslXDef() {
        IXDefinition def = getSelfDef();
        XDslKeys xdslKeys = getXDslKeys();

        // Note: 在单元测试中只能基于内容做判断，而不是 vfs 路径
        return def != null //
               && def.getXdefCheckNs().contains(XDslKeys.DEFAULT.NS) //
               && !XDslKeys.DEFAULT.equals(xdslKeys);
    }

    /** 获取 {@link IXDefNode} 上指定属性的 xdef 定义 */
    private IXDefAttribute getXDefNodeAttr(IXDefNode xdefNode, String attrName) {
        String xmlnsPrefix = "xmlns:";
        if (attrName.startsWith(xmlnsPrefix)) {
            String attrValue = getAttributeValue(attrName);
            // 忽略 xmlns:biz="biz" 形式的属性
            if (attrName.equals(xmlnsPrefix + attrValue)) {
                return null;
            }

            XDefAttribute at = new XDefAttribute();
            at.setName(attrName);
            at.setType(STD_DOMAIN_XDEF_REF);

            return at;
        }

        if (xdefNode == null) {
            return null;
        }

        IXDefAttribute attr = xdefNode.getAttribute(attrName);
        if (attr != null) {
            return attr;
        }

        // Note: 在 IXDefNode 中，对 xdef:unknown-attr 只记录了类型，并没有 IXDefAttribute 实体，
        // 其处理逻辑见 XDefinitionParser#parseNode
        XDefTypeDecl xdefUnknownAttrType = xdefNode.getXdefUnknownAttr();
        if (xdefUnknownAttrType != null) {
            XDefAttribute at = new XDefAttribute() {
                @Override
                public boolean isUnknownAttr() {
                    return true;
                }
            };

            at.setName(getXDefKeys().UNKNOWN_ATTR);
            at.setType(xdefUnknownAttrType);

            // Note: 在需要时，通过节点位置再定位具体的属性位置
            SourceLocation loc = null;
            // 在存在节点继承的情况下，选择最上层定义的同类型的 unknown-attr 属性
            IXDefNode refNode = xdefNode;
            while (refNode != null) {
                if (refNode.getXdefUnknownAttr() != xdefUnknownAttrType) {
                    break;
                }

                loc = refNode.getLocation();
                refNode = refNode.getRefNode();
            }
            at.setLocation(loc);

            return at;
        }

        return null;
    }

    private static String changeNamespace(String name, String fromNs, String toNs) {
        if (fromNs == null || toNs == null || fromNs.equals(toNs)) {
            return name;
        }

        if (StringHelper.startsWithNamespace(name, fromNs)) {
            return toNs + ':' + name.substring(fromNs.length() + 1);
        }
        return name;
    }

    private synchronized SchemaMeta getSchemaMeta() {
        if (schemaMeta == null) {
            Project project = getProject();
            schemaMeta = ProjectEnv.withProject(project, this::createSchemaMeta);

            try {
                ProgressManager.checkCanceled();
            } catch (ProcessCanceledException e) {
                // Note: 若处理被中断，则保持元模型信息为空，以便于后续再重新初始化
                schemaMeta = null;

                // Note: 避免后续访问成员变量出现 NPE 问题
                return SchemaMeta.UNKNOWN;
            }
        }
        return schemaMeta;
    }

    private SchemaMeta createSchemaMeta() {
        XLangTag parentTag = getParentTag();
        if (parentTag == null) {
            return createSchemaMetaForRootTag(this);
        }

        String tagNs = getNamespacePrefix();
        String tagName = getName();

        // Note: 允许元模型不存在，以支持检查 xdsl.xdef 对应的节点
        IXDefinition schemaDef = parentTag.getSchemaDef();
        IXDefinition selfDef = parentTag.getSelfDef();

        XDefKeys xdefKeys = parentTag.getXDefKeys();
        XDslKeys xdslKeys = parentTag.getXDslKeys();
        IXDefNode parentSchemaDefNode = parentTag.getSchemaDefNode();
        IXDefNode parentXDslDefNode = parentTag.getXDslDefNode();
        IXDefNode parentSelfDefNode = parentTag.getSelfDefNode();

        boolean xpl = XDefPsiHelper.isXplDefNode(parentSchemaDefNode) //
                      // xlib.xdef 中的 source 标签设置为 xml 类型，是因为在获取 XplLib 模型的时候会根据 xlib.xdef 来解析，
                      // 但此时这个 source 段无法自动进行编译，必须结合它的 outputMode 和 attrs 配置等才能决定。
                      // 因此，将其子节点同样视为 xpl 节点处理
                      || (schemaDef != null //
                          && (schemaDef.resourcePath().equals(XDslConstants.XDSL_SCHEMA_XLIB) //
                              || schemaDef.resourcePath().endsWith("_vfs" + XDslConstants.XDSL_SCHEMA_XLIB) //
                          ) //
                          && XDefConstants.STD_DOMAIN_XML.equals(XDefPsiHelper.getDefNodeType(parentSchemaDefNode)) //
                          && XlibConstants.SOURCE_NAME.equals(parentTag.getName()) //
                      );
        if (xpl) {
            // Xpl 子节点均为 xdef:unknown-tag
            parentSchemaDefNode = XDefPsiHelper.getXplDef().getRootNode().getXdefUnknownTag();
        }

        IXDefNode xdslDefNode = parentXDslDefNode != null ? parentXDslDefNode.getChild(tagName) : null;
        IXDefNode selfDefNode = parentSelfDefNode != null ? parentSelfDefNode.getChild(tagName) : null;

        IXDefNode schemaDefNode = null;
        if (tagNs.equals(XDslKeys.DEFAULT.NS) && !parentTag.isInXDslXDef()) {
            schemaDefNode = xdslDefNode;
        } //
        else if (parentSchemaDefNode != null) {
            // Note: 如果是 xdef.xdef 中的节点，则其节点定义均为 xdef:unknown-tag
            boolean inXDefXDef = parentTag.isInXDefXDef();

            schemaDefNode = inXDefXDef //
                            ? parentSchemaDefNode.getXdefUnknownTag() //
                            : parentSchemaDefNode.getChild(tagName);
        }

        return new SchemaMeta(schemaDef, schemaDefNode, xdslDefNode, selfDef, selfDefNode, xdefKeys, xdslKeys);
    }

    private static SchemaMeta createSchemaMetaForRootTag(XLangTag rootTag) {
        String schemaUrl = XDefPsiHelper.getSchemaPath(rootTag);
        if (schemaUrl == null) {
            return SchemaMeta.UNKNOWN;
        }

        // Note: 允许元模型不存在，以支持检查 xdsl.xdef 对应的节点
        IXDefinition schemaDef = XDefPsiHelper.loadSchema(schemaUrl);

        String xdefNs = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDEF);
        String xdslNs = XmlPsiHelper.getXmlnsForUrl(rootTag, XDslConstants.XDSL_SCHEMA_XDSL);
        XDefKeys xdefKeys = XDefKeys.of(xdefNs);
        XDslKeys xdslKeys = XDslKeys.of(xdslNs);

        IXDefinition selfDef = null;
        // x:schema 为 /nop/schema/xdef.xdef 时，其自身也为元模型
        if (XDslConstants.XDSL_SCHEMA_XDEF.equals(schemaUrl)) {
            String vfsPath = XmlPsiHelper.getNopVfsPath(rootTag);

            if (vfsPath != null) {
                selfDef = XDefPsiHelper.loadSchema(vfsPath);
            } else {
                // 适配单元测试环境：待测试资源可能不是标准的 vfs 资源
                selfDef = XDefPsiHelper.loadSchema(rootTag.getContainingFile());
            }
        }

        IXDefNode schemaDefNode = schemaDef != null ? schemaDef.getRootNode() : null;
        IXDefNode xdslDefNode = XDefPsiHelper.getXDslDef().getRootNode();
        IXDefNode selfDefNode = selfDef != null ? selfDef.getRootNode() : null;

        return new SchemaMeta(schemaDef, schemaDefNode, xdslDefNode, selfDef, selfDefNode, xdefKeys, xdslKeys);
    }

    /**
     * @param schemaDef
     *         当前标签所在的元模型（在 *.xdef 中定义）
     * @param schemaDefNode
     *         当前标签在 {@link #schemaDef} 中所对应的节点
     * @param xdslDefNode
     *         当前标签在 xdsl 模型（xdsl.xdef）中所对应的节点。
     *         注：所有 DSL 模型的节点均与 xdsl.xdef 的节点存在对应
     * @param selfDef
     *         在当前标签定义在 *.xdef 文件中时，需记录该元模型
     * @param selfDefNode
     *         当前标签定义在 {@link #selfDefNode} 中所对应的节点
     * @param xdefKeys
     *         <code>/nop/schema/xdef.xdef</code> 对应的 {@link XDefKeys}。
     *         仅在元模型中设置，如 <code>xmlns:xdef="/nop/schema/xdef.xdef"</code>
     * @param xdslKeys
     *         <code>/nop/schema/xdsl.xdef</code> 对应的 {@link XDslKeys}。
     *         在 DSL 模型（含元模型）中均有设置，如 <code>xmlns:x="/nop/schema/xdsl.xdef"</code>
     */
    private record SchemaMeta( //
                               IXDefinition schemaDef, IXDefNode schemaDefNode, //
                               IXDefNode xdslDefNode, //
                               IXDefinition selfDef, IXDefNode selfDefNode, //
                               XDefKeys xdefKeys, XDslKeys xdslKeys //
    ) {
        public static final SchemaMeta UNKNOWN = new SchemaMeta(null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                XDefKeys.DEFAULT,
                                                                XDslKeys.DEFAULT);
    }
}
