/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.marker;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.commons.util.CollectionHelper;

import java.io.Serializable;
import java.util.List;

@ImmutableBean
public class MarkedString implements Serializable, IMarkedString {

    private static final long serialVersionUID = -2035853190425046022L;

    public static final MarkedString EMPTY = new MarkedString("");

    private final String text;

    private final List<Marker> markers;

    public MarkedString(String text) {
        this(text, null);
    }

    public MarkedString(String text, List<Marker> markers) {
        this.text = text;
        this.markers = CollectionHelper.freezeList(markers);
    }

    protected MarkedString(IMarkedString str) {
        this(str.getText(), str.getMarkers());
    }

    public String toString() {
        return getClass().getSimpleName() + "[text=" + getText() + ",markers=" + getMarkers() + "]";
    }

    @Override
    public String getText() {
        return text;
    }

    public String getTextSequence() {
        return text;
    }

    public int length() {
        return text.length();
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public boolean hasMarker() {
        return !markers.isEmpty();
    }

    public int getMarkerCount() {
        return markers.size();
    }

    @Override
    public List<Marker> getMarkers() {
        return markers;
    }

    public Marker getMarker(int index) {
        return markers.get(index);
    }

    /**
     * 返回调试用的显示文本，所有的marker对应部分以及marker的内容将被显示出来
     */
    public String getDumpText() {
        return renderText(this::getMarkerDumpText);
    }

    protected StringBuilder getMarkerDumpText(Marker marker) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(text, marker.getBegin(), marker.getEnd()).append(']');
        sb.append("/*");
        sb.append(marker);
        sb.append("*/");
        return sb;
    }
}