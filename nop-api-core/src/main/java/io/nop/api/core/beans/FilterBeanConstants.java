/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

public interface FilterBeanConstants {

    String FILTER_ATTR_NAME = "name";
    String FILTER_ATTR_VALUE = "value";

    String FILTER_ATTR_VALUE_NAME = "valueName";

    //    String FILTER_ATTR_RIGHT = "right";
    String FILTER_ATTR_MIN = "min";
    String FILTER_ATTR_MAX = "max";
    String FILTER_ATTR_EXCLUDE_MIN = "excludeMin";
    String FILTER_ATTR_EXCLUDE_MAX = "excludeMax";

    String FILTER_ATTR_OWNER = "owner";
    String FILTER_ATTR_VALUE_OWNER = "valueOwner";
//    String FILTER_ATTR_LEFT_OWNER = "leftOwner";
//    String FILTER_ATTR_RIGHT_OWNER = "rightOwner";

    String FILTER_ATTR_MIN_NAME = "minName";
    String FILTER_ATTR_MAX_NAME = "maxName";

    String FILTER_OP_BETWEEN = "between";
    String FILTER_OP_NOT_BETWEEN = "notBetween";

    String FILTER_OP_DATE_BETWEEN = "dateBetween";
    String FILTER_OP_DATETIME_BETWEEN = "dateTimeBetween";
    String FILTER_OP_LENGTH_BETWEEN = "lengthBetween";

    /**
     * 字符串长度为指定值，或者在指定区间内
     */
    String FILTER_OP_LENGTH = "length";

    String FILTER_OP_STARTS_WITH = "startsWith";
    String FILTER_OP_ENDS_WITH = "endsWith";
    /**
     * 包含子字符串
     */
    String FILTER_OP_CONTAINS = "contains";

    String FILTER_OP_NOT_CONTAINS = "notContains";

    /**
     * 包含字符串，大小写不敏感
     */
    String FILTER_OP_ICONTAINS = "icontains";

    /**
     * SQL语言中的like语法
     */
    String FILTER_OP_LIKE = "like";

    /**
     * 是否包含匹配指定模式的子字符串
     */
    String FILTER_OP_REGEX = "regex";

    String FILTER_OP_IN = "in";
    String FILTER_OP_NOT_IN = "notIn";

    String FILTER_OP_EQ = "eq";
    String FILTER_OP_GT = "gt";
    String FILTER_OP_GE = "ge";
    String FILTER_OP_LT = "lt";
    String FILTER_OP_LE = "le";
    String FILTER_OP_NE = "ne";

//    String FILTER_OP_REL_PREFIX = "rel-";

//    String FILTER_OP_REL_EQ = FILTER_OP_REL_PREFIX + FILTER_OP_EQ;
//    String FILTER_OP_REL_NE = FILTER_OP_REL_PREFIX + FILTER_OP_NE;
//    String FILTER_OP_REL_GT = FILTER_OP_REL_PREFIX + FILTER_OP_GT;
//    String FILTER_OP_REL_GE = FILTER_OP_REL_PREFIX + FILTER_OP_GE;
//    String FILTER_OP_REL_LT = FILTER_OP_REL_PREFIX + FILTER_OP_LT;
//    String FILTER_OP_REL_LE = FILTER_OP_REL_PREFIX + FILTER_OP_LE;

    String FILTER_OP_AND = "and";
    String FILTER_OP_OR = "or";
    String FILTER_OP_NOT = "not";

    String FILTER_OP_ALWAYS_TRUE = "alwaysTrue";
    String FILTER_OP_ALWAYS_FALSE = "alwaysFalse";
    String FILTER_OP_IS_NULL = "isNull";
    String FILTER_OP_IS_EMPTY = "isEmpty";
    String FILTER_OP_IS_BLANK = "isBlank";

    String FILTER_OP_NOT_NULL = "notNull";
    String FILTER_OP_NOT_EMPTY = "notEmpty";
    String FILTER_OP_NOT_BLANK = "notBlank";

    String FILTER_OP_IS_NUMBER = "isNumber";
    String FILTER_OP_NOT_NUMBER = "notNumber";

    String FILTER_OP_IS_TRUE = "isTrue";
    String FILTER_OP_IS_FALSE = "isFalse";
    String FILTER_OP_NOT_TRUE = "notTrue";
    String FILTER_OP_NOT_FALSE = "notFalse";

    String FILTER_OP_SQL = "sql";

    String FILTER_OP_EXPR = "expr";

    String DUMMY_TAG_NAME = "_";
    String FILTER_TAG_NAME = "filter";
}