package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.lang.script.psi.Identifier;
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class QualifiedNameReference extends XLangReferenceBase {
    private final IdentifierNode identifier;
    private final QualifiedNameReference parentReference;

    public QualifiedNameReference(
            PsiElement myElement, IdentifierNode identifier, TextRange myRangeInElement,
            QualifiedNameReference parentReference
    ) {
        super(myElement, myRangeInElement);
        this.identifier = identifier;
        this.parentReference = parentReference;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        if (!identifier.isValid()) {
            return null;
        }

        Project project = myElement.getProject();
        String subName = identifier.getText();

        // 最顶层标志符
        if (parentReference == null) {
            // 先尝试查找引用的变量类型
            PsiClass clazz = identifier.getVarType();
            if (clazz != null) {
                return clazz;
            }

            // ，再按包名查找
            return PsiClassHelper.findPackage(project, subName);
        } else {
            PsiElement parentElement = parentReference.resolve();
            if (parentElement == null) {
                return null;
            }

            if (parentElement instanceof PsiClass clazz) {
                return clazz.findInnerClassByName(subName, true);
            } //
            else if (parentElement instanceof PsiPackage pkg) {
                subName = pkg.getQualifiedName() + '.' + subName;

                PsiPackage subPkg = PsiClassHelper.findPackage(project, subName);
                if (subPkg != null) {
                    return subPkg;
                }

                // 若包不存在，则可能是类
                return PsiClassHelper.findClass(project, subName);
            }
        }

        return null;
    }

    /** 在构造函数中，用于修改包名 */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        PsiElement element = myElement;
        TextRange rangeInElement = getRangeInElement();

        // 直接替换名字
        ((Identifier) identifier.getFirstChild()).replaceWithText(newElementName);

        rangeInElement = identifier.getTextRangeInParent().shiftRight(rangeInElement.getStartOffset());
        setRangeInElement(rangeInElement);

        return element;
    }

    /** 在构造函数中，用于修改类名 */
    @Override
    public PsiElement bindToElement(@NotNull PsiElement source) throws IncorrectOperationException {
        String newName = null;
        if (source instanceof PsiClass clazz) {
            newName = clazz.getName();
        }

        return newName != null ? handleElementRename(newName) : null;
    }
}
