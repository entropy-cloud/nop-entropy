/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.util.ICloneable;
import io.nop.commons.util.StringHelper;

/**
 * 单元格的只读视图。
 * <p>
 * 对于单元格合并的情况，rowSpan或者colSpan大于1，最左上角的单元格为realCell。同时会产生一系列占位用的Proxy单元格。
 */
public interface ICellView extends ICloneable {
    /**
     * 单元格合并时，合并后被屏蔽的单元格位置用代理单元格来占位
     */
    default boolean isProxyCell() {
        return false;
    }

    /**
     * 每个单元格都具有唯一id。 ProxyCell返回的是realCell.getId()
     */
    String getId();

    String getStyleId();

    String getComment();

    ICellView cloneInstance();

    @JsonIgnore
    IRowView getRow();

    @JsonIgnore
    /**
     * 如果是占位用的代理单元格，这里返回对应的真实单元格。如果单元格没有合并，则返回this
     */
    ICellView getRealCell();

    /**
     * 如果单元格发生合并，则被合并的单元格会用代理单元格来占位。rowOffset指定了代理单元格相对于左上角的偏移量。 例如 B2和B3单元格合并，则B2对应realCell，rowOffset=0,colOffset=0,
     * 同时在B3位置会用代理单元格占位， 它的realCell对应B2，而rowOffset=1,colOffset=0
     */
    default int getRowOffset() {
        return 0;
    }

    default int getColOffset() {
        return 0;
    }

    @JsonIgnore
    default int getColSpan() {
        return getMergeAcross() + 1;
    }

    @JsonIgnore
    default int getRowSpan() {
        return getMergeDown() + 1;
    }

    /**
     * 向下合并的行数，缺省为0。 对于ProxyCell, 这里总返回0
     */
    int getMergeDown();

    /**
     * 向右合并的列数，缺省为0 对于ProxyCell， 这里总返回0
     */
    int getMergeAcross();

    /**
     * 单元格的值。对于Proxy单元格，这里总返回null
     */
    Object getValue();

    default Object getFormattedValue() {
        return getValue();
    }

    default Object getExportValue(){
        return getValue();
    }

    @JsonIgnore
    /**
     * 单元格的显示文本. 对于ProxyCell, 这里总返回null
     */
    default String getText() {
        Object value = getFormattedValue();
        if (value instanceof Iterable)
            return StringHelper.join((Iterable<?>) value, ",");
        return StringHelper.toString(value, null);
    }

    default String getLinkUrl(){
        return null;
    }

    @JsonIgnore
    /**
     * 是否空白单元格. 对于ProxyCell, 这里总返回true
     */
    default boolean isBlankCell() {
        return StringHelper.isBlank(getText());
    }
}