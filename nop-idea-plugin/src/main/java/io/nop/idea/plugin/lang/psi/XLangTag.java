package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
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

    /** 当前标签是否在 xdef 文件中 */
    public boolean isInXDef() {
        return getSelfDefNode() != null;
    }

    /** @see SchemaMeta#xdef */
    private IXDefinition getXDef() {
        return getSchemaMeta().xdef;
    }

    /** @see SchemaMeta#xdefNode */
    public IXDefNode getXDefNode() {
        return getSchemaMeta().xdefNode;
    }

    public IXDefAttribute getXDefNodeAttr(String attrName) {
        // Note: xdef.xdef 的属性在固定的名字空间 xdef 中声明
        attrName = changeNamespace(attrName, getXDefKeys().NS, XDefKeys.DEFAULT.NS);

        return getXDefNodeAttr(getXDefNode(), attrName);
    }

    /** @see SchemaMeta#xdslDefNode */
    public IXDefNode getXDslDefNode() {
        return getSchemaMeta().xdslDefNode;
    }

    public IXDefAttribute getXDslDefNodeAttr(String attrName) {
        // Note: xdsl.xdef 的属性在固定的名字空间 x 中声明
        attrName = changeNamespace(attrName, getXDslKeys().NS, XDslKeys.DEFAULT.NS);

        return getXDefNodeAttr(getXDslDefNode(), attrName);
    }

    /** @see SchemaMeta#selfDefNode */
    public IXDefNode getSelfDefNode() {
        return getSchemaMeta().selfDefNode;
    }

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

    /** 获取 {@link IXDefNode} 上指定属性的 xdef 定义 */
    private static IXDefAttribute getXDefNodeAttr(IXDefNode xdefNode, String attrName) {
        if (attrName.startsWith("xmlns:")) {
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

        // Note: 在普通 *.xdef 的 IXDefNode 中，
        // 对 xdef:unknown-attr 只记录了类型，并没有 IXDefAttribute 实体，
        // 其处理逻辑见 XDefinitionParser#parseNode
        XDefTypeDecl xdefUnknownAttrType = xdefNode.getXdefUnknownAttr();
        if (xdefUnknownAttrType != null) {
            XDefAttribute at = new XDefAttribute() {
                @Override
                public boolean isUnknownAttr() {
                    return true;
                }
            };

            at.setName(XDefKeys.DEFAULT.UNKNOWN_ATTR);
            at.setType(xdefUnknownAttrType);
            // Note: 在需要时，通过节点位置再定位具体的属性位置
            at.setLocation(xdefNode.getLocation());

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
            }
        }
        return schemaMeta;
    }

    private SchemaMeta createSchemaMeta() {
        XLangTag parentTag = (XLangTag) getParentTag();

        // 当前为根标签
        if (parentTag == null) {
            String schemaUrl = XDefPsiHelper.getSchemaPath(this);
            if (schemaUrl == null) {
                return SchemaMeta.UNKNOWN;
            }

            // Note: 允许元模型不存在，以支持检查 xdsl.xdef 对应的节点
            IXDefinition xdef = XDefPsiHelper.loadSchema(schemaUrl);

            String xdefNs = XmlPsiHelper.getXmlnsForUrl(this, XDslConstants.XDSL_SCHEMA_XDEF);
            XDefKeys xdefKeys = XDefKeys.of(xdefNs);
            String xdslNs = XmlPsiHelper.getXmlnsForUrl(this, XDslConstants.XDSL_SCHEMA_XDSL);
            XDslKeys xdslKeys = XDslKeys.of(xdslNs);

            IXDefNode selfDefNode = null;
            // x:schema 为 /nop/schema/xdef.xdef 的，均为 xdef 模型
            if (XDslConstants.XDSL_SCHEMA_XDEF.equals(getAttributeValue(xdslKeys.SCHEMA))) {
                String vfsPath = XmlPsiHelper.getNopVfsPath(this);

                IXDefinition selfDef;
                if (vfsPath != null) {
                    selfDef = XDefPsiHelper.loadSchema(vfsPath);
                } else {
                    // 适配单元测试环境：待测试资源可能不是标准的 vfs 资源
                    selfDef = XDefPsiHelper.loadSchema(getContainingFile());
                }

                selfDefNode = selfDef != null ? selfDef.getRootNode() : null;
            }

            IXDefNode xdefNode = xdef != null ? xdef.getRootNode() : null;
            IXDefNode xdslDefNode = XDefPsiHelper.getXDslDef().getRootNode();

            return new SchemaMeta(xdef, xdefNode, xdslDefNode, selfDefNode, xdefKeys, xdslKeys);
        }

        // Note: 允许元模型不存在，以支持检查 xdsl.xdef 对应的节点
        IXDefinition xdef = parentTag.getXDef();

        String tagName = getName();
        XDefKeys xdefKeys = parentTag.getXDefKeys();
        XDslKeys xdslKeys = parentTag.getXDslKeys();
        IXDefNode parentXDefNode = parentTag.getXDefNode();
        IXDefNode parentXDslDefNode = parentTag.getXDslDefNode();
        IXDefNode parentSelfDefNode = parentTag.getSelfDefNode();

        boolean xpl = XDefPsiHelper.isXplDefNode(parentXDefNode) //
                      // xlib.xdef 中的 source 标签设置为 xml 类型，是因为在获取 XplLib 模型的时候会根据 xlib.xdef 来解析，
                      // 但此时这个 source 段无法自动进行编译，必须结合它的 outputMode 和 attrs 配置等才能决定。
                      // 因此，将其子节点同样视为 xpl 节点处理
                      || (xdef != null //
                          && (xdef.resourcePath().equals(XDslConstants.XDSL_SCHEMA_XLIB) //
                              || xdef.resourcePath().endsWith("_vfs" + XDslConstants.XDSL_SCHEMA_XLIB) //
                          ) //
                          && "xml".equals(XDefPsiHelper.getDefNodeType(parentXDefNode)) //
                          && "source".equals(parentTag.getName()) //
                      );
        if (xpl) {
            // 对于 Xpl 节点，始终采用 xpl.xdef 作为其子节点的元模型
            xdef = XDefPsiHelper.getXplDef();
            // Xpl 子节点均为 xdef:unknown-tag
            parentXDefNode = xdef.getRootNode().getXdefUnknownTag();
        }

        IXDefNode xdslDefNode = parentXDslDefNode != null ? parentXDslDefNode.getChild(tagName) : null;
        // TODO x/xdef 名字空间的标签，不取 self def
        IXDefNode selfDefNode = parentSelfDefNode != null ? parentSelfDefNode.getChild(tagName) : null;

        IXDefNode xdefNode;
        if (tagName.startsWith(XDslKeys.DEFAULT.X_NS_PREFIX)) {
            xdef = XDefPsiHelper.getXDslDef();
            xdefNode = xdslDefNode;
        } else {
            // Note: 如果是 xdef.xdef 中的节点，则其节点 xdef 定义均为 xdef:unknown-tag
            boolean inXDefXDef = xdef != null //
                                 && xdef.getXdefCheckNs().contains(XDefKeys.DEFAULT.NS) //
                                 && !XDefKeys.DEFAULT.equals(xdefKeys); // 在单元测试中只能基于内容做判断，而不是 vfs 路径

            xdefNode = parentXDefNode != null //
                       ? inXDefXDef //
                         ? parentXDefNode.getXdefUnknownTag() //
                         : parentXDefNode.getChild(tagName) //
                       : null;
        }

        return new SchemaMeta(xdef, xdefNode, xdslDefNode, selfDefNode, xdefKeys, xdslKeys);
    }

    /**
     * @param xdef
     *         当前标签所在的元模型（在 *.xdef 中定义）
     * @param xdefNode
     *         当前标签在 {@link #xdef} 中所对应的节点
     * @param xdslDefNode
     *         当前标签在 xdsl.xdef 中所对应的节点。
     *         注：所有 DSL 模型的节点均与 xdsl.xdef 的节点存在对应
     * @param selfDefNode
     *         若当前标签定义在 xdef 文件中，则需得到其自身的定义节点
     * @param xdefKeys
     *         <code>/nop/schema/xdef.xdef</code> 对应的 {@link XDefKeys}。
     *         仅在元模型中设置，如 <code>xmlns:xdef="/nop/schema/xdef.xdef"</code>
     * @param xdslKeys
     *         <code>/nop/schema/xdsl.xdef</code> 对应的 {@link XDslKeys}。
     *         在 DSL 模型（含元模型）中均有设置，如 <code>xmlns:x="/nop/schema/xdsl.xdef"</code>
     */
    private record SchemaMeta( //
                               IXDefinition xdef, IXDefNode xdefNode, //
                               IXDefNode xdslDefNode, //
                               IXDefNode selfDefNode, //
                               XDefKeys xdefKeys, XDslKeys xdslKeys //
    ) {
        public static final SchemaMeta UNKNOWN = new SchemaMeta(null,
                                                                null,
                                                                null,
                                                                null,
                                                                XDefKeys.DEFAULT,
                                                                XDslKeys.DEFAULT);
    }
}
