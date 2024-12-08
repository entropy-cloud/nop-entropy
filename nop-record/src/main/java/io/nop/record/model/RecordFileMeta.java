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
    private RecordObjectMeta resolvedHeaderType;
    private RecordObjectMeta resolvedBodyType;
    private RecordObjectMeta resolvedTrailerType;

    public RecordFileMeta() {

    }

    public boolean useAggregate() {
        return hasAggregates() || getPagination() != null && getPagination().hasAggregates();
    }

    public RecordObjectMeta getResolvedHeaderType() {
        return resolvedHeaderType;
    }

    public RecordObjectMeta getResolvedBodyType() {
        return resolvedBodyType;
    }

    public RecordObjectMeta getResolvedTrailerType() {
        return resolvedTrailerType;
    }

    @Override
    public void init() {
        super.init();

        if (getHeader() != null) {
            if (getHeader().getTypeRef() != null) {
                resolvedHeaderType = resolveType(getHeader().getTypeRef());
            } else {
                resolvedHeaderType = getHeader();
                getHeader().init(this);
            }

            if (!resolvedHeaderType.hasFieldsOrTemplate())
                resolvedHeaderType = null;
        }

        if (getBody() != null) {
            if (getBody().getTypeRef() != null) {
                resolvedBodyType = resolveType(getBody().getTypeRef());
            } else {
                resolvedBodyType = getBody();
                getBody().init(this);
            }
        }

        if (getTrailer() != null) {
            if (getTrailer().getTypeRef() != null) {
                resolvedTrailerType = resolveType(getTrailer().getTypeRef());
            } else {
                resolvedTrailerType = getTrailer();
                getTrailer().init(this);
            }

            if (!resolvedTrailerType.hasFieldsOrTemplate())
                resolvedTrailerType = null;
        }

        if (getPagination() != null)
            getPagination().init(this);
    }
}
