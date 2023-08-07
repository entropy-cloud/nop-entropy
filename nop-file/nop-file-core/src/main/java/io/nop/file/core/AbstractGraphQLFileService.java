package io.nop.file.core;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class AbstractGraphQLFileService {
    private IGraphQLEngine graphQLEngine;

    static final Set<String> IGNORE_HEADERS = CollectionHelper.buildImmutableSet("connection",
            "accept", "accept-encoding", "content-length");

    protected boolean shouldIgnoreHeader(String name) {
        return IGNORE_HEADERS.contains(name);
    }

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    public CompletionStage<ApiResponse<?>> uploadAsync(ApiRequest<UploadRequestBean> request) {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                FileConstants.OPERATION_FILE_STORE_UPLOAD, request);
        return graphQLEngine.executeRpcAsync(ctx);
    }

    public CompletionStage<ApiResponse<WebContentBean>> downloadAsync(ApiRequest<DownloadRequestBean> request) {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                FileConstants.OPERATION_FILE_STORE_DOWNLOAD, request);
        return graphQLEngine.executeRpcAsync(ctx).thenApply(res -> {
            if (res.isOk()) {
                WebContentBean content = BeanTool.castBeanToType(res.getData(), WebContentBean.class);
                ((ApiResponse) res).setData(content);
            }
            return (ApiResponse<WebContentBean>) res;
        });
    }
}