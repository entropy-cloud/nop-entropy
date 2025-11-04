/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.marker;

import io.nop.commons.text.marker.Markers.ValueMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 描述允许附加各类标记对象的一段文本
 */
public interface IMarkedString {

    /**
     * 文本对象
     *
     * @return
     */
    default String getText() {
        return getTextSequence().toString();
    }

    /**
     * 内部文本对象。内部实现不一定是String，所以允许返回CharSequence。
     *
     * @return
     */
    CharSequence getTextSequence();

    default CharSequence subSequence(int startPos, int endPos) {
        return getTextSequence().subSequence(startPos, endPos);
    }

    /**
     * 文本长度
     */
    int length();

    /**
     * 文本是否为空
     */
    boolean isEmpty();

    /**
     * 文本关联的标记对象。所有标记对象的位置是不重叠的，并且按照起始位置排列
     *
     * @return
     */
    List<Marker> getMarkers();

    /**
     * 标记对象中的ValueMarker的个数
     *
     * @return
     */
    default int getValueMarkerCount() {
        int n = 0;
        for (Marker marker : getMarkers()) {
            if (marker instanceof ValueMarker)
                n++;
        }
        return n;
    }

    default List<Object> getMarkerValues() {
        List<Marker> markers = getMarkers();
        if (markers.isEmpty())
            return Collections.emptyList();
        List<Object> ret = new ArrayList<>(markers.size());
        for (Marker marker : markers) {
            if (marker instanceof ValueMarker) {
                ret.add(((ValueMarker) marker).getValue());
            } else if (marker instanceof Markers.ProviderMarker) {
                ret.add(((Markers.ProviderMarker) marker).getValue());
            }
        }
        return ret;
    }

    /**
     * 将marker部分标记的文本替换为transformer返回的文本。
     *
     * @param transformer 根据marker生成替换文本。原文本中被marker标记的部分将被此函数的返回值替换
     * @return 对文本进行替换后生成的新文本。
     */
    default String renderText(Function<Marker, Object> transformer) {
        return MarkStringHelper.renderText(this, transformer);
    }
}