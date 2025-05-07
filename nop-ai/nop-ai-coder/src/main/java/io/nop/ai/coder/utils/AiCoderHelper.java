package io.nop.ai.coder.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
}
