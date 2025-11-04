/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.marker;

import java.util.List;
import java.util.function.Function;

public class MarkStringHelper {
    /**
     * 将marker部分标记的文本替换为transformer返回的文本。
     *
     * @param transformer 根据marker生成替换文本。原文本中被marker标记的部分将被此函数的返回值替换
     * @return 对文本进行替换后生成的新文本。
     */
    public static String renderText(IMarkedString ms, Function<Marker, Object> transformer) {
        String text = ms.getText();
        List<Marker> markers = ms.getMarkers();

        if (markers.isEmpty())
            return text;

        StringBuilder sb = new StringBuilder(text.length() + 32);
        Marker lastMarker = null;

        for (int i = 0, n = markers.size(); i < n; i++) {
            Marker marker = markers.get(i);
            Object str = transformer.apply(marker);
            if (str == null) {
                continue;
            }
            if (lastMarker == null) {
                // first transformed marker
                sb.append(text, 0, marker.getBegin());
            } else {
                sb.append(text, lastMarker.getEnd(), marker.getBegin());
            }
            sb.append(str);
            lastMarker = marker;
        }

        if (lastMarker == null) {
            // first transformed marker
            sb.append(text);
        } else {
            sb.append(text, lastMarker.getEnd(), text.length());
        }

        return sb.toString();
    }
}
