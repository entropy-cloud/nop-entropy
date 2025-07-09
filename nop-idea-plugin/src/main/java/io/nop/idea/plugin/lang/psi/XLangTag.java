package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import io.nop.core.lang.xml.XNode;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
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
    private XDefMeta defMeta;

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getElementType() + "('" + getName() + "')";
    }

    @Override
    public void clearCaches() {
        this.defMeta = null;

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

    /** 获取当前标签上指定属性的 xdef 定义 */
    public IXDefAttribute getDefAttr(String attrName) {
        return null;
    }

    public IXDefinition getDef() {
        prepareDef();
        return defMeta.def;
    }

    public IXDefNode getDefNode() {
        prepareDef();
        return defMeta.defNode;
    }

    public IXDefNode getXDslDefNode() {
        prepareDef();
        return defMeta.xdslDefNode;
    }

    private void prepareDef() {
        if (defMeta != null) {
            return;
        }

        XLangTag parentTag = (XLangTag) getParentTag();
        // 当前为根节点
        if (parentTag == null) {
            String schemaUrl = XDefPsiHelper.getSchemaPath(this);
            if (schemaUrl == null) {
                return;
            }

            IXDefinition def = XDefPsiHelper.loadSchema(schemaUrl);
            if (def == null) {
                return;
            }

            IXDefNode defNode = def.getRootNode();
            IXDefNode xdslDefNode = XDefPsiHelper.getXDslDef().getRootNode();

            defMeta = new XDefMeta(def, defNode, xdslDefNode);
            return;
        }

        IXDefinition def = parentTag.getDef();
        if (def == null) {
            return;
        }

        String tagName = getName();
        IXDefNode parentDefNode = parentTag.getDefNode();
        IXDefNode parentXDslDefNode = parentTag.getXDslDefNode();

        IXDefNode defNode = parentDefNode != null ? parentDefNode.getChild(tagName) : null;
        IXDefNode xdslDefNode = parentXDslDefNode != null ? parentXDslDefNode.getChild(tagName) : null;

        defMeta = new XDefMeta(def, defNode, xdslDefNode);
    }

    /**
     * @param def
     *         当前标签的元模型（在 *.xdef 中定义）
     * @param defNode
     *         当前标签在 {@link #def} 中所对应的节点
     * @param xdslDefNode
     *         当前标签所对应的 xdsl.xdef 中的节点，
     *         所有 DSL 模型的节点均与 xdsl.xdef 的节点存在对应
     */
    private record XDefMeta(IXDefinition def, IXDefNode defNode, IXDefNode xdslDefNode) {}
}
