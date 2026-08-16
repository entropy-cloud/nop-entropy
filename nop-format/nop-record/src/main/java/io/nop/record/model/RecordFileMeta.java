/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.record.model._gen._RecordFileMeta;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.ITextDataReader;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static io.nop.record.RecordErrors.ARG_ATTRIBUTE_NAME;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED;

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

        if (getBody() != null)
            fixBody(getBody());

        checkUnsupportedAttributes();
    }

    /**
     * terminator/includeTerminator/tillEnd 声明但无引擎实现：模型配置即快速失败（No Silent No-Op）
     */
    void checkUnsupportedAttributes() {
        Set<String> seen = new HashSet<>();
        checkTypeAttributes(resolvedHeaderType, seen);
        checkTypeAttributes(resolvedBodyType, seen);
        checkTypeAttributes(resolvedTrailerType, seen);
    }

    void checkTypeAttributes(RecordObjectMeta type, Set<String> seen) {
        if (type == null || type.getName() == null || !seen.add(type.getName()))
            return;

        for (RecordFieldMeta field : type.getFields()) {
            if (field.getTerminator() != null)
                throw new NopException(ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED)
                        .source(field)
                        .param(ARG_ATTRIBUTE_NAME, "terminator")
                        .param(ARG_FIELD_NAME, field.getName());
            if (field.isIncludeTerminator())
                throw new NopException(ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED)
                        .source(field)
                        .param(ARG_ATTRIBUTE_NAME, "includeTerminator")
                        .param(ARG_FIELD_NAME, field.getName());
            if (field.isTillEnd())
                throw new NopException(ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED)
                        .source(field)
                        .param(ARG_ATTRIBUTE_NAME, "tillEnd")
                        .param(ARG_FIELD_NAME, field.getName());

            if (field.getTypeRef() != null) {
                checkTypeAttributes(resolveType(field.getTypeRef()), seen);
            }
        }
    }

    void fixBody(RecordFileBodyMeta body) {
        if (resolvedTrailerType != null) {
            ByteString prefix = resolvedTrailerType.getPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                if (body.getRepeatCountExpr() == null && body.getRepeatUntil() == null) {
                    if (isBinary()) {
                        body.setRepeatUntil((thisObj, args, scope) -> {
                            return binaryMatchTrailer((IBinaryDataReader) args[0], prefix.toByteArray());
                        });
                    } else {
                        String str = prefix.buildString(resolvedTrailerType.getCharsetObj());
                        body.setRepeatUntil((thisObj, args, scope) -> {
                            return textMatchTrailer((ITextDataReader) args[0], str);
                        });
                    }
                    body.setRepeatKind(FieldRepeatKind.until);
                }
            }
        }
    }

    boolean textMatchTrailer(ITextDataReader in, String data) {
        try {
            return in.startsWith(data);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    boolean binaryMatchTrailer(IBinaryDataReader in, byte[] data) {
        try {
            return in.startsWithBytes(data);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}
