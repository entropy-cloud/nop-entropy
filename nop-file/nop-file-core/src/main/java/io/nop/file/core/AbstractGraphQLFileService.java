/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.core;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class AbstractGraphQLFileService {
    static final Set<String> IGNORE_HEADERS = CollectionHelper.buildImmutableSet("connection",
            "accept", "accept-encoding", "content-length");

    protected boolean shouldIgnoreHeader(String name) {
        return IGNORE_HEADERS.contains(name);
    }

    protected IGraphQLEngine getGraphQLEngine() {
        return BeanContainer.getBeanByType(IGraphQLEngine.class);
    }

    public CompletionStage<ApiResponse<?>> uploadAsync(ApiRequest<UploadRequestBean> request) {
        IGraphQLEngine graphQLEngine = getGraphQLEngine();

        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
            GraphQLOperationType.mutation,
            FileConstants.OPERATION_FILE_STORE_UPLOAD, request
        );

        return graphQLEngine.executeRpcAsync(ctx);
    }

    public CompletionStage<ApiResponse<WebContentBean>> downloadAsync(ApiRequest<DownloadRequestBean> request) {
        IGraphQLEngine graphQLEngine = getGraphQLEngine();
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
            GraphQLOperationType.query,
            FileConstants.OPERATION_FILE_STORE_DOWNLOAD, request
        );

        return graphQLEngine.executeRpcAsync(ctx).thenApply(res -> {
            if (res.isOk()) {
                WebContentBean content = BeanTool.castBeanToType(res.getData(), WebContentBean.class);
                ((ApiResponse) res).setData(content);
            }
            return (ApiResponse<WebContentBean>) res;
        });
    }

    protected <T> ApiRequest<T> buildApiRequest(T data, Consumer<BiConsumer<String, Object>> headersConsumer) {
        ApiRequest<T> ret = new ApiRequest<>();
        ret.setData(data);

        headersConsumer.accept((name, value) -> {
            name = name.toLowerCase(Locale.ENGLISH);

            if (!shouldIgnoreHeader(name)) {
                ret.setHeader(name, value);
            }
        });

        return ret;
    }

    protected UploadRequestBean buildUploadRequestBean(
            InputStream is, String fileName, long fileSize, String contentType, Function<String, String> paramGetter
    ) {
        String mimeType = MediaTypeHelper.getMimeType(contentType, StringHelper.fileExt(fileName));

        UploadRequestBean request = new UploadRequestBean(is, fileName, fileSize, mimeType);

        request.setBizObjName(paramGetter.apply(FileConstants.PARAM_BIZ_OBJ_NAME));
        request.setFieldName(paramGetter.apply(FileConstants.PARAM_FIELD_NAME));

        return request;
    }

    protected DownloadRequestBean buildDownloadRequestBean(String fileId, String contentType) {
        DownloadRequestBean request = new DownloadRequestBean();

        request.setFileId(fileId);
        request.setContentType(contentType);

        return request;
    }
}
