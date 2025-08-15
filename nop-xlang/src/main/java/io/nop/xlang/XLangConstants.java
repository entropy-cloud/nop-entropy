/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang;

import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.CollectionHelper;
import io.nop.xlang.expr.ExprConstants;
import io.nop.xlang.filter.BizFilterConstants;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xmeta.utils.ObjMetaPropConstants;
import io.nop.xlang.xpl.XplConstants;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.Set;

public interface XLangConstants extends ExprConstants,
        XplConstants, XDslConstants, BizFilterConstants, ObjMetaPropConstants {
    String XPATH_OPERATOR_VALUE = "$value";
    String XPATH_OPERATOR_TAG = "$tag";
    String XPATH_OPERATOR_XML = "$xml";
    String XPATH_OPERATOR_INNER_XML = "$innerXml";
    String XPATH_OPERATOR_TEXT = "$text";
    String XPATH_OPERATOR_HTML = "$html";
    String XPATH_OPERATOR_INNER_HTML = "$innerHtml";
    String XPATH_OPERATOR_IDENTITY = "$node";

    String XPATH_VAR_THIS_NODE = "thisNode";
    String XPATH_VAR_ROOT = "root";

    String GEN_VAR_PREFIX = "__v_";
    String GEN_FUNC_PREFIX = "__fn_";

    String DECORATOR_MACRO = "Macro";
    String DECORATOR_DEPRECATED = "Deprecated";
    String DECORATOR_DETERMINISTIC = "deterministic";

    String SOURCE_TYPE_LOCAL_BLOCK = "localBlock";
    String SOURCE_TYPE_SCRIPT = "localBlock";

    String VAR_JSON = "json";

    Set<Class<?>> IMMUTABLE_PRIMITIVE_TYPES = CollectionHelper.buildImmutableSet(String.class, Integer.class,
            Double.class, Float.class, Character.class, Byte.class, Boolean.class, Long.class, BigInteger.class,
            BigDecimal.class, LocalDate.class, LocalDateTime.class, Year.class, YearMonth.class, Duration.class,
            ByteString.class);

    String MODEL_TYPE_XPL = "xpl";
    String MODEL_TYPE_XLIB = "xlib";

    String MODEL_TYPE_XTASK = "xtask";

    String FILE_TYPE_XLIB = "xlib";
    String FILE_TYPE_XPL = "xpl";
    String FILE_TYPE_XRUN = "xrun";
    String FILE_TYPE_XGEN = "xgen";


    String MODEL_TYPE_XMETA = "xmeta";
    String MODEL_TYPE_XDEF = "xdef";

    String MODEL_TYPE_XJAVA = "xjava";

    String LOCAL_NAMESPACE = "local";

    String FUNC_PARAM_SELF = "self";
    String FUNC_PARAM_PROP_NAME = "propName";
    String FUNC_PARAM_VALUE = "value";

    String SLOT_DEFAULT = "default";
    String SLOT_VAR_PREFIX = "slot_";

    String VAR_VALIDATION_CTX = "validationCtx";
}