/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import static io.nop.core.CoreErrors.ARG_MESSAGE;
import static io.nop.core.CoreErrors.ERR_LANG_DECODE_INVALID_MESSAGE;

/**
 * 将消息对象编码为文本格式，用于存储到缓存或者文件中。编码后第一行可能会存储数据类型信息，便于解码时使用。
 */
public class PrefixEncodeHelper {
    public static final String PREFIX_REQUEST = "$r:";
    public static final String PREFIX_RESPONSE = "$s:";
    public static final String PREFIX_DATA = "$d:";
    public static final String PREFIX_STRING = "$:";

    /**
     * Boolean和Number类型会被编码为字符串，解码时也是作为字符串返回，不会自动转型为Boolean和Number
     * <ol>
     * <li>如果是null或者空串，则转换为空串</li>
     * <li>如果是Number类型，则转换为字符串返回。</li>
     * <li>如果是Boolean类型，则转换为0/1。</li>
     * <li>如果是字符串，且首字母是$，则转换为$:string</li>
     * <li>如果是ApiRequest，则转换为$r:class + \n + json</li>
     * <li>如果是ApiResponse,则转换为$s:class + \n + json</li>
     * <li>如果是其他情况, 则转换为 $d:class + \n + json</li>
     * </ol>
     *
     * @param message
     * @return
     */
    public static String encode(Object message) {
        if (StringHelper.isEmptyObject(message))
            return "";

        if (message instanceof Number)
            return message.toString();

        if (message instanceof Boolean)
            return Boolean.TRUE.equals(message) ? "1" : "0";

        if (message instanceof String) {
            String str = message.toString();
            if (str.charAt(0) == PREFIX_STRING.charAt(0))
                return PREFIX_STRING + str;
            return str;
        }

        if (message instanceof ApiRequest) {
            ApiRequest request = (ApiRequest) message;
            String result = PREFIX_REQUEST + getDataClass(request.getData()) + "\n" + JSON.stringify(request);
            return result;
        } else if (message instanceof ApiResponse) {
            ApiResponse response = (ApiResponse) message;
            String result = PREFIX_RESPONSE + getDataClass(response.getData()) + "\n" + JSON.stringify(response);
            return result;
        } else {
            String result = PREFIX_DATA + getDataClass(message) + "\n" + JSON.stringify(message);
            return result;
        }
    }

    public static Object decode(String str, IClassLoader classLoader) {
        if (StringHelper.isEmpty(str))
            return null;

        if (str.charAt(0) != PREFIX_STRING.charAt(0))
            return str;

        if (str.startsWith(PREFIX_STRING))
            return str.substring(PREFIX_STRING.length());

        int pos = str.indexOf('\n');
        if (pos < 0)
            throw new NopException(ERR_LANG_DECODE_INVALID_MESSAGE).param(ARG_MESSAGE, StringHelper.limitLen(str, 100));

        String message = str;
        String typePrefix = str.substring(0, pos);
        str = str.substring(pos + 1);

        if (typePrefix.startsWith(PREFIX_DATA)) {
            Class<?> dataClass = loadDataClass(typePrefix.substring(PREFIX_DATA.length()), classLoader);
            return JsonTool.parseBeanFromText(str, dataClass);
        } else if (typePrefix.startsWith(PREFIX_REQUEST)) {
            Class<?> dataClass = loadDataClass(typePrefix.substring(PREFIX_REQUEST.length()), classLoader);
            IGenericType type = JavaGenericTypeBuilder.buildParameterizedType(ApiRequest.class, dataClass);
            return JsonTool.parseBeanFromText(str, type);
        } else if (typePrefix.startsWith(PREFIX_RESPONSE)) {
            Class<?> dataClass = loadDataClass(typePrefix.substring(PREFIX_REQUEST.length()), classLoader);
            IGenericType type = JavaGenericTypeBuilder.buildParameterizedType(ApiResponse.class, dataClass);
            return JsonTool.parseBeanFromText(str, type);
        } else {
            throw new NopException(ERR_LANG_DECODE_INVALID_MESSAGE).param(ARG_MESSAGE,
                    StringHelper.limitLen(message, 100));
        }
    }

    static String getDataClass(Object obj) {
        if (obj == null || obj instanceof String)
            return "";
        return obj.getClass().getName();
    }

    static Class<?> loadDataClass(String className, IClassLoader classLoader) {
        if (StringHelper.isEmpty(className))
            return String.class;
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw NopException.adapt(e);
        }
    }
}
