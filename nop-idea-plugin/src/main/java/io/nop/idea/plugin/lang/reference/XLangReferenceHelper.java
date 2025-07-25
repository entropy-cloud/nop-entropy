/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.idea.plugin.utils.PsiClassHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.idea.plugin.vfs.NopVirtualFileReference;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdsl.XDslParseHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_CLASS_NAME;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_CLASS_NAME_SET;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_DEF_TYPE;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_DICT;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_ENUM;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_GENERIC_TYPE;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_GENERIC_TYPE_LIST;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_NAME_OR_V_PATH;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_PACKAGE_NAME;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_V_PATH;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_V_PATH_LIST;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_XDEF_ATTR;
import static io.nop.xlang.xdef.XDefConstants.STD_DOMAIN_XDEF_REF;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_ATTR_PREFIX;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_OPTIONS;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class XLangReferenceHelper {
    /** 无命名空间的属性排在最前面，且 xdef 名字空间排在其他名字空间之前 */
    public static final Comparator<String> XLANG_NAME_COMPARATOR = (a, b) -> {
        int aNsIndex = a.indexOf(':');
        int bNsIndex = b.indexOf(':');

        if (aNsIndex <= 0 && bNsIndex <= 0) {
            return a.compareTo(b);
        } //
        else if (aNsIndex > 0 && bNsIndex > 0) {
            return !a.startsWith("xdef:") && b.startsWith("xdef:")
                   ? 1
                   : a.startsWith("xdef:") && !b.startsWith("xdef:") //
                     ? -1 : a.compareTo(b);
        }

        return Integer.compare(aNsIndex, bNsIndex);
    };

    /**
     * 根据{@link XDefTypeDecl 定义类型}识别引用
     *
     * @return 若返回 <code>null</code>，则表示未支持对指定类型的处理
     */
    public static PsiReference[] getReferencesByDefType(
            XmlElement refElement, String refValue, XDefTypeDecl refDefType
    ) {
        // Note: 计算引用源文本（XmlAttributeValue#getText 的结果包含引号）与引用值文本之间的文本偏移量，
        // 从而精确匹配与引用相关的文本内容
        int textRangeOffset = refElement.getText().indexOf(refValue);
        TextRange textRange = new TextRange(0, refValue.length()).shiftRight(textRangeOffset);

        String stdDomain = refDefType.getStdDomain();
        return switch (stdDomain) {
            case STD_DOMAIN_XDEF_REF -> //
                    new PsiReference[] {
                            new XLangStdDomainXdefRefReference(refElement, textRange, refValue)
                    };
            case STD_DOMAIN_V_PATH, STD_DOMAIN_NAME_OR_V_PATH -> //
                    getReferencesByVfsPath(refElement, refValue, textRange);
            case STD_DOMAIN_V_PATH_LIST -> //
                    getReferencesFromVfsPathCsv(refElement, refValue, textRangeOffset);
            case STD_DOMAIN_GENERIC_TYPE, STD_DOMAIN_GENERIC_TYPE_LIST -> //
                    getReferencesFromGenericTypeCsv(refElement, refValue, textRangeOffset);
            case STD_DOMAIN_CLASS_NAME, STD_DOMAIN_CLASS_NAME_SET -> //
                    PsiClassHelper.createJavaClassReferences(refElement, refValue, textRangeOffset);
            case STD_DOMAIN_PACKAGE_NAME -> //
                    PsiClassHelper.createPackageReferences(refElement, refValue, textRangeOffset);
            case STD_DOMAIN_DICT, STD_DOMAIN_ENUM -> //
                    new PsiReference[] {
                            new XLangDictOptionReference(refElement, textRange, refDefType.getOptions(), refValue)
                    };
            case STD_DOMAIN_XDEF_ATTR, STD_DOMAIN_DEF_TYPE -> //
                    getReferencesFromDefType(refElement, refValue, refValue);
            default -> new PsiReference[] {
                    new XLangStdDomainGeneralReference(refElement, textRange, refDefType)
            };
        };
    }

    /** 根据属性的类型定义识别引用 */
    public static PsiReference[] getReferencesFromDefType(
            XmlElement refElement, String refValue, String refDefTypeText
    ) {
        XDefTypeDecl refDefType;
        try {
            refDefType = XDslParseHelper.parseDefType(null, null, refDefTypeText);
        } catch (Exception ignore) {
            return PsiReference.EMPTY_ARRAY;
        }

        // (!~#)?{stdDomain}(:{options})?(={defaultValue})?
        String stdDomain = refDefType.getStdDomain();
        String options = refDefType.getOptions();
        String defaultValue = Objects.toString(refDefType.getDefaultValue(), null);
        List<String> defaultAttrNames = refDefType.getDefaultAttrNames();

        // Note: 计算引用源文本（XmlAttributeValue#getText 的结果包含引号）与引用值文本之间的文本偏移量，
        // 从而精确匹配与引用相关的文本内容
        int textRangeOffset = refElement.getText().indexOf(refValue);

        int indexOffset = 0;
        int stdDomainIndex = refValue.indexOf(stdDomain, indexOffset);

        indexOffset = stdDomainIndex + stdDomain.length();
        int optionsIndex = options != null ? refValue.indexOf(XDEF_TYPE_PREFIX_OPTIONS + options, indexOffset) + 1 : -1;

        indexOffset = optionsIndex + (options != null ? options.length() : 1);
        int defaultValueIndex = defaultValue != null ? refValue.indexOf('=' + defaultValue, indexOffset) + 1 : -1;
        int defaultAttrNamesIndex = defaultAttrNames != null //
                                    ? refValue.indexOf('=' + XDEF_TYPE_ATTR_PREFIX, indexOffset)
                                      + 1
                                      + XDEF_TYPE_ATTR_PREFIX.length() //
                                    : -1;

        List<PsiReference> refs = new ArrayList<>();

        // 引用数据域的类型定义
        TextRange textRange = new TextRange(0, stdDomain.length()).shiftRight(textRangeOffset + stdDomainIndex);
        refs.add(new XLangStdDomainReference(refElement, textRange, stdDomain));

        if (optionsIndex > 0) {
            int offset = textRangeOffset + optionsIndex;
            textRange = new TextRange(0, options.length()).shiftRight(offset);

            switch (stdDomain) {
                case STD_DOMAIN_ENUM -> {
                    // Note: 忽略 enum:a|b|c|d 形式的数据
                    if (StringHelper.isValidClassName(options)) {
                        refs.add(new XLangStdDomainEnumReference(refElement, textRange, options));
                    }
                }
                case STD_DOMAIN_DICT -> {
                    refs.add(new XLangStdDomainDictReference(refElement, textRange, options));
                }
            }
        }

        if (defaultValueIndex > 0) {
            textRange = new TextRange(0, defaultValue.length()).shiftRight(textRangeOffset + defaultValueIndex);

            // 引用字典项或枚举值
            switch (stdDomain) {
                case STD_DOMAIN_ENUM, STD_DOMAIN_DICT -> {
                    refs.add(new XLangDictOptionReference(refElement, textRange, options, defaultValue));
                }
            }
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
        Map<TextRange, String> rangeMap = extractValuesFromCsv(refValue);

        List<PsiReference> list = new ArrayList<>(rangeMap.size());
        rangeMap.forEach((textRange, value) -> {
            TextRange range = textRange.shiftRight(textRangeOffset);
            PsiReference[] refs = getReferencesByVfsPath(refElement, value, range);

            Collections.addAll(list, refs);
        });

        return list.toArray(PsiReference[]::new);
    }

    /** 对文本做默认的引用识别 */
    public static PsiReference[] getReferencesFromText(XmlElement refElement, String refValue) {
        if (!refValue.endsWith(".xdef") //
            && !refValue.endsWith(".xpl") //
            && !refValue.endsWith(".xgen") //
            && !refValue.endsWith(".xrun") //
            && !refValue.endsWith(".xlib") //
        ) {
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

    /** 从 csv 文本中识别对 {@link IGenericType} 的引用 */
    public static PsiReference[] getReferencesFromGenericTypeCsv(
            XmlElement refElement, String refValue, int textRangeOffset
    ) {
        Map<TextRange, String> rangeMap = extractValuesFromCsv(refValue);

        List<PsiReference> list = new ArrayList<>(rangeMap.size());
        rangeMap.forEach((textRange, value) -> {
            TextRange range = textRange.shiftRight(textRangeOffset);
            PsiReference ref = new XLangStdDomainGenericTypeReference(refElement, range, value);

            list.add(ref);
        });

        return list.toArray(PsiReference[]::new);
    }

    public static NopVirtualFile createNopVfsForDict(
            PsiElement refElement, String dictName, Object dictOptionValue
    ) {
        Function<PsiFile, PsiElement> targetResolver = //
                (file) -> XmlPsiHelper.findFirstElement(file, (element) -> {
                    if (element instanceof LeafPsiElement value //
                        && dictOptionValue.equals(value.getText()) //
                    ) {
                        PsiElement parent = //
                                PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);
                        PsiElement key = parent != null ? parent.getFirstChild() : null;

                        return key != null && "value".equals(key.getText());
                    }
                    return false;
                });

        String path = "/dict/" + dictName + ".dict.yaml";

        return new NopVirtualFile(refElement, path, dictOptionValue != null ? targetResolver : null);
    }

    public static List<String> getRegisteredStdDomains() {
        StdDomainRegistry registry = StdDomainRegistry.instance();

        try {
            Field field = registry.getClass().getDeclaredField("domainHandlers");
            field.setAccessible(true);

            List<String> result = new ArrayList<>(((Map<String, ?>) field.get(registry)).keySet());
            Collections.sort(result);

            return result;
        } catch (Exception ignore) {
            return new ArrayList<>();
        }
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
