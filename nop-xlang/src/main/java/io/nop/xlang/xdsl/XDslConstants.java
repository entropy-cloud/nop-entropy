/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

public interface XDslConstants {
    String XDSL_SCHEMA_XDEF = "/nop/schema/xdef.xdef";
    String XDSL_SCHEMA_XDSL = "/nop/schema/xdsl.xdef";
    String XDSL_SCHEMA_XMETA = "/nop/schema/xmeta.xdef";
    String XDSL_SCHEMA_XPL = "/nop/schema/xpl.xdef";
    String XDSL_SCHEMA_XLIB = "/nop/schema/xlib.xdef";
    String XDSL_SCHEMA_API = "/nop/schema/api.xdef";

    String XDSL_SCHEMA_QUERY_FILTER = "/nop/schema/query/filter.xdef";

    String XDSL_SCHEMA_VALIDATOR = "/nop/schema/validator.xdef";

    String XDSL_SCHEMA_REGISTER_MODEL = "/nop/schema/register-model.xdef";

    String XDSL_SCHEMA_REGISTRY = "/nop/schema/registry.xdef";

    String XDSL_SCHEMA_SCHEMA = "/nop/schema/schema/schema.xdef";

    String XMLNS_NAME = "xmlns";
    String NS_INFO = "info";

    String NS_INFO_PREFIX = "info:";
    String NS_EXT_PREFIX = "ext:";

    String NS_XDSL_PREFIX = "xdsl:";

    String NS_I18N_PREFIX = "i18n:";
    String NS_XMLNS_PREFIX = "xmlns:";
    String NS_X_PREFIX = "x:";
    String NS_XDEF_PREFIX = "xdef:";

    String TAG_X_DIV = "x:div";

    String ATTR_FEATURE_ON = "feature:on";
    String ATTR_FEATURE_OFF = "feature:off";

    String ATTR_PROP_ID = "propId";
    String ATTR_DEPENDS = "depends";
    String ATTR_STEREO_TYPES = "stereoTypes";
    String ATTR_MAP_TO_PROP = "mapToProp";

    String ATTR_PATTERN = "pattern";
    String ATTR_MIN = "min";
    String ATTR_MAX = "max";
    String ATTR_MIN_LENGTH = "minLength";
    String ATTR_MAX_LENGTH = "maxLength";
    String ATTR_DICT = "dict";
    String ATTR_PRECISION = "precision";
    String ATTR_SCALE = "scale";
    String ATTR_EXCLUDE_MIN = "excludeMin";
    String ATTR_EXCLUDE_MAX = "excludeMax";
    String ATTR_MULTIPLE_OF = "multipleOf";

    String PROP_VALUE = "value";
    String PROP_CHILDREN = "children";
    String PROP_TYPE = "type";
    String PROP_BODY = "body";

    String EXTENDS_NONE = "none";
    String EXTENDS_SUPER = "super";

    String OUTPUT_MODE_NAME = "outputMode";

    String EXCEL_MODEL_LOADER_CLASS = "io.nop.ooxml.xlsx.imp.XlsxObjectLoader";
}
