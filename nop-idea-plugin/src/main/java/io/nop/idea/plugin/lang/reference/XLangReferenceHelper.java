package io.nop.idea.plugin.lang.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.utils.PsiClassHelper;
import io.nop.idea.plugin.vfs.NopVirtualFileReference;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;

import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_DICT;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_ENUM;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_ATTR_PREFIX;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_OPTIONS;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class XLangReferenceHelper {

    /**
     * 根据数据域类型识别引用
     *
     * @return 若返回 <code>null</code>，则表示未支持对指定类型的处理
     */
    public static PsiReference[] getReferencesByStdDomain(
            XmlElement refElement, String refValue, String stdDomain
    ) {
        // Note: 计算引用源文本（XmlAttributeValue#getText 的结果包含引号）与引用值文本之间的文本偏移量，
        // 从而精确匹配与引用相关的文本内容
        int textRangeOffset = refElement.getText().indexOf(refValue);
        TextRange textRange = new TextRange(0, refValue.length()).shiftRight(textRangeOffset);

        if (XDefConstants.STD_DOMAIN_V_PATH.equals(stdDomain) //
            || XDefConstants.STD_DOMAIN_NAME_OR_V_PATH.equals(stdDomain) //
        ) {
            return getReferencesByVfsPath(refElement, refValue, textRange);
        } //
        else if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
            return getReferencesFromVfsPathCsv(refElement, refValue, textRangeOffset);
        } //
        else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
            return new PsiReference[] {
                    new XLangStdDomainXdefRefReference(refElement, textRange, refValue)
            };
        }

        return null;
    }

    /** 根据属性的类型定义文本识别引用 */
    public static PsiReference[] getReferencesFromDefType(
            XmlElement refElement, String refValue, XDefTypeDecl refDefType
    ) {
        // (!~#)?{stdDomain}(:{options})?(={defaultValue})?
        String stdDomain = refDefType.getStdDomain();
        String options = refDefType.getOptions();
        Object defaultValue = refDefType.getDefaultValue();
        List<String> defaultAttrNames = refDefType.getDefaultAttrNames();

        // Note: 计算引用源文本（XmlAttributeValue#getText 的结果包含引号）与引用值文本之间的文本偏移量，
        // 从而精确匹配与引用相关的文本内容
        int textRangeOffset = refElement.getText().indexOf(refValue);

        int stdDomainIndex = refValue.indexOf(stdDomain);
        int optionsIndex = options != null ? refValue.indexOf(XDEF_TYPE_PREFIX_OPTIONS + options) + 1 : -1;
        int defaultValueIndex = defaultValue != null ? refValue.indexOf("=" + defaultValue) + 1 : -1;
        int defaultAttrNamesIndex = defaultAttrNames != null ? refValue.indexOf('=' + XDEF_TYPE_ATTR_PREFIX)
                                                               + 1
                                                               + XDEF_TYPE_ATTR_PREFIX.length() : -1;

        List<PsiReference> refs = new ArrayList<>();

        // 引用数据域的类型定义
        TextRange textRange = new TextRange(0, stdDomain.length()).shiftRight(textRangeOffset + stdDomainIndex);
        refs.add(new XLangDictOptionReference(refElement, textRange, "core/std-domain", stdDomain));

        if (optionsIndex > 0) {
            int offset = textRangeOffset + optionsIndex;
            textRange = new TextRange(0, options.length()).shiftRight(offset);

            if (STD_DOMAIN_ENUM.equals(stdDomain)) {
                // Note: 忽略 enum:a|b|c|d 形式的数据
                if (StringHelper.isValidClassName(options)) {
                    PsiReference[] ref = PsiClassHelper.createJavaClassReferences(refElement, options, offset);

                    Collections.addAll(refs, ref);
                }
            } //
            else if (STD_DOMAIN_DICT.equals(stdDomain)) {
                refs.add(new XLangDictOptionReference(refElement, textRange, options));
            }
        }

        // 引用字典/枚举值
        if (defaultValueIndex > 0) {
            textRange = new TextRange(0, defaultValue.toString().length()).shiftRight(textRangeOffset
                                                                                      + defaultValueIndex);
            refs.add(new XLangDictOptionReference(refElement, textRange, options, defaultValue));
        }

        // 引用节点属性
        if (defaultAttrNamesIndex > 0) {
            String csv = refValue.substring(defaultAttrNamesIndex);
            Map<TextRange, String> rangeNameMap = extractValuesFromCsv(csv);

            rangeNameMap.forEach((range, name) -> {
                TextRange r = range.shiftRight(textRangeOffset + defaultAttrNamesIndex);

                refs.add(new XLangParentTagAttrReference(refElement, r, name));
            });
        }

        return refs.toArray(PsiReference[]::new);
    }

    /** 从 csv 文本中识别对 vfs 资源路径的引用 */
    public static PsiReference[] getReferencesFromVfsPathCsv(
            XmlElement refElement, String refValue, int textRangeOffset
    ) {
        Map<TextRange, String> rangePathMap = extractValuesFromCsv(refValue);

        List<PsiReference> list = new ArrayList<>(rangePathMap.size());
        rangePathMap.forEach((textRange, path) -> {
            TextRange range = textRange.shiftRight(textRangeOffset);
            PsiReference[] refs = getReferencesByVfsPath(refElement, path, range);

            Collections.addAll(list, refs);
        });

        return list.toArray(PsiReference[]::new);
    }

    /** 对文本做默认的引用识别 */
    public static PsiReference[] getReferencesFromText(XmlElement refElement, String refValue) {
        if (!refValue.endsWith(".xdef")) {
            return PsiReference.EMPTY_ARRAY;
        }

        // Note: 计算引用源文本（XmlAttributeValue#getText 的结果包含引号）与引用值文本之间的文本偏移量，
        // 从而精确匹配与引用相关的文本内容
        int textRangeOffset = refElement.getText().indexOf(refValue);
        TextRange textRange = new TextRange(0, refValue.length()).shiftRight(textRangeOffset);

        return getReferencesByVfsPath(refElement, refValue, textRange);
    }

    /** 识别对 vfs 资源路径的引用 */
    public static PsiReference[] getReferencesByVfsPath(XmlElement refElement, String path, TextRange textRange) {
        if (!StringHelper.isValidFilePath(path) || path.lastIndexOf('.') <= 0) {
            return PsiReference.EMPTY_ARRAY;
        }

        return new PsiReference[] {
                new NopVirtualFileReference(refElement, textRange, path)
        };
    }

    private static Map<TextRange, String> extractValuesFromCsv(String csv) {
        Map<TextRange, String> rangePathMap = new HashMap<>();

        TextScanner sc = TextScanner.fromString(null, csv);

        sc.skipBlank();
        while (!sc.isEnd()) {
            int offset = sc.pos;
            MutableString buf = sc.useBuf();
            sc.nextUntil(s -> s.cur == ',' || StringHelper.isSpace(sc.cur), sc::appendToBuf);

            String value = buf.toString();
            rangePathMap.put(new TextRange(offset, sc.pos), value);

            sc.next();
            sc.skipBlank();
        }

        return rangePathMap;
    }
}
