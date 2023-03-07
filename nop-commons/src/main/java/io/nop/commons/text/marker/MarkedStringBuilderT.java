/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.marker;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.text.marker.Markers.ValueMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.commons.CommonErrors.ARG_COUNT;
import static io.nop.commons.CommonErrors.ARG_EXPECTED_COUNT;
import static io.nop.commons.CommonErrors.ARG_LENGTH;
import static io.nop.commons.CommonErrors.ARG_MARKER;
import static io.nop.commons.CommonErrors.ARG_PREV_MARKER;
import static io.nop.commons.CommonErrors.ARG_TEXT;
import static io.nop.commons.CommonErrors.ERR_TEXT_INVALID_MARKER_RANGE;
import static io.nop.commons.CommonErrors.ERR_TEXT_MAKER_COUNT_MISMATCH;
import static io.nop.commons.CommonErrors.ERR_TEXT_MARKER_POS_CONFLICT;

/**
 * 类似于String和StringBuilder的关系。MarkedString为不可变对象，而MarkedStringBuilderT为可变对象
 *
 * @param <T>
 */
public abstract class MarkedStringBuilderT<T extends MarkedStringBuilderT<T>> implements IMarkedString {
    // 这里有一个小优化，当只有一个append语句时不会生成StringBuilder对象。
    private StringBuilder buf = null;
    // 第一次append调用会缓存在这里
    private String firstText;

    private List<Marker> markers = null;

    public MarkedStringBuilderT() {
    }

    public MarkedStringBuilderT(StringBuilder buf) {
        this.buf = buf;
    }

    public MarkedStringBuilderT(String text, List<Marker> markers) {
        this.firstText = text;
        this.markers = markers;
    }

    public MarkedStringBuilderT(IMarkedString str) {
        this.firstText = str.getText();
        this.markers = str.getMarkers() == null ? null : new ArrayList<>(str.getMarkers());
    }

    public void clear() {
        this.firstText = null;
        if (this.buf != null)
            this.buf.setLength(0);
        if (this.markers != null)
            this.markers.clear();
    }

    @SuppressWarnings("unchecked")
    T castReturn() {
        return (T) this;
    }

    public T append(CharSequence text) {
        if (text == null)
            return castReturn();

        if (buf == null && firstText == null) {
            firstText = text.toString();
        } else {
            makeBuf().append(text);
        }
        return castReturn();
    }

    public T appendChars(char[] chars, int offset, int len) {
        makeBuf().append(chars, offset, len);
        return castReturn();
    }

    public T append(CharSequence chars, int start, int end) {
        makeBuf().append(chars, start, end);
        return castReturn();
    }

    StringBuilder makeBuf() {
        if (buf == null) {
            buf = new StringBuilder();
            if (firstText != null) {
                buf.append(firstText);
                firstText = null;
            }
        }
        return buf;
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    @Override
    public List<Marker> getMarkers() {
        return markers == null ? Collections.emptyList() : markers;
    }

    public Marker getMarker(int index) {
        if (markers == null || markers.isEmpty())
            return null;
        return markers.get(index);
    }

    public char charAt(int index) {
        if (buf == null) {
            Guard.notNull(firstText, "nop.err.commons.text.null-marked-string");
            return firstText.charAt(index);
        }
        return buf.charAt(index);
    }

    @Override
    public CharSequence getTextSequence() {
        if (buf == null) {
            if (firstText == null)
                return "";
            return firstText;
        }
        return buf;
    }

    public String toString() {
        return getText();
    }

    public T append(char c) {
        makeBuf().append(c);
        return castReturn();
    }

    /**
     * 从尾部删除n个字符，如果marker与被删除的位置重叠，则也会被自动删除
     *
     * @param len
     * @return
     */
    public T deleteTail(int len) {
        StringBuilder buf = makeBuf();
        buf.delete(buf.length() - len, buf.length());
        if (markers != null) {
            int length = buf.length();
            for (int i = markers.size() - 1; i >= 0; i--) {
                Marker marker = markers.get(i);
                if (marker.getEnd() <= length)
                    break;
                markers.remove(i);
            }
        }
        return castReturn();
    }

    public Marker getLastMarker() {
        if (markers == null || markers.isEmpty())
            return null;
        return markers.get(markers.size() - 1);
    }

    void checkAddMarker(Marker marker) {
        Marker lastMarker = this.getLastMarker();
        if (lastMarker != null) {
            if (lastMarker.intersect(marker) || lastMarker.getBegin() >= marker.getBegin())
                throw new NopException(ERR_TEXT_MARKER_POS_CONFLICT).param(ARG_PREV_MARKER, lastMarker)
                        .param(ARG_MARKER, marker);
        }

        if (marker.getEnd() > length())
            throw new NopException(ERR_TEXT_INVALID_MARKER_RANGE).param(ARG_MARKER, marker).param(ARG_LENGTH, length());
    }

    public T appendMarker(Marker marker) {
        checkAddMarker(marker);
        addMarker(marker);
        return castReturn();
    }

    public T appendMarkers(List<? extends Marker> markers) {
        for (Marker marker : markers) {
            addMarker(marker);
        }
        return castReturn();
    }

    public T insertMarker(Marker marker) {
        if (markers == null) {
            addMarker(marker);
            return castReturn();
        }

        int idx = Collections.binarySearch(this.markers, marker);
        if (idx >= 0)
            throw new NopException(ERR_TEXT_MARKER_POS_CONFLICT).param(ARG_MARKER, marker).param(ARG_PREV_MARKER,
                    markers.get(idx));

        if (marker.getEnd() > length())
            throw new NopException(ERR_TEXT_INVALID_MARKER_RANGE).param(ARG_MARKER, marker).param(ARG_LENGTH, length());

        int pos = -idx - 1;
        if (pos > 0) {
            Marker prev = markers.get(pos - 1);
            if (prev.intersect(marker))
                throw new NopException(ERR_TEXT_MARKER_POS_CONFLICT).param(ARG_MARKER, marker).param(ARG_PREV_MARKER,
                        prev);
        }

        if (pos < markers.size()) {
            Marker next = markers.get(pos);
            if (next.intersect(marker))
                throw new NopException(ERR_TEXT_MARKER_POS_CONFLICT).param(ARG_MARKER, marker).param(ARG_PREV_MARKER,
                        next);
        }
        markers.add(pos, marker);

        return castReturn();
    }

    protected void addMarker(Marker marker) {
        if (markers == null)
            markers = new ArrayList<>();
        markers.add(marker);
    }

    /**
     * 追加一段MarkedString，合并文本，并将markers逐个偏移offset之后加入到当前marker集合
     *
     * @param str
     * @return
     */
    public T append(IMarkedString str) {
        int offset = length();
        append(str.getTextSequence());
        List<Marker> markers = str.getMarkers();
        if (markers != null) {
            for (int i = 0, n = markers.size(); i < n; i++) {
                Marker marker = markers.get(i);
                marker = marker.offset(offset);
                // 假设marker已经排好序，无需再检查是否与其他marker重叠
                this.addMarker(marker);
            }
        }
        return castReturn();
    }

    public T append(Object o) {
        if (o == null)
            return castReturn();

        if (o instanceof IMarkedString) {
            return append((IMarkedString) o);
        } else if (o instanceof CharSequence) {
            return append((CharSequence) o);
        } else {
            return append(o.toString());
        }
    }

    public T appendWithMarker(String text, Marker marker) {
        this.append(text).addMarker(marker);
        return castReturn();
    }

    public T appendWithValueMarker(String text, String name, Object value, boolean masked) {
        StringBuilder buf = this.makeBuf();
        int start = buf.length();
        buf.append(text);
        int end = buf.length();
        return appendMarker(new ValueMarker(start, end, name, value, masked));
    }

    public T markWithName(String text, String name) {
        StringBuilder buf = this.makeBuf();
        int start = buf.length();
        buf.append(text);
        int end = buf.length();
        return appendMarker(new Markers.NameMarker(start, end, name));
    }

    public T markWithProvider(String text, String name, Supplier<?> provider) {
        StringBuilder buf = this.makeBuf();
        int start = buf.length();
        buf.append(text);
        int end = buf.length();
        return appendMarker(new Markers.ProviderMarker(start, end, name, provider));
    }

    public T markValue(String text, Object value, boolean masked) {
        return appendWithValueMarker(text, null, value, masked);
    }

    public int length() {
        if (buf == null) {
            if (firstText == null)
                return 0;
            return firstText.length();
        }
        return buf.length();
    }

    /**
     * 将ValueMarker的值替换为新的值
     *
     * @param values 列表的长度必须与ValueMarker的个数一致
     * @return 当前的MarkedStringBuilder对象，用于链式调用
     */
    public T changeMarkerValues(List<Object> values) {
        List<Marker> markers = this.getMarkers();
        int k = 0;
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker marker = markers.get(i);
            if (marker instanceof ValueMarker) {
                if (k >= values.size()) {
                    k++;
                    break;
                }

                Object value = values.get(k);
                k++;
                marker = ((ValueMarker) marker).changeValue(value);
                markers.set(i, marker);
            }
        }
        if (k != values.size())
            throw new NopException(ERR_TEXT_MAKER_COUNT_MISMATCH).param(ARG_TEXT, getText())
                    .param(ARG_COUNT, values.size()).param(ARG_EXPECTED_COUNT, getValueMarkerCount());
        return castReturn();
    }

    public T changeMarker(Function<Marker, Marker> transformer) {
        List<Marker> markers = this.getMarkers();
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker marker = markers.get(i);
            marker = transformer.apply(marker);
            if (marker == null) {
                markers.remove(i);
                i--;
                n--;
            } else {
                markers.set(i, marker);
            }
        }
        return castReturn();
    }

    public T appendRange(IMarkedString str, int startPos, int endPos) {
        CharSequence seq = str.subSequence(startPos, endPos);
        int pos = length();
        this.append(seq);

        for (Marker marker : str.getMarkers()) {
            if (marker.within(startPos, endPos)) {
                marker = marker.offset(-startPos + pos);
                addMarker(marker);
            }
        }
        return castReturn();
    }

    /**
     * 将marker转换为MarkedString对象，后续的marker位置将被自动调整。
     *
     * @param transformer 入口参数的marker总是保持原始位置。
     * @return 当前对象
     */
    public T transformMarker(Function<Marker, IMarkedString> transformer) {
        List<Marker> markers = getMarkers();
        int offset = 0;
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker marker = markers.get(i);
            IMarkedString str = transformer.apply(marker);
            if (str != null) {
                markers.remove(i);
                i--;
                n--;
                String text = str.getText();
                int begin = marker.getBegin() + offset;
                StringBuilder buf = makeBuf();
                buf.replace(begin, marker.getEnd() + offset, text);
                offset += text.length() - marker.length();
                for (Marker strMarker : str.getMarkers()) {
                    strMarker = strMarker.offset(begin);
                    markers.add(i, strMarker);
                    i++;
                    n++;
                }
            } else {
                marker = marker.offset(offset);
                markers.set(i, marker);
            }
        }
        return castReturn();
    }

    /**
     * 追加一段MarkedString，并把其中的Marker替换为transformer返回的对象
     *
     * @param source      追加的MarkedString
     * @param transformer 如果返回null, 则表示保持原先的marker
     */
    public T appendWithTransform(IMarkedString source, Function<Marker, IMarkedString> transformer) {

        // String text = source.getText();
        List<Marker> markers = source.getMarkers();
        if (markers.isEmpty()) {
            append(source.getTextSequence());
            return castReturn();
        }

        CharSequence text = source.getTextSequence();
        int prevTransformedMarkerIndex = -1;

        int offset = 0;
        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker marker = markers.get(i);
            IMarkedString str = transformer.apply(marker);
            if (str == null) {
                // 保持原有marker
                continue;
            }

            int lastPos = prevTransformedMarkerIndex < 0 ? 0 : markers.get(prevTransformedMarkerIndex).getEnd();
            append(text, lastPos, marker.getBegin());
            for (int k = prevTransformedMarkerIndex + 1; k < i; k++) {
                Marker prevMarker = markers.get(k);
                if (offset != 0)
                    prevMarker = prevMarker.offset(offset);
                addMarker(prevMarker);
            }
            offset += str.length() - marker.length();

            append(str);
            prevTransformedMarkerIndex = i;
        }

        int lastPos = prevTransformedMarkerIndex < 0 ? 0 : markers.get(prevTransformedMarkerIndex).getEnd();
        append(text, lastPos, text.length());
        for (int k = prevTransformedMarkerIndex + 1; k < markers.size(); k++) {
            Marker prevMarker = markers.get(k);
            if (offset != 0)
                prevMarker = prevMarker.offset(offset);
            addMarker(prevMarker);
        }
        return castReturn();
    }
}