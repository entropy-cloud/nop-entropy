package io.nop.ai.coder.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.XDslCleaner;
import io.nop.xlang.xdsl.XDslExtracter;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.ai.coder.AiCoderErrors.ARG_DATA;
import static io.nop.ai.coder.AiCoderErrors.ARG_HEADERS;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH;

public class AiCoderHelper {
    public static String camelCaseName(String name, boolean firstLetterUpper) {
        String code = underscoreName(name, false);
        return StringHelper.camelCase(code, firstLetterUpper);
    }

    public static String underscoreName(String name, boolean upperCase) {
        String code = StringHelper.camelCaseToUnderscore(name, upperCase);
        // a_b_c这种名称如果作为aBC这种形式，会导致根据get方法反向确定的属性名与原始名称不一致
        if (code.length() >= 3 && code.charAt(1) == '_') {
            return code.charAt(0) + code.substring(2);
        }
        return code;
    }

    public static String getRelationNameFromColCode(String colCode, String refEntityName) {
        if (colCode.equalsIgnoreCase("_id") || colCode.equalsIgnoreCase("id"))
            return camelCaseName(StringHelper.lastPart(refEntityName, '.'), false);

        if (StringHelper.endsWithIgnoreCase(colCode, "_id")) {
            return camelCaseName(colCode.substring(0, colCode.length() - "_id".length()), false);
        }
        return camelCaseName(colCode, false) + "Obj";
    }

    /**
     * 解析以分隔符分隔的表头和以逗号分隔的数据
     *
     * @param headers   比如让AI按照 A~B~C或者A,B,C这种紧凑的方式返回数据
     * @param data      AI模型返回的数据
     * @param separator 分隔符
     * @return 从header到数据的映射
     */
    public static Map<String, String> parseList(String headers, String data, char separator) {
        List<String> parts = StringHelper.split(headers, separator);
        if (StringHelper.isBlank(data))
            return null;

        List<String> list = StringHelper.split(data, separator);
        if (parts.size() != list.size())
            throw new NopException(ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH)
                    .param(ARG_HEADERS, headers).param(ARG_DATA, data);

        Map<String, String> ret = new LinkedHashMap<>();
        for (int i = 0, n = parts.size(); i < n; i++) {
            String part = parts.get(i).trim();
            String item = list.get(i).trim();
            ret.put(part, item);
        }
        return ret;
    }

    public static XNode validateDslNode(String xdefPath, XNode dslNode) {
        new XDslValidator()
                .validateForXDef(xdefPath, dslNode);
        return dslNode;
    }

    public static XNode cleanDslNode(String xdefPath, XNode dslNode) {
        XDslCleaner.INSTANCE.cleanForXDef(xdefPath, dslNode);
        return dslNode;
    }

    public static XNode extractDslNode(String xdefPath, XNode dslNode) {
        return XDslExtracter.INSTANCE.extractForXDef(xdefPath, dslNode);
    }

    public static XNode cleanAndValidateDslNode(String xdefPath, XNode dslNode) {
        IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
        XDslCleaner.INSTANCE.clean(dslNode, xdef.getRootNode());
        new XDslValidator().validate(dslNode, xdef.getRootNode(), true);
        return dslNode;
    }

    public static XNode mergeDslNode(String xdefPath, XNode dslNodeA, XNode dslNodeB) {
        if (dslNodeA == dslNodeB)
            return dslNodeA;

        IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
        dslNodeA = dslNodeA.cloneInstance();
        dslNodeB = dslNodeB.cloneInstance();
        new DeltaMerger(XDslKeys.DEFAULT).merge(dslNodeA, dslNodeB, xdef.getRootNode(), false);
        XDslCleaner.INSTANCE.removeMergeOp(dslNodeA);
        return dslNodeA;
    }

    public static void saveDslNode(String xdefPath, XNode dslNode, IResource resource) {
        // 这里并不要求dslNode中的属性都在xdef定义范围内。因为可能是将Ai生成的模型保存为平台可识别的文件。
        // AI生成的模型可能会具有额外的扩展属性。
        addXmlNs(dslNode);
        dslNode.setAttr(XDslKeys.DEFAULT.SCHEMA, xdefPath);
        dslNode.setAttr(XLangConstants.XMLNS_X, XLangConstants.XDSL_SCHEMA_XDSL);
        dslNode.saveToResource(resource, null);
    }

    public static Object parseDslNode(String xdefPath, XNode dslNode) {
        if (!dslNode.hasAttr(XDslKeys.DEFAULT.SCHEMA)) {
            dslNode.setAttr(XDslKeys.DEFAULT.SCHEMA, xdefPath);
        }
        return DslModelHelper.parseDslNode(xdefPath, dslNode);
    }

    public static void addXmlNs(XNode dslNode) {
        Set<String> namespaces = dslNode.getAllNamespaces();
        for (String namespace : namespaces) {
            String nsAttr = XLangConstants.NS_XMLNS_PREFIX + namespace;
            if (!dslNode.hasAttr(nsAttr))
                dslNode.setAttr(nsAttr, namespace);
        }
    }
}
