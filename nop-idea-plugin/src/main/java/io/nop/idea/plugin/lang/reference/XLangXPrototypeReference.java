package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefNode;
import org.jetbrains.annotations.Nullable;

/**
 * {@link io.nop.xlang.xdsl.XDslKeys#PROTOTYPE x:prototype} 的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangXPrototypeReference extends XLangReferenceBase {
    private final String attrValue;

    public XLangXPrototypeReference(XmlElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangTag tag = PsiTreeUtil.getParentOfType(myElement, XLangTag.class);
        assert tag != null;

        XLangTag parentTag = (XLangTag) tag.getParentTag();
        if (parentTag == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.x-prototype-no-parent");
            setUnresolvedMessage(msg);

            return null;
        }

        // 仅从父节点中取引用到的子节点
        // io.nop.xlang.delta.DeltaMerger#mergePrototype
        IXDefNode defNode = tag.getSchemaDefNode();
        IXDefNode parentDefNode = parentTag.getSchemaDefNode();

        String keyAttr = parentDefNode.getXdefKeyAttr();
        if (keyAttr == null) {
            keyAttr = defNode.getXdefUniqueAttr();
        }

        XLangTag protoTag = (XLangTag) XmlPsiHelper.getChildTagByAttr(parentTag, keyAttr, attrValue);
        if (protoTag == null) {
            String msg = keyAttr == null
                         ? NopPluginBundle.message("xlang.annotation.reference.x-prototype-tag-not-found",
                                                   attrValue)
                         : NopPluginBundle.message("xlang.annotation.reference.x-prototype-attr-not-found",
                                                   keyAttr,
                                                   attrValue);
            setUnresolvedMessage(msg);

            return null;
        }

        // 定位到目标属性或标签上
        return keyAttr != null ? protoTag.getAttribute(keyAttr) : protoTag;
    }
}
