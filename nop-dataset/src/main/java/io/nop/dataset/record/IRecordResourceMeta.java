/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public interface IRecordResourceMeta extends Serializable {
    List<String> getHeaders();

    Map<String, Object> getHeaderMeta();

    default Object getHeaderMeta(String name) {
        Map<String, Object> attrs = getHeaderMeta();
        if (attrs == null)
            return null;
        return attrs.get(name);
    }

    default Map<String, Object> getTrailerMeta() {
        return null;
    }
}
