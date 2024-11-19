/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.record.model._gen._RecordFileMeta;

public class RecordFileMeta extends _RecordFileMeta {
    public RecordFileMeta() {

    }

    public boolean useAggregate() {
        return hasAggregates() || getPagination() != null && getPagination().hasAggregates();
    }

    @Override
    public void init() {
        super.init();

        if(getHeader() != null)
            getHeader().init(this);

        if(getBody() != null)
            getBody().init(this);

        if(getTrailer() != null)
            getTrailer().init(this);

        if(getPagination() != null)
            getPagination().init(this);
    }
}
