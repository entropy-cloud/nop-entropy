package io.nop.idea.plugin.reference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefConstants;
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

    /** 获取 xml 标签对应的引用（节点定义、xpl 函数定义等） */
    private PsiReference @NotNull [] getReferencesFromXmlTag(Project project, XmlElement refElement, XmlTag tag) {
        // TODO xpl 函数的引用

        return PsiReference.EMPTY_ARRAY;
    }

    /** 获取 xml 属性名对应的引用（属性定义） */
    private PsiReference @NotNull [] getReferencesFromXmlAttribute(
            Project project, XmlElement refElement, XmlAttribute attr
    ) {
        String attrName = attr.getName();
        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
        IXDefAttribute attrDef = tagInfo != null ? tagInfo.getDefAttr(attrName) : null;

        if (attrDef == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        SourceLocation loc = attrDef.getLocation();
        String path = loc.getPath();

        TextRange textRange = new TextRange(0, attrName.length());

        return getReferencesByVfsPath(project,
                                      refElement,
                                      path,
                                      textRange,
                                      new NopVfsFileReference.PosAnchor(loc.getLine(), loc.getPos()));
    }

    /** 获取 xml 属性值对应的引用（文件、节点、属性类型等） */
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
            return getReferencesByVfsPath(project, refElement, attrValue);
        }

        // TODO 对于声明属性，仅对其类型的定义（涉及枚举和字典）做跳转
        if (tagInfo.isDefDeclaredAttr(attrName)) {
            return PsiReference.EMPTY_ARRAY;
        }

        // 根据属性声明的类型，对属性值做文件/名字引用跳转处理
        String stdDomain = attrDefType.getStdDomain();

        // Note: v-path 类型采用缺省处理
        if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
            return getReferencesFromVfsPathCsv(project, refElement, attrValue);
        } //
        else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
            return getReferencesFromXDefRef(project, refElement, attrValue);
        } //
        else {
            String xdslNs = XDefPsiHelper.getXDslNamespace(tagInfo.getTag());

            if ((xdslNs + ":prototype").equals(attrName)) {
                return getReferencesFromPrototype(project, refElement, tagInfo, attrValue);
            } else {
                String xdefNs = XDefPsiHelper.getXDefNamespace(tagInfo.getTag());

                if ((xdefNs + ":key-attr").equals(attrName)) {
                    return getReferencesFromKeyAttr(project, refElement, tagInfo, attrValue);
                } else if ((xdefNs + ":unique-attr").equals(attrName)) {
                    return getReferencesFromUniqueAttr(project, refElement, tagInfo, attrValue);
                }
            }
        }

        // 缺省引用识别
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />
        // <dialog page="/nop/rule/pages/RuleService/executeRule.page.yaml" />
        return getReferencesByVfsPath(project, refElement, attrValue);
    }

    private PsiReference[] getReferencesFromXmlText(Project project, XmlElement refElement, XmlTag tag) {
        return PsiReference.EMPTY_ARRAY;
    }

    /** 获取指定路径的引用（文件） */
    private PsiReference[] getReferencesByVfsPath(Project project, XmlAttributeValue refElement, String path) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, path.length() + 1);

        return getReferencesByVfsPath(project, refElement, path, textRange, null);
    }

    /** 从 csv 文本中获取引用 */
    private PsiReference[] getReferencesFromVfsPathCsv(Project project, XmlAttributeValue refElement, String csv) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        Map<TextRange, String> rangePathMap = extractPathsFromCsv(csv);

        List<PsiReference> list = new ArrayList<>(rangePathMap.size());
        rangePathMap.forEach((textRange, path) -> {
            PsiReference[] refs = getReferencesByVfsPath(project, refElement, path, textRange.shiftRight(1), null);

            list.addAll(Arrays.stream(refs).toList());
        });

        return list.toArray(PsiReference[]::new);
    }

    /** 从 <code>xdef-ref</code> 类型的属性值中获取引用 */
    private PsiReference[] getReferencesFromXDefRef(
            Project project, XmlAttributeValue refElement, String xdefRefValue
    ) {
        // - /nop/schema/xdef.xdef:
        //   - `<schema xdef:ref="schema-node.xdef" />`
        //   - `<item xdef:ref="ISchema" />`
        // - /nop/schema/schema/schema-node.xdef:
        //   `<schema ref="/test/test-filter.xdef#FilterCondition" />`

        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, xdefRefValue.length() + 1);

        // 含有后缀的，视为文件引用
        if (xdefRefValue.indexOf(".") > 0) {
            int hashIndex = xdefRefValue.indexOf('#');

            String path = hashIndex > 0 ? xdefRefValue.substring(0, hashIndex) : xdefRefValue;
            String ref = hashIndex > 0 ? xdefRefValue.substring(hashIndex + 1) : null;
            NopVfsFileReference.Anchor anchor = new NopVfsFileReference.RefAnchor(ref);

            return getReferencesByVfsPath(project, refElement, path, textRange, anchor);
        }
        // 否则，视为名字引用
        else {
            String ref = xdefRefValue;
            NopVfsFileReference.Anchor anchor = new NopVfsFileReference.RefAnchor(ref);
            // Note: 只能引用当前文件（不一定是 vfs）内的名字
            PsiFile file = refElement.getContainingFile();

            return new PsiReference[] {
                    new NopVfsFileReference(refElement, textRange, file, anchor)
            };
        }
    }

    /** 从 <code>x:prototype</code> 的属性值中获取引用 */
    private PsiReference[] getReferencesFromPrototype(
            Project project, XmlAttributeValue refElement, XmlTagInfo tagInfo, String attrValue
    ) {
        // 仅从父节点中取引用到的子节点
        // io.nop.xlang.delta.DeltaMerger#mergePrototype
        IXDefNode defNode = tagInfo.getDefNode();
        IXDefNode parentDefNode = tagInfo.getParentDefNode();

        String keyAttr = parentDefNode.getXdefKeyAttr();
        if (keyAttr == null) {
            keyAttr = defNode.getXdefUniqueAttr();
        }

        XmlTag parentTag = tagInfo.getTag().getParentTag();
        if (parentTag == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        XmlTag protoTag = XmlPsiHelper.getChildTagByAttr(parentTag, keyAttr, attrValue);
        if (protoTag == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        return new PsiReference[] { new XLangElementReference(refElement, textRange, protoTag) };
    }

    /** 从 <code>xdef:key-attr</code> 的属性值中获取引用 */
    private PsiReference[] getReferencesFromKeyAttr(
            Project project, XmlAttributeValue refElement, XmlTagInfo tagInfo, String attrValue
    ) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        return XmlPsiHelper.getAttrsFromChildTag(tagInfo.getTag(), attrValue)
                           .stream()
                           .map((attr) -> new XLangElementReference(refElement, textRange, attr))
                           .toArray(PsiReference[]::new);
    }

    /** 从 <code>xdef:unique-attr</code> 的属性值中获取引用 */
    private PsiReference[] getReferencesFromUniqueAttr(
            Project project, XmlAttributeValue refElement, XmlTagInfo tagInfo, String attrValue
    ) {
        // 仅从当前节点中取引用到的属性
        XmlTag tag = tagInfo.getTag();
        XmlAttribute attr = tag.getAttribute(attrValue);
        if (attr == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        return new PsiReference[] { new XLangElementReference(refElement, textRange, attr) };
    }

    private PsiReference[] getReferencesByVfsPath(
            Project project, XmlElement refElement, //
            String path, TextRange textRange, NopVfsFileReference.Anchor anchor
    ) {
        if (!StringHelper.isValidFilePath(path) || path.indexOf('.') <= 0) {
            return PsiReference.EMPTY_ARRAY;
        }

        String absPath = XmlPsiHelper.absolutePath(path, refElement);

        PsiReference[] refs = XmlPsiHelper.findPsiFileList(project, absPath)
                                          .stream()
                                          .map((file) -> new NopVfsFileReference(refElement, textRange, file, anchor))
                                          .toArray(PsiReference[]::new);

        return refs.length > 0 //
               ? refs //
               : new PsiReference[] { new NopVfsFileReference.NotFound(refElement, textRange, path) };
    }

    private Map<TextRange, String> extractPathsFromCsv(String csv) {
        Map<TextRange, String> rangePathMap = new HashMap<>();

        int offset = 0;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (ch != ',') {
                continue;
            }

            String path = csv.substring(offset, i);
            rangePathMap.put(new TextRange(offset, i), path);

            offset = i + 1;
        }

        if (offset < csv.length()) {
            String path = csv.substring(offset);
            rangePathMap.put(new TextRange(offset, csv.length()), path);
        }
        return rangePathMap;
    }
}


