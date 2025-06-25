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
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
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
            else if (isElementType(element, XmlElementType.XML_DATA_CHARACTERS)) {
                XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);

                if (tag != null) {
                    return getReferencesFromXmlText((XmlElement) element, tag);
                }
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
        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
        if (tagInfo == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        String attrName = attr.getName();
        IXDefAttribute attrDef = tagInfo.getDefAttr(attrName);
        SourceLocation loc = attrDef != null ? attrDef.getLocation() : null;

        // Note:
        // - 属性可能定义在外部 xdef 中
        // - SourceLocation#getPath() 得到的 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        String path = loc != null //
                      ? loc.getPath().replace("classpath:_vfs", "") //
                      : tagInfo.getDef().resourcePath();

        // Note: 对于包含名字空间的属性，需仅对属性名建立引用，否则，会被默认的 xml 引用替代。
        // 不过，对于 XLang 而言，名字空间也无需建立引用
        TextRange textRange = new TextRange(attrName.indexOf(':') + 1, attrName.length());

        PsiReference[] refs = XmlPsiHelper.findPsiFilesByNopVfsPath(attr, path)
                                          .stream()
                                          .map((file) -> {
                                              if (loc == null) {
                                                  // 尝试取 xdef:unknown-attr
                                                  SourceLocation sl = tagInfo.getDefNode().getLocation();
                                                  XmlTag tag = XmlPsiHelper.getPsiElementAt(file, sl, XmlTag.class);

                                                  return tag != null ? tag.getAttribute("xdef:unknown-attr") : null;
                                              } else {
                                                  return XmlPsiHelper.getPsiElementAt(file, loc, XmlAttribute.class);
                                              }
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

        // 根据属性声明的类型，对属性值做文件/名字引用
        PsiReference[] refs = getReferencesByDefType(refElement, attrValue, attrDefType);
        if (refs != null) {
            return refs;
        }

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

        // TODO 其他引用识别
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />

        return getReferencesByDefault(refElement, attrValue);
    }

    private PsiReference[] getReferencesFromXmlText(XmlElement refElement, XmlTag tag) {
        XmlTagInfo tagInfo = tag != null ? XDefPsiHelper.getTagInfo(tag) : null;
        if (tagInfo == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        XDefTypeDecl tagDefType = tagInfo.getDefNode().getXdefValue();
        if (tagDefType == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        String refValue = refElement.getText();
        PsiReference[] refs = getReferencesByDefType(refElement, refValue, tagDefType);
        if (refs != null) {
            return refs;
        }

        return getReferencesByDefault(refElement, refValue);
    }

    /**
     * 根据数据域类型识别引用
     *
     * @return 若返回 <code>null</code>，则表示未支持对指定类型的处理
     */
    private PsiReference[] getReferencesByDefType(
            XmlElement refElement, String refValue, XDefTypeDecl refDefType
    ) {
        // Note: 计算引用源文本（XmlAttributeValue#getText 的结果包含引号）与引用值文本之间的文本偏移量，
        // 从而精确匹配与引用相关的文本内容
        int textRangeOffset = refElement.getText().indexOf(refValue);
        TextRange textRange = new TextRange(textRangeOffset, refValue.length() + textRangeOffset);

        String stdDomain = refDefType.getStdDomain();

        if (XDefConstants.STD_DOMAIN_V_PATH.equals(stdDomain) //
            || XDefConstants.STD_DOMAIN_NAME_OR_V_PATH.equals(stdDomain) //
        ) {
            return getReferencesByVfsPath(refElement, refValue, textRange);
        } //
        else if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
            return getReferencesFromVfsPathCsv(refElement, refValue, textRangeOffset);
        } //
        else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
            return getReferencesFromXDefRef(refElement, refValue, textRange);
        }

        return null;
    }

    /** 对文本做默认的引用识别 */
    private PsiReference[] getReferencesByDefault(XmlElement refElement, String refValue) {
        if (!refValue.endsWith(".xdef")) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: XmlAttributeValue 的文本范围是包含引号的
        int textRangeOffset = refElement instanceof XmlAttributeValue ? 1 : 0;
        TextRange textRange = new TextRange(textRangeOffset, refValue.length() + textRangeOffset);

        return getReferencesByVfsPath(refElement, refValue, textRange);
    }

    /** 从 csv 文本中获取引用 */
    private PsiReference[] getReferencesFromVfsPathCsv(
            XmlElement refElement, String refValue, int textRangeOffset
    ) {
        Map<TextRange, String> rangePathMap = extractPathsFromCsv(refValue);

        List<PsiReference> list = new ArrayList<>(rangePathMap.size());
        rangePathMap.forEach((textRange, path) -> {
            PsiReference[] refs = getReferencesByVfsPath(refElement, path, textRange.shiftRight(textRangeOffset));

            list.addAll(Arrays.stream(refs).toList());
        });

        return list.toArray(PsiReference[]::new);
    }

    /** 从 <code>xdef-ref</code> 类型的属性值中获取引用 */
    private PsiReference[] getReferencesFromXDefRef(XmlElement refElement, String refValue, TextRange textRange) {
        // - /nop/schema/xdef.xdef:
        //   - `<schema xdef:ref="schema-node.xdef" />`
        //   - `<item xdef:ref="ISchema" />`
        // - /nop/schema/schema/schema-node.xdef:
        //   `<schema ref="/test/test-filter.xdef#FilterCondition" />`

        String ref;
        String path = null;
        List<PsiFile> psiFiles;
        // 含有后缀的，视为文件引用：相对路径 .. 可能出现在开头，故而，检查最后一个 . 的位置
        if (refValue.lastIndexOf('.') > 0) {
            int hashIndex = refValue.indexOf('#');

            path = hashIndex > 0 ? refValue.substring(0, hashIndex) : refValue;
            ref = hashIndex > 0 ? refValue.substring(hashIndex + 1) : null;

            // 文件引用直接返回
            if (ref == null) {
                return getReferencesByVfsPath(refElement, path, textRange);
            }

            psiFiles = XmlPsiHelper.findPsiFilesByNopVfsPath(refElement, path);
        }
        // 否则，视为名字引用
        else {
            ref = refValue;
            // Note: 只能引用当前文件（不一定是 vfs）内的名字
            psiFiles = Collections.singletonList(refElement.getContainingFile());
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
                                      .map((attr) -> new XLangElementReference(refElement, textRange, attr))
                                      .toArray(PsiReference[]::new);
        if (refs.length > 0) {
            return refs;
        }

        String msg = path == null
                     ? NopPluginBundle.message("xlang.annotation.reference.xdef-ref-not-found", ref)
                     : NopPluginBundle.message("xlang.annotation.reference.xdef-ref-not-found-in-path", ref, path);
        return new PsiReference[] {
                new XLangNotFoundReference(refElement, textRange, msg)
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
        if (!StringHelper.isValidFilePath(path) || path.lastIndexOf('.') <= 0) {
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

        TextScanner sc = TextScanner.fromString(null, csv);

        sc.skipBlank();
        while (!sc.isEnd()) {
            int offset = sc.pos;
            MutableString buf = sc.useBuf();
            sc.nextUntil(s -> s.cur == ',' || StringHelper.isSpace(sc.cur), sc::appendToBuf);

            String path = buf.toString();
            rangePathMap.put(new TextRange(offset, sc.pos), path);

            sc.next();
            sc.skipBlank();
        }

        return rangePathMap;
    }
}


