/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.server;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.grpc.status.GrpcStatusMapping;
import io.nop.rpc.grpc.utils.GrpcHelper;

import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class GraphQLServerCallHandler<S, R> implements ServerCallHandler<S, R> {
    @VisibleForTesting
    static final String TOO_MANY_REQUESTS = "Too many requests";
    @VisibleForTesting
    static final String MISSING_REQUEST = "Half-closed without a request";

    private final IGraphQLEngine graphQLEngine;

    private final GraphQLFieldDefinition fieldDefinition;

    private final GrpcStatusMapping statusMapping;

    public GraphQLServerCallHandler(IGraphQLEngine graphQLEngine, GrpcStatusMapping statusMapping,
                                    GraphQLFieldDefinition fieldDefinition) {
        this.graphQLEngine = graphQLEngine;
        this.statusMapping = statusMapping;
        this.fieldDefinition = fieldDefinition;
    }

    @Override
    public ServerCall.Listener<S> startCall(ServerCall<S, R> call, Metadata headers) {
        Map<String, Object> reqHeaders = GrpcHelper.parseHeaders(headers);
        FieldSelectionBean selection = GrpcHelper.getSelection(reqHeaders);

        ServerCallStreamObserverImpl<S, R> responseObserver =
                new ServerCallStreamObserverImpl<>(call, false, statusMapping);
        // We expect only 1 request, but we ask for 2 requests here so that if a misbehaving client
        // sends more than 1 requests, ServerCall will catch it. Note that disabling auto
        // inbound flow control has no effect on unary calls.
        call.request(2);

        return new ServerCall.Listener<S>() {
            private boolean canInvoke = true;
            private boolean wasReady;
            private S request;

            @Override
            public void onMessage(S request) {
                if (this.request != null) {
                    // Safe to close the call, because the application has not yet been invoked
                    call.close(
                            Status.INTERNAL.withDescription(TOO_MANY_REQUESTS),
                            new Metadata());
                    canInvoke = false;
                    return;
                }

                // We delay calling method.invoke() until onHalfClose() to make sure the client
                // half-closes.
                this.request = request;
            }

            @Override
            public void onHalfClose() {
                if (!canInvoke) {
                    return;
                }
                if (request == null) {
                    // Safe to close the call, because the application has not yet been invoked
                    call.close(
                            Status.INTERNAL.withDescription(MISSING_REQUEST),
                            new Metadata());
                    return;
                }

                ApiRequest<S> req = new ApiRequest<>();
                req.setHeaders(reqHeaders);
                req.setSelection(selection);
                req.setData(request);

                IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(fieldDefinition.getOperationType(),
                        fieldDefinition.getOperationName(), req);
                responseObserver.context = ctx;

                graphQLEngine.executeRpcAsync(ctx).whenComplete((res, err) -> {
                    if (err != null) {
                        responseObserver.onError(err);
                    } else {
                        try {
                            responseObserver.onNext((ApiResponse<R>) res);
                        } catch (Exception e) {
                            responseObserver.onError(e);
                        }
                        responseObserver.onCompleted();
                    }
                });

                // method.invoke(request, responseObserver);
                request = null;
                responseObserver.freeze();
                if (wasReady) {
                    // Since we are calling invoke in halfClose we have missed the onReady
                    // event from the transport so recover it here.
                    onReady();
                }
            }

            @Override
            public void onCancel() {
                super.onCancel();
            }

            @Override
            public void onComplete() {
                super.onComplete();
            }

            @Override
            public void onReady() {
                wasReady = true;
            }
        };
    }

    private static final class ServerCallStreamObserverImpl<ReqT, RespT>
            extends ServerCallStreamObserver<ApiResponse<RespT>> {
        final ServerCall<ReqT, RespT> call;
        private final boolean serverStreamingOrBidi;
        volatile boolean cancelled;
        private boolean frozen;
        private boolean autoRequestEnabled = true;
        private boolean sentHeaders;
        private Runnable onReadyHandler;
        private Runnable onCancelHandler;
        private boolean aborted = false;
        private boolean completed = false;
        private Runnable onCloseHandler;

        private final GrpcStatusMapping statusMapping;

        private IGraphQLExecutionContext context;

        // Non private to avoid synthetic class
        ServerCallStreamObserverImpl(ServerCall<ReqT, RespT> call, boolean serverStreamingOrBidi,
                                     GrpcStatusMapping statusMapping) {
            this.call = call;
            this.serverStreamingOrBidi = serverStreamingOrBidi;
            this.statusMapping = statusMapping;
        }

        private void freeze() {
            this.frozen = true;
        }

        @Override
        public void setMessageCompression(boolean enable) {
            call.setMessageCompression(enable);
        }

        @Override
        public void setCompression(String compression) {
            call.setCompression(compression);
        }

        @Override
        public void onNext(ApiResponse<RespT> response) {
            if (cancelled) {
                if (serverStreamingOrBidi) {
                    throw Status.CANCELLED
                            .withDescription("call already cancelled. "
                                    + "Use ServerCallStreamObserver.setOnCancelHandler() to disable this exception")
                            .asRuntimeException();
                } else {
                    // We choose not to throw for unary responses. The exception is intended to stop servers
                    // from continuing processing, but for unary responses there is no further processing
                    // so throwing an exception would not provide a benefit and would increase application
                    // complexity.
                }
            }
            checkState(!aborted, "Stream was terminated by error, no further calls are allowed");
            checkState(!completed, "Stream is already completed, no further calls are allowed");

            if (response.isOk()) {
                if (!sentHeaders) {
                    call.sendHeaders(GrpcHelper.buildHeaders(response.getHeaders()));
                    sentHeaders = true;
                }
                call.sendMessage(response.getData());
            } else {
                call.close(statusMapping.mapToStatus(response), GrpcHelper.buildHeaders(response.getHeaders()));
                completed = true;
            }
        }

        @Override
        public void onError(Throwable t) {
            String locale = context == null ? null : context.getContext().getLocale();
            ApiResponse<?> err = ErrorMessageManager.instance().buildResponse(locale, t);

            call.close(statusMapping.mapToStatus(err), GrpcHelper.buildHeaders(err.getHeaders()));
            aborted = true;
        }

        @Override
        public void onCompleted() {
            if (!completed && !aborted) {
                call.close(Status.OK, new Metadata());
                completed = true;
            }
        }

        @Override
        public boolean isReady() {
            return call.isReady();
        }

        @Override
        public void setOnReadyHandler(Runnable r) {
            checkState(!frozen, "Cannot alter onReadyHandler after initialization. May only be called "
                    + "during the initial call to the application, before the service returns its "
                    + "StreamObserver");
            this.onReadyHandler = r;
        }

        @Override
        public boolean isCancelled() {
            return call.isCancelled();
        }

        @Override
        public void setOnCancelHandler(Runnable onCancelHandler) {
            checkState(!frozen, "Cannot alter onCancelHandler after initialization. May only be called "
                    + "during the initial call to the application, before the service returns its "
                    + "StreamObserver");
            this.onCancelHandler = onCancelHandler;
        }

        @Override
        public void disableAutoInboundFlowControl() {
            disableAutoRequest();
        }

        @Override
        public void disableAutoRequest() {
            checkState(!frozen, "Cannot disable auto flow control after initialization");
            autoRequestEnabled = false;
        }

        @Override
        public void request(int count) {
            call.request(count);
        }

        @Override
        public void setOnCloseHandler(Runnable onCloseHandler) {
            checkState(!frozen, "Cannot alter onCloseHandler after initialization. May only be called "
                    + "during the initial call to the application, before the service returns its "
                    + "StreamObserver");
            this.onCloseHandler = onCloseHandler;
        }
    }
}
