package io.nop.idea.plugin.reference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;

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
            if (element instanceof XmlTag tag) {
                return getReferencesFromXmlTag(tag);
            } //
            else if (element instanceof XmlAttribute attr) {
                return getReferencesFromXmlAttribute(attr);
            } //
            else if (element instanceof XmlAttributeValue value) {
                XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);

                if (attr != null) {
                    return getReferencesFromXmlAttributeValue(value, attr);
                }
            } //
            else if (element.getParent() instanceof XmlText text) {
                return getReferencesFromXmlText(text, text.getParentTag());
            }

            return PsiReference.EMPTY_ARRAY;
        });
    }

    /** 获取 xml 标签对应的引用（节点定义、xpl 函数定义等） */
    private PsiReference @NotNull [] getReferencesFromXmlTag(XmlTag tag) {
        // TODO xpl 函数的引用

        return PsiReference.EMPTY_ARRAY;
    }

    /** 获取 xml 属性名对应的引用（属性定义） */
    private PsiReference @NotNull [] getReferencesFromXmlAttribute(XmlAttribute attr) {
        String attrName = attr.getName();
        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
        IXDefAttribute attrDef = tagInfo != null ? tagInfo.getDefAttr(attrName) : null;

        SourceLocation loc = attrDef != null ? attrDef.getLocation() : null;
        if (loc == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: 在 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        String path = loc.getPath().replace("classpath:_vfs", "");
        // Note: 对于包含名字空间的属性，需仅对属性名建立引用，否则，会被默认的 xml 引用替代。
        // 不过，对于 XLang 而言，名字空间也无需建立引用
        TextRange textRange = new TextRange(attrName.indexOf(':') + 1, attrName.length());

        PsiReference[] refs = XmlPsiHelper.findPsiFilesByNopVfsPath(attr, path)
                                          .stream()
                                          .map((file) -> {
                                              PsiElement element = XmlPsiHelper.getPsiElementAt(file,
                                                                                                loc.getLine(),
                                                                                                loc.getCol());
                                              return element instanceof XmlAttribute
                                                     ? (XmlAttribute) element
                                                     : PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
                                          })
                                          .filter(Objects::nonNull)
                                          .map((defAttr) -> new XLangXDefReference(attr, textRange, defAttr))
                                          .toArray(PsiReference[]::new);
        if (refs.length > 0) {
            return refs;
        }

        String msg = NopPluginBundle.message("xlang.annotation.reference.attr-xdef-not-defined", attrName);
        return new PsiReference[] {
                new XLangNotFoundReference(attr, textRange, msg)
        };
    }

    /** 获取 xml 属性值对应的引用（文件、节点、属性类型等） */
    private PsiReference @NotNull [] getReferencesFromXmlAttributeValue(
            XmlAttributeValue refElement, XmlAttribute attr
    ) {
        // Note: XmlAttributeValue#getValue 的结果包含引号
        String attrValue = attr.getValue();
        if (StringHelper.isEmpty(attrValue)) {
            return PsiReference.EMPTY_ARRAY;
        }

        String attrName = attr.getName();

        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
        XDefTypeDecl attrDefType = tagInfo != null ? tagInfo.getDefAttrType(attrName) : null;
        // 在无节点定义时，则做默认识别
        if (attrDefType == null) {
            return getReferencesByDefault(refElement, attrValue);
        }

        // TODO 对于声明属性，仅对其类型的定义（涉及枚举和字典）做跳转
        if (tagInfo.isDefDeclaredAttr(attrName)) {
            return PsiReference.EMPTY_ARRAY;
        }

        // 根据属性声明的类型，对属性值做文件/名字引用跳转处理
        String stdDomain = attrDefType.getStdDomain();

        // Note: v-path 类型采用缺省处理
        if (XDefConstants.STD_DOMAIN_V_PATH.equals(stdDomain) //
            || XDefConstants.STD_DOMAIN_NAME_OR_V_PATH.equals(stdDomain) //
        ) {
            return getReferencesByVfsPath(refElement, attrValue);
        } //
        else if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
            return getReferencesFromVfsPathCsv(refElement, attrValue);
        } //
        else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
            return getReferencesFromXDefRef(refElement, attrValue);
        } //
        else {
            String xdslNs = XDefPsiHelper.getXDslNamespace(tagInfo.getTag());

            if ((xdslNs + ":prototype").equals(attrName)) {
                return getReferencesFromPrototype(refElement, attrValue, tagInfo);
            } else {
                String xdefNs = XDefPsiHelper.getXDefNamespace(tagInfo.getTag());

                if ((xdefNs + ":key-attr").equals(attrName)) {
                    return getReferencesFromKeyAttr(refElement, attrValue, tagInfo);
                } else if ((xdefNs + ":unique-attr").equals(attrName)) {
                    return getReferencesFromUniqueAttr(refElement, attrValue, tagInfo);
                }
            }
        }

        // TODO 其他引用识别
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />

        return getReferencesByDefault(refElement, attrValue);
    }

    private PsiReference[] getReferencesFromXmlText(XmlText refElement, XmlTag tag) {
        if (tag == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        return PsiReference.EMPTY_ARRAY;
    }

    /** 对文本做默认的引用识别 */
    private PsiReference[] getReferencesByDefault(XmlAttributeValue attrValueElement, String attrValue) {
        if (!attrValue.endsWith(".xdef")) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        return getReferencesByVfsPath(attrValueElement, attrValue, textRange);
    }

    /** 获取指定路径的引用（文件） */
    private PsiReference[] getReferencesByVfsPath(XmlAttributeValue attrValueElement, String attrValue) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        return getReferencesByVfsPath(attrValueElement, attrValue, textRange);
    }

    /** 从 csv 文本中获取引用 */
    private PsiReference[] getReferencesFromVfsPathCsv(XmlAttributeValue attrValueElement, String attrValue) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        Map<TextRange, String> rangePathMap = extractPathsFromCsv(attrValue);

        List<PsiReference> list = new ArrayList<>(rangePathMap.size());
        rangePathMap.forEach((textRange, path) -> {
            PsiReference[] refs = getReferencesByVfsPath(attrValueElement, path, textRange.shiftRight(1));

            list.addAll(Arrays.stream(refs).toList());
        });

        return list.toArray(PsiReference[]::new);
    }

    /** 从 <code>xdef-ref</code> 类型的属性值中获取引用 */
    private PsiReference[] getReferencesFromXDefRef(XmlAttributeValue attrValueElement, String attrValue) {
        // - /nop/schema/xdef.xdef:
        //   - `<schema xdef:ref="schema-node.xdef" />`
        //   - `<item xdef:ref="ISchema" />`
        // - /nop/schema/schema/schema-node.xdef:
        //   `<schema ref="/test/test-filter.xdef#FilterCondition" />`

        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        String ref;
        String path = null;
        List<PsiFile> psiFiles;
        // 含有后缀的，视为文件引用
        if (attrValue.indexOf(".") > 0) {
            int hashIndex = attrValue.indexOf('#');

            path = hashIndex > 0 ? attrValue.substring(0, hashIndex) : attrValue;
            ref = hashIndex > 0 ? attrValue.substring(hashIndex + 1) : null;

            // 文件引用直接返回
            if (ref == null) {
                return getReferencesByVfsPath(attrValueElement, path, textRange);
            }

            psiFiles = XmlPsiHelper.findPsiFilesByNopVfsPath(attrValueElement, path);
        }
        // 否则，视为名字引用
        else {
            ref = attrValue;
            // Note: 只能引用当前文件（不一定是 vfs）内的名字
            psiFiles = Collections.singletonList(attrValueElement.getContainingFile());
        }

        // 收集引用节点属性
        PsiReference[] refs = psiFiles.stream()
                                      .map((file) -> (XmlAttribute) XmlPsiHelper.findFirstElement(file, (element) -> {
                                          if (element instanceof XmlAttribute attr) {
                                              String name = attr.getName();
                                              String value = attr.getValue();

                                              // Note: xdef-ref 引用的只能是 xdef:name 命名的节点
                                              return ("xdef:name".equals(name) //
                                                      || "meta:name".equals(name) //
                                                     ) && ref.equals(value);
                                          }
                                          return false;
                                      }))
                                      .filter(Objects::nonNull)
                                      .map((attr) -> new XLangElementReference(attrValueElement, textRange, attr))
                                      .toArray(PsiReference[]::new);
        if (refs.length > 0) {
            return refs;
        }

        String msg = path == null
                     ? NopPluginBundle.message("xlang.annotation.reference.xdef-ref-not-found", ref)
                     : NopPluginBundle.message("xlang.annotation.reference.xdef-ref-not-found-in-path", ref, path);
        return new PsiReference[] {
                new XLangNotFoundReference(attrValueElement, textRange, msg)
        };
    }

    /** 从 <code>x:prototype</code> 的属性值中获取引用 */
    private PsiReference[] getReferencesFromPrototype(
            XmlAttributeValue attrValueElement, String attrValue, XmlTagInfo tagInfo
    ) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

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
            String msg = NopPluginBundle.message("xlang.annotation.reference.x-prototype-no-parent");
            return new PsiReference[] {
                    new XLangNotFoundReference(attrValueElement, textRange, msg)
            };
        }

        XmlTag protoTag = XmlPsiHelper.getChildTagByAttr(parentTag, keyAttr, attrValue);
        if (protoTag == null) {
            String msg = keyAttr == null
                         ? NopPluginBundle.message("xlang.annotation.reference.x-prototype-tag-not-found",
                                                   attrValue)
                         : NopPluginBundle.message("xlang.annotation.reference.x-prototype-attr-not-found",
                                                   keyAttr,
                                                   attrValue);
            return new PsiReference[] {
                    new XLangNotFoundReference(attrValueElement, textRange, msg)
            };
        }

        // 定位到目标属性或标签上
        XmlElement target = keyAttr != null ? protoTag.getAttribute(keyAttr) : protoTag;

        return new PsiReference[] { new XLangElementReference(attrValueElement, textRange, target) };
    }

    /** 从 <code>xdef:key-attr</code> 的属性值中获取引用 */
    private PsiReference[] getReferencesFromKeyAttr(
            XmlAttributeValue attrValueElement, String attrValue, XmlTagInfo tagInfo
    ) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        PsiReference[] refs = XmlPsiHelper.getAttrsFromChildTag(tagInfo.getTag(), attrValue)
                                          .stream()
                                          .map((attr) -> new XLangElementReference(attrValueElement, textRange, attr))
                                          .toArray(PsiReference[]::new);
        if (refs.length > 0) {
            return refs;
        }

        String msg = NopPluginBundle.message("xlang.annotation.reference.xdef-key-attr-not-found", attrValue);
        return new PsiReference[] {
                new XLangNotFoundReference(attrValueElement, textRange, msg)
        };
    }

    /** 从 <code>xdef:unique-attr</code> 的属性值中获取引用 */
    private PsiReference[] getReferencesFromUniqueAttr(
            XmlAttributeValue attrValueElement, String attrValue, XmlTagInfo tagInfo
    ) {
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange textRange = new TextRange(1, attrValue.length() + 1);

        // 仅从当前节点中取引用到的属性
        XmlTag tag = tagInfo.getTag();
        XmlAttribute attr = tag.getAttribute(attrValue);
        if (attr == null) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.xdef-unique-attr-no-found", attrValue);
            return new PsiReference[] {
                    new XLangNotFoundReference(attrValueElement, textRange, msg)
            };
        }

        return new PsiReference[] { new XLangElementReference(attrValueElement, textRange, attr) };
    }

    private PsiReference[] getReferencesByVfsPath(XmlElement refElement, String path, TextRange textRange) {
        if (!StringHelper.isValidFilePath(path) || path.indexOf('.') <= 0) {
            return PsiReference.EMPTY_ARRAY;
        }

        PsiReference[] refs = XmlPsiHelper.findPsiFilesByNopVfsPath(refElement, path)
                                          .stream()
                                          .map((file) -> new XLangVfsFileReference(refElement, textRange, file))
                                          .toArray(PsiReference[]::new);

        if (refs.length > 0) {
            return refs;
        }

        String msg = NopPluginBundle.message("xlang.annotation.reference.vfs-file-not-found", path);
        return new PsiReference[] {
                new XLangNotFoundReference(refElement, textRange, msg)
        };
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


