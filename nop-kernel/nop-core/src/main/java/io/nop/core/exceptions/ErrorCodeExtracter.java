package io.nop.core.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

public class ErrorCodeExtracter {
    public static final ErrorCodeExtracter INSTANCE = new ErrorCodeExtracter();

    /**
     * 从CoreErrors这种异常码定义类中反射得到异常码描述信息
     *
     * @param errorsClass 异常码定义类
     * @return errorCode到ErrorCode对象的定义类
     */
    public Map<String, ErrorCode> extractErrorCodes(Class<?> errorsClass) {
        Map<String, ErrorCode> errorCodeMap = new TreeMap<>();

        for (Field field : errorsClass.getFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                if (field.getType() == ErrorCode.class) {
                    try {
                        ErrorCode errorCode = (ErrorCode) field.get(null);
                        errorCodeMap.put(errorCode.getErrorCode(), errorCode);
                    } catch (IllegalAccessException e) {
                        throw NopException.adapt(e);
                    }
                }
            }
        }

        return errorCodeMap;
    }

    public Map<String, String> toDescriptionMap(Map<String, ErrorCode> errorCodeMap) {
        Map<String, String> map = new TreeMap<>();
        errorCodeMap.forEach((name, value) -> {
            map.put(name, value.getDescription());
        });
        return map;
    }

    public void saveToFile(File file, Class<?>... classes) {
        Map<String, ErrorCode> map = new TreeMap<>();
        for (Class<?> clazz : classes) {
            map.putAll(extractErrorCodes(clazz));
        }

        Map<String, String> descMap = toDescriptionMap(map);
        String text = JsonTool.serializeToYaml(descMap);
        FileHelper.writeText(file, text, null);
    }
}