/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 根据ApiResponse响应消息重建出来的异常
 */
public class NopRebuildException extends NopException {
    static final Logger LOG = LoggerFactory.getLogger(NopRebuildException.class);

    private static final long serialVersionUID = 7362504382995351115L;

    /**
     * forPublic表示可以直接返给客户的错误提示信息。如果没有找到对应的错误码映射，也不需要显示为内部错误。
     */
    private boolean forPublic;

    private NopRebuildException(String errorCode, Throwable cause, boolean enableSuppression,
                                boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
        LOG.info("nop.api.rebuild-exception:errorCode={},seq={}", errorCode, this.getSeq());
    }

    public NopRebuildException forPublic(boolean b) {
        this.forPublic = b;
        return this;
    }

    public boolean isForPublic() {
        return forPublic;
    }

    public static NopException rebuild(ErrorBean error) {
        String errorCode = error.getErrorCode();
        String description = error.getDescription();
        Map<String, Object> params = error.getParams();

        SourceLocation loc = null;
        if (error.getSourceLocation() != null) {
            try {
                loc = SourceLocation.parse(error.getSourceLocation());
            } catch (Exception e) {
                LOG.error("nop.err.invalid-source-loc:{}", error.getSourceLocation(), e);
            }
        }

        return new NopRebuildException(errorCode, null, false, true).forPublic(error.isForPublic())
                .loc(loc)
                .bizFatal(error.isBizFatal())
                .description(description).params(params);
    }

    public static NopException rebuild(ApiResponse error) {
        Guard.checkArgument(!error.isOk(), "response is not error message");
        Guard.checkArgument(error.getCode() != null, "response.error is null");

        String errorCode = error.getCode();
        String description = error.getMsg();
        Map<String, Object> params = (Map<String, Object>) error.getData();

//        SourceLocation loc = null;
//        if (error.getError().getSourceLocation() != null) {
//            try {
//                loc = SourceLocation.parse(error.getError().getSourceLocation());
//            } catch (Exception e) {
//            }
//        }

        return new NopRebuildException(errorCode, null, false, true).forPublic(true)
                .bizFatal(Boolean.TRUE.equals(error.getBizFatal()))
                .description(description).params(params);
    }
}