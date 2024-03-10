/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.core.type.IGenericType;

/**
 * 简单值类型对应的schema，它不会按照对象结构分解为多个prop，也不会按照列表结构分解为多个item
 */
public interface ISimpleSchema extends ISchemaNode {
    /**
     * 泛型类型声明
     */
    IGenericType getType();

    default boolean isPrimitive() {
        return getType().isPrimitive();
    }

    /**
     * 自定义的类型域名称，例如role, dept等，可以由程序自定义并负责解释。
     */
    String getDomain();

    /**
     * 对应数据字典的名称。可以是常量类的类名，或者dict定义文件的路径
     */
    String getDict();

    Integer getPrecision();

    /**
     * 小数位数
     */
    Integer getScale();

    /**
     * 正则字符串模式
     */
    String getPattern();

    boolean matchPattern(String str);

    Double getMin();

    Double getMax();

    Boolean getExcludeMin();

    Boolean getExcludeMax();

    Integer getMinLength();

    Integer getMaxLength();

    Integer getMultipleOf();
}
