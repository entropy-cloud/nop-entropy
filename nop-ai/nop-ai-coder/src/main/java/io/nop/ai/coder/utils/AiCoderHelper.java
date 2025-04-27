package io.nop.ai.coder.utils;

import io.nop.commons.util.StringHelper;

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
}
