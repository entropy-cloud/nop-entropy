package io.nop.idea.plugin.reference;

import java.util.Arrays;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;

/**
 * 针对 XLang 中的 {@link PsiElement 元素} 创建引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 * @deprecated
 */
@Deprecated
public class XLangReferenceProvider extends PsiReferenceProvider {

    /**
     * @param element
     *         在探测的元素，若针对该元素返回了非空结果，则将该引用与 element 建立关联。
     *         因此，需要精确针对某个 element，而不能包含多余内容，从而确保在 UI 层面，
     *         高亮的或可点击的部分只是该 element，而不会包含引号等无关内容
     */
    @Override
    public PsiReference @NotNull [] getReferencesByElement(
            @NotNull PsiElement element, @NotNull ProcessingContext context
    ) {
        Project project = element.getProject();

        return ProjectEnv.withProject(project, () -> {
            /* XmlTag:xdef:unknown-tag(505,3305)
                  XmlToken:XML_START_TAG_START('<')(505,506)
                  XmlToken:XML_NAME('xdef:unknown-tag')(506,522)
                  PsiElement(XML_ATTRIBUTE)(523,558)
                    XmlToken:XML_NAME('xdsl:schema')(523,534)
                    XmlToken:XML_EQ('=')(534,535)
                    PsiElement(XML_ATTRIBUTE_VALUE)(535,558)
                      XmlToken:XML_ATTRIBUTE_VALUE_START_DELIMITER('"')(535,536)
                      XmlToken:XML_ATTRIBUTE_VALUE_TOKEN('/nop/schema/xdef.xdef')(536,557)
                      XmlToken:XML_ATTRIBUTE_VALUE_END_DELIMITER('"')(557,558)
            */
            if (element instanceof XmlTag tag) {
                return getReferencesFromXmlTag(tag);
            } //

            return PsiReference.EMPTY_ARRAY;
        });
    }

    /** 获取 xml 标签对应的引用（节点定义、xpl 函数定义等） */
    private PsiReference @NotNull [] getReferencesFromXmlTag(XmlTag tag) {
        // TODO xpl 函数的引用：根据导入 xlib 中定义的函数进行识别
        // TODO 引用节点定义
        String tagName = tag.getName();

        int pos = tagName.indexOf(':');
        if (pos <= 0) {
            return PsiReference.EMPTY_ARRAY;
        }

        // 内置的名字空间
        String ns = tagName.substring(0, pos);

        if (ns.equals("x") || ns.equals("xdef") || ns.equals("xdsl") || ns.equals("xpl") //
            || ns.equals("c") || ns.equals("macro") || ns.equals("xmlns") //
        ) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: 仅对名字做引用识别，忽略名字空间
        TextRange textRange = new TextRange(pos + 1, tagName.length()).shiftRight(1);

        return Arrays.stream(XmlPsiHelper.findXplTag(tag.getProject(), tag))
                     //.map((xpl) -> new XLangElementReference(tag, textRange, xpl))
                     .toArray(PsiReference[]::new);
    }
}


