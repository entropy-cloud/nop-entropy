package io.nop.idea.plugin.lang.script.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.lang.script.psi.ExpressionNode;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.getMethodSignature;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.isClassWithName;

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

    @Override
    public Object @NotNull [] getVariants() {
        // Note: 只有在当前引用的结果不存在时，才需要补全，而其可补全项由上层引用结果确定
        PsiClass clazz = ((ExpressionNode) myElement).getObjectClass();
        if (clazz == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        List<Object> result = new ArrayList<>();

        // 代码来自 JavaLangClassMemberReference.getVariants
        LookupElement[] fields = StreamEx.of(clazz.getAllFields())
                                         .filter(field -> isPotentiallyAccessible(field, clazz))
                                         .distinct(PsiField::getName)
                                         .sorted(Comparator.comparingInt((PsiField field) -> isPublic(field) ? 0 : 1)
                                                           .thenComparing(PsiField::getName))
                                         .map(field -> withPriority(JavaLookupElementBuilder.forField(field),
                                                                    isPublic(field)))
                                         .toArray(LookupElement[]::new);
        Collections.addAll(result, fields);

        LookupElement[] methods = StreamEx.of(clazz.getVisibleSignatures())
                                          .map(MethodSignatureBackedByPsiMethod::getMethod)
                                          .filter(method -> isRegularMethod(method) && isPotentiallyAccessible(method,
                                                                                                               clazz))
                                          .sorted(Comparator.comparingInt(ObjectMemberReference::getMethodSortOrder)
                                                            .thenComparing(PsiMethod::getName))
                                          .map(method -> withPriority(lookupMethod(method, null),
                                                                      -getMethodSortOrder(method)))
                                          .nonNull()
                                          .toArray(LookupElement[]::new);
        Collections.addAll(result, methods);

        return result.toArray();
    }

    // <<<<<<<<<<<<<<<< 代码来自 JavaLangClassMemberReference.getVariants
    static boolean isPotentiallyAccessible(PsiMember member, PsiClass clazz) {
        return member != null && (member.getContainingClass() == clazz || isPublic(member));
    }

    static boolean isPublic(@NotNull PsiMember member) {
        return member.hasModifierProperty(PsiModifier.PUBLIC);
    }

    @NotNull
    static LookupElement withPriority(@NotNull LookupElement lookupElement, boolean hasPriority) {
        return hasPriority ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, -1);
    }

    static LookupElement withPriority(@Nullable LookupElement lookupElement, int priority) {
        return priority == 0 || lookupElement == null
               ? lookupElement
               : PrioritizedLookupElement.withPriority(lookupElement, priority);
    }

    static boolean isRegularMethod(@Nullable PsiMethod method) {
        return method != null && !method.isConstructor();
    }

    static int getMethodSortOrder(@NotNull PsiMethod method) {
        return isJavaLangObject(method.getContainingClass()) ? 1 : isPublic(method) ? -1 : 0;
    }

    static boolean isJavaLangObject(@Nullable PsiClass aClass) {
        return isClassWithName(aClass, CommonClassNames.JAVA_LANG_OBJECT);
    }

    static LookupElement lookupMethod(@NotNull PsiMethod method, @Nullable InsertHandler<LookupElement> insertHandler) {
        final JavaReflectionReferenceUtil.ReflectiveSignature signature = getMethodSignature(method);
        return signature != null ? LookupElementBuilder.create(signature, method.getName())
                                                       .withIcon(signature.getIcon())
                                                       .withTailText(signature.getShortArgumentTypes())
                                                       .withInsertHandler(insertHandler) : null;
    }
    // >>>>>>>>>>>>>>>>>>>>>
}
