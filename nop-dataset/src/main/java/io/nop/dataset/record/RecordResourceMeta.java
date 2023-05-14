/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;

@DataBean
public class RecordResourceMeta implements IRecordResourceMeta {
    private List<String> headers;

    private Map<String, Object> attributes;

    public RecordResourceMeta() {
    }

    public RecordResourceMeta(List<String> headers, Map<String, Object> attributes) {
        setHeaders(headers);
        setAttributes(attributes);
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getHeaderMeta() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}