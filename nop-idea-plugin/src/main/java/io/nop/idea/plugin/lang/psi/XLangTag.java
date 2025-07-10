package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import io.nop.core.lang.xml.XNode;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefKeys;
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
    public IXDefinition getXDef() {
        prepareSchema();
        return schemaMeta.xdef;
    }

    /** @see SchemaMeta#xdefNode */
    public IXDefNode getXDefNode() {
        prepareSchema();
        return schemaMeta.xdefNode;
    }

    public IXDefAttribute getXDefNodeAttr(String attrName) {
        // Note: xdef.xdef 的属性在固定的名字空间 xdef 中声明
        attrName = changeNamespace(attrName, getXDefNs(), XDefKeys.DEFAULT.NS);

        return getXDefNodeAttr(getXDefNode(), attrName);
    }

    /** @see SchemaMeta#xdslDefNode */
    public IXDefNode getXDslDefNode() {
        prepareSchema();
        return schemaMeta.xdslDefNode;
    }

    public IXDefAttribute getXDslDefNodeAttr(String attrName) {
        // Note: xdsl.xdef 的属性在固定的名字空间 x 中声明
        String xNs = XDslKeys.DEFAULT.X_NS_PREFIX.substring(0, XDslKeys.DEFAULT.X_NS_PREFIX.length() - 1);
        attrName = changeNamespace(attrName, getXDslNs(), xNs);

        return getXDefNodeAttr(getXDslDefNode(), attrName);
    }

    /** @see SchemaMeta#selfDefNode */
    public IXDefNode getSelfDefNode() {
        prepareSchema();
        return schemaMeta.selfDefNode;
    }

    public IXDefAttribute getSelfDefNodeAttr(String attrName) {
        return getXDefNodeAttr(getSelfDefNode(), attrName);
    }

    /** @see SchemaMeta#xdefNs */
    public String getXDefNs() {
        prepareSchema();
        return schemaMeta.xdefNs;
    }

    /** @see SchemaMeta#xdslNs */
    public String getXDslNs() {
        prepareSchema();
        return schemaMeta.xdslNs;
    }

    /** 获取 {@link IXDefNode} 上指定属性的 xdef 定义 */
    private static IXDefAttribute getXDefNodeAttr(IXDefNode xdefNode, String attrName) {
        IXDefAttribute attr = xdefNode != null ? xdefNode.getAttribute(attrName) : null;

        return attr;
    }

    private static String changeNamespace(String name, String fromNs, String toNs) {
        if (fromNs.equals(toNs)) {
            return name;
        }

        String ns = fromNs + ':';
        if (name.startsWith(ns)) {
            return toNs + ':' + name.substring(ns.length());
        }
        return name;
    }

    private synchronized void prepareSchema() {
        if (this.schemaMeta != null) {
            return;
        }

        Project project = getProject();
        schemaMeta = ProjectEnv.withProject(project, this::createSchemaMeta);
    }

    private SchemaMeta createSchemaMeta() {
        XLangTag parentTag = (XLangTag) getParentTag();

        // 当前为根标签
        if (parentTag == null) {
            String schemaUrl = XDefPsiHelper.getSchemaPath(this);
            if (schemaUrl == null) {
                return SchemaMeta.UNDEFINED;
            }

            IXDefinition xdef = XDefPsiHelper.loadSchema(schemaUrl);
            if (xdef == null) {
                return SchemaMeta.UNDEFINED;
            }

            String xdefNs = XmlPsiHelper.getXmlnsForUrl(this, XDslConstants.XDSL_SCHEMA_XDEF);
            String xdslNs = XmlPsiHelper.getXmlnsForUrl(this, XDslConstants.XDSL_SCHEMA_XDSL);

            IXDefNode selfDefNode = null;
            // x:schema 为 /nop/schema/xdef.xdef 的，均为 xdef 模型
            if (XDslConstants.XDSL_SCHEMA_XDEF.equals(getAttributeValue(xdslNs + ":schema"))) {
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

            IXDefNode xdefNode = xdef.getRootNode();
            IXDefNode xdslDefNode = XDefPsiHelper.getXDslDef().getRootNode();

            return new SchemaMeta(xdef, xdefNode, xdslDefNode, selfDefNode, xdefNs, xdslNs);
        }

        IXDefinition xdef = parentTag.getXDef();
        if (xdef == null) {
            return SchemaMeta.UNDEFINED;
        }

        String xdefNs = parentTag.getXDefNs();
        String xdslNs = parentTag.getXDslNs();
        IXDefNode parentXDefNode = parentTag.getXDefNode();
        IXDefNode parentXDslDefNode = parentTag.getXDslDefNode();
        IXDefNode parentSelfDefNode = parentTag.getSelfDefNode();

        String tagName = getName();
        tagName = changeNamespace(tagName, xdefNs, XDefKeys.DEFAULT.NS);

        IXDefNode xdefNode = parentXDefNode != null ? parentXDefNode.getChild(tagName) : null;
        IXDefNode xdslDefNode = parentXDslDefNode != null ? parentXDslDefNode.getChild(tagName) : null;
        IXDefNode selfDefNode = parentSelfDefNode != null ? parentSelfDefNode.getChild(tagName) : null;

        return new SchemaMeta(xdef, xdefNode, xdslDefNode, selfDefNode, xdefNs, xdslNs);
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
     * @param xdefNs
     *         <code>/nop/schema/xdef.xdef</code> 对应的名字空间。
     *         仅在元模型中设置，如 <code>xmlns:xdef="/nop/schema/xdef.xdef"</code>
     * @param xdslNs
     *         <code>/nop/schema/xdsl.xdef</code> 对应的名字空间。
     *         在 DSL 模型（含元模型）中均有设置，如 <code>xmlns:x="/nop/schema/xdsl.xdef"</code>
     */
    private record SchemaMeta( //
                               IXDefinition xdef, IXDefNode xdefNode, //
                               IXDefNode xdslDefNode, //
                               IXDefNode selfDefNode, //
                               String xdefNs, String xdslNs //
    ) {
        public static final SchemaMeta UNDEFINED = new SchemaMeta(null, null, null, null, null, null);
    }
}
