/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

public interface IListSchema extends ISchemaNode {

    Integer getMinItems();

    Integer getMaxItems();

    default boolean isKeyedListType() {
        return isListSchema() && getKeyProp() != null;
    }

    /**
     * 对于集合属性，指定集合中元素的唯一键属性，例如id,name等
     */
    String getKeyAttr();

    String getKeyProp();

    /**
     * 如果指定了排序字段，则列表按照排序字段排序
     */
    String getOrderAttr();

    String getOrderProp();

    /**
     * 如果是数组类型，则对应数组元素的schema
     */
    ISchema getItemSchema();
}