/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface ApiErrors {
    String ARG_INDEX = "index";
    String ARG_SIZE = "size";
    String ARG_EXPECTED = "expected";
    String ARG_ACTUAL = "actual";
    String ARG_VALUE = "value";
    String ARG_PART = "part";
    String ARG_NAME = "name";
    String ARG_ERROR_CODE = "errorCode";
    String ARG_TARGET_TYPE = "targetType";
    String ARG_SRC_TYPE = "srcType";
    String ARG_VAR = "var";
    String ARG_OBJ = "obj";

    String ARG_HEADER = "header";

    String ARG_TAG_NAME = "tagName";
    String ARG_ATTR_NAME = "attrName";

    String ARG_CLASS_NAME = "className";
    String ARG_PROP_NAME = "propName";

    String ARG_EXPECTED_TYPE = "expectedType";

    String ARG_BEAN_NAME = "beanName";
    String ARG_BEAN_TYPE = "beanType";

    String ARG_ERRORS = "errors";

    String ARG_MESSAGE = "message";
    String ARG_VAR_NAME = "varName";

    String ARG_SEQ = "seq";

    ErrorCode ERR_TIMEOUT = define("nop.err.api.exceptions.timeout", "超时");

    ErrorCode ERR_BREAK = define("nop.err.api.exceptions.break", "跳出当前函数");


    ErrorCode ERR_DUPLICATE_SINGLETON_EXCEPTION =
            define("nop.err.api.exceptions.duplicate-singleton-exception-code",
                    "异常码[{errorCode}]已定义", ARG_ERROR_CODE);

    ErrorCode ERR_WRAP_EXCEPTION = define("nop.err.api.wrap", "包装异常");

    ErrorCode ERR_CHECK_NOT_EQUALS = define("nop.err.api.check.value-not-equals",
            "实际值[{actual}]不等于期待值[{expected}]", ARG_ACTUAL, ARG_EXPECTED);

    ErrorCode ERR_CHECK_INVALID_POSITION_INDEX = define("nop.err.api.check.invalid-position-index",
            "下标[{index}]超出范围，size为{size}", "index", "size");

    ErrorCode ERR_CHECK_INVALID_ARGUMENT = define("nop.err.api.check.invalid-argument", "非法参数");

    ErrorCode ERR_CHECK_OBJ_IS_FROZEN =
            define("nop.err.api.check.obj-is-frozen", "对象已经被冻结，不允许被修改");

    ErrorCode ERR_INVALID_OFFSET_LIMIT_STRING = define("nop.err.api.beans.invalid-offset-limit-string",
            "offset,limit区间字符串格式不正确:{value}", ARG_VALUE);

    ErrorCode ERR_INVALID_SOURCE_LOCATION_STRING = define("nop.err.api.beans.source-location-string",
            "SourceLocation字符串格式格式不正确:{value}", ARG_VALUE);

    ErrorCode ERR_CONVERTER_ALREADY_REGISTERED = define("nop.err.api.convert.converter-already-registered",
            "目标类型[{targetType}]的类型转换器已经存在", ARG_TARGET_TYPE);

    ErrorCode ERR_CONVERT_TO_TYPE_FAIL = define("nop.err.api.convert-to-type-fail",
            "数据类型转换错误，无法将值[{value}}转化到类型[{targetType}]", ARG_TARGET_TYPE, ARG_VALUE);


    ErrorCode ERR_INVALID_GEO_POINT_STRING =
            define("nop.err.api.convert.invalid-geo-point-string",
                    "数据点格式不正确:{value}", ARG_VALUE);

    ErrorCode ERR_INVALID_GEO_POINT_WKT_STRING =
            define("nop.err.api.convert.invalid-geo-point-wkt-string",
                    "数据点格式不正确:{value},要求格式为POINT(x y)", ARG_VALUE);

    ErrorCode ERR_MESSAGE_HEADER_INVALID_TIMEOUT_HEADER =
            define("nop.err.api.message.invalid-timeout-out-header",
                    "消息头中的的timeout字段格式不合法", ARG_VALUE);

    ErrorCode ERR_MESSAGE_HEADER_INVALID_INTEGER =
            define("nop.err.api.message.invalid-integer",
                    "消息头中的的[{header}]字段不是整数类型", ARG_HEADER);

    ErrorCode ERR_CONFIG_VAR_CONVERT_TO_TYPE_FAIL = define(
            "nop.err.api.config.var-convert-to-type-fail",
            "配置项({var})类型转换失败：值={value},目标类型={targetType}",
            ARG_VAR, ARG_VALUE, ARG_TARGET_TYPE
    );

    ErrorCode ERR_CONFIG_VALUE_TYPE_NOT_ALLOW_CHANGE =
            define("nop.err.api.config.value-type-not-allow-change",
                    "获取配置项时指定的数据类型必须与定义时一致，不能发生改变", ARG_VAR);

    ErrorCode ERR_VALIDATE_CHECK_FAIL =
            define("nop.err.api.validate.check-fail",
                    "验证失败");

    ErrorCode ERR_CONTEXT_PROVIDER_ALREADY_INITIALIZED =
            define("nop.err.api.context.provider-already-initialized",
                    "ContextProvider已经初始化，不允许再次初始化");

    ErrorCode ERR_CONTEXT_PROVIDER_NOT_INITIALIZED =
            define("nop.err.api.context.provider-not-initialized",
                    "ContextProvider尚未初始化或者已经被销毁");

    ErrorCode ERR_CONTEXT_ALREADY_CLOSED =
            define("nop.err.api.context-already-closed", "上下文对象已经被关闭，不允许再更新：seq={}", ARG_SEQ);

    ErrorCode ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED =
            define("nop.err.api.ioc.bean-container-not-initialized",
                    "Ioc容器尚未初始化");

    ErrorCode ERR_JSON_PROVIDER_NOT_INITIALIZED =
            define("nop.err.api.json.provider-not-initialized",
                    "尚未注册JSON解析器");

    ErrorCode ERR_JSON_TREE_BEAN_INVALID_TAG_NAME =
            define("nop.err.api.json.tree-bean-invalid-tag-name",
                    "TreeBean对应的tag属性[{tagName}]不是合法的XML名称", ARG_TAG_NAME);

    ErrorCode ERR_JSON_TREE_BEAN_INVALID_ATTR_NAME =
            define("nop.err.api.json.tree-bean-invalid-attr-name",
                    "TreeBean对应的属性名[{attrName}]不是合法的XML名称", ARG_ATTR_NAME);

    ErrorCode ERR_CONTEXT_TIMEOUT =
            define("nop.err.api.context-timeout",
                    "上下文执行时间已超时");

    ErrorCode ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY =
            define("nop.err.api.annotation-prop-not-allow-empty",
                    "注解[{className}]的属性[{propName}]不允许为空",
                    ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_IOC_UNKNOWN_BEAN_FOR_NAME =
            define("nop.err.api.unknown-bean-for-name",
                    "没有名称为[{beanName}]的bean", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_UNKNOWN_BEAN_FOR_TYPE =
            define("nop.err.api.unknown-bean-for-type",
                    "没有类型为[{beanType}]的bean", ARG_BEAN_TYPE);

    ErrorCode ERR_SELECTION_DUPLICATE_FIELD =
            define("nop.err.api.selection-duplicate-field",
                    "字段选择中字段名重复:{propName}", ARG_PROP_NAME);

    ErrorCode ERR_UTILS_TEMPLATE_VAR_NOT_ALLOW_NULL =
            define("nop.err.api.template-var-not-allow-null",
                    "模板字符串[{message}]中的变量[{varName}]的值不允许为空", ARG_MESSAGE, ARG_VAR_NAME);
}