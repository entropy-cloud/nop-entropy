/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.marker;

import java.util.List;

public class MarkedStringBuilder extends MarkedStringBuilderT<MarkedStringBuilder> {
    public MarkedStringBuilder() {
    }

    public MarkedStringBuilder(StringBuilder sb) {
        super(sb);
    }

    public MarkedStringBuilder(String text, List<Marker> markers) {
        super(text, markers);
    }

    public MarkedStringBuilder(IMarkedString str) {
        super(str);
    }

    public MarkedString end() {
        return new MarkedString(this);
    }
}
