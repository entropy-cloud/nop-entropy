package io.nop.idea.plugin.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.utils.XmlPsiHelper.isElementType;

/**
 * 针对 XLang 中的 {@link PsiElement 元素} 创建引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
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
            if (isElementType(element, XmlElementType.XML_NAME)) {
                XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);

                if (attr != null) {
                    return getReferencesFromXmlAttribute(project, (XmlElement) element, attr);
                } else {
                    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);

                    if (tag != null) {
                        return getReferencesFromXmlTag(project, (XmlElement) element, tag);
                    }
                }
            } //
            else if (isElementType(element, XmlElementType.XML_ATTRIBUTE_VALUE)) {
                XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);

                if (attr != null) {
                    return getReferencesFromXmlAttributeValue(project, (XmlAttributeValue) element, attr);
                }
            } //
            else if (isElementType(element, XmlElementType.XML_TEXT)) {
                XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);

                if (tag != null) {
                    return getReferencesFromXmlText(project, (XmlElement) element, tag);
                }
            }

            return PsiReference.EMPTY_ARRAY;
        });
    }

    private PsiReference @NotNull [] getReferencesFromXmlTag(Project project, XmlElement refElement, XmlTag tag) {
        return PsiReference.EMPTY_ARRAY;
    }

    private PsiReference @NotNull [] getReferencesFromXmlAttribute(
            Project project, XmlElement refElement, XmlAttribute attr
    ) {
        return PsiReference.EMPTY_ARRAY;
    }

    private PsiReference @NotNull [] getReferencesFromXmlAttributeValue(
            Project project, XmlAttributeValue refElement, XmlAttribute attr
    ) {
        // Note: XmlAttributeValue#getValue 的结果包含引号
        String attrValue = attr.getValue();
        if (StringHelper.isEmpty(attrValue)) {
            return PsiReference.EMPTY_ARRAY;
        }

        String attrName = attr.getName();

        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
        XDefTypeDecl attrDefType = tagInfo != null ? tagInfo.getDefAttrType(attrName) : null;
        // 在无节点定义时，仅做缺省处理
        if (attrDefType == null) {
            return getReferencesByPath(project, refElement, attrValue);
        }

        // TODO 对于声明属性，仅对其类型的定义（涉及枚举和字典）做跳转
        if (tagInfo.isDefDeclaredAttr(attrName)) {
            return PsiReference.EMPTY_ARRAY;
        }

        // 根据属性声明的类型，对属性值做文件/名字引用跳转处理
        PsiFile file = attr.getContainingFile();
        String stdDomain = attrDefType.getStdDomain();

//        // Note: v-path 类型采用缺省处理
//        if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
//            return getGotoDeclarationTargetsFromPathCsv(project, file, cursorOffset);
//        } //
//        else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
//            return getGotoDeclarationTargetsFromXDefRef(project, attr, attrValue);
//        } //
//        else {
//            String xdslNs = XDefPsiHelper.getXDslNamespace(tagInfo.getTag());
//
//            if ((xdslNs + ":prototype").equals(attrName)) {
//                return getGotoDeclarationTargetsFromPrototype(project, tagInfo, attrValue);
//            } else {
//                String xdefNs = XDefPsiHelper.getXDefNamespace(tagInfo.getTag());
//
//                if ((xdefNs + ":key-attr").equals(attrName)) {
//                    return getGotoDeclarationTargetsFromKeyAttr(project, tagInfo, attrValue);
//                } else if ((xdefNs + ":unique-attr").equals(attrName)) {
//                    return getGotoDeclarationTargetsFromUniqueAttr(project, tagInfo, attrValue);
//                }
//            }
//        }

        // 缺省引用识别
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />
        // <dialog page="/nop/rule/pages/RuleService/executeRule.page.yaml" />
        return getReferencesByPath(project, refElement, attrValue);
    }

    private PsiReference[] getReferencesFromXmlText(Project project, XmlElement refElement, XmlTag tag) {
        return PsiReference.EMPTY_ARRAY;
    }

    /** 获取指定路径的引用（文件） */
    private PsiReference[] getReferencesByPath(Project project, @NotNull XmlAttributeValue refElement, String path) {
        if (!StringHelper.isValidFilePath(path)) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, path.length() + 1);
        String absPath = XmlPsiHelper.absolutePath(path, refElement);

        return XmlPsiHelper.findPsiFileList(project, absPath)
                           .stream()
                           .map((file) -> new NopVfsFileReference(refElement, textRange, file))
                           .toArray(PsiReference[]::new);
    }
}


