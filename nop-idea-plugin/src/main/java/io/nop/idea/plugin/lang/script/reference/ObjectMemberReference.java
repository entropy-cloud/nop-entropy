package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.lang.script.psi.ExpressionNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对象成员（属性或方法）引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class ObjectMemberReference extends XLangReferenceBase {

    public ObjectMemberReference(ExpressionNode myElement) {
        super(myElement, null);
    }

    @Override
    protected TextRange calculateDefaultRangeInElement() {
        return ((ExpressionNode) myElement).getObjectMemberTextRange();
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        return ((ExpressionNode) myElement).getObjectMember();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        PsiElement element = myElement;
        TextRange rangeInElement = getRangeInElement();

        ElementManipulator<PsiElement> manipulator = getManipulator(element);
        element = manipulator.handleContentChange(element, rangeInElement, newElementName);

        rangeInElement = ((ExpressionNode) element).getObjectMemberTextRange();
        setRangeInElement(rangeInElement);

        return element;
    }
}
