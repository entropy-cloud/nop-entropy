/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.rpc.core.message.DefaultRpcMessageAdapter;
import io.nop.rpc.core.message.MessageRpcServer;
import io.nop.rpc.core.reflect.DefaultRpcMessageTransformer;
import io.nop.rpc.core.reflect.ReflectiveRpcService;

import java.util.concurrent.CompletionStage;

/**
 * Stage 39 Phase 2: server-side adapter that exposes a streaming control-plane
 * RPC service ({@link IStreamTaskRpcService} on the task side,
 * {@link IStreamCoordinatorRpcService} on the coordinator side) over the platform
 * RPC framework.
 *
 * <p>Wires {@link MessageRpcServer} (RPC-over-{@link IMessageService}, topic-addressed)
 * with a {@link ReflectiveRpcService} that dispatches incoming {@code ApiRequest}s to
 * the service implementation by reflective method lookup. The default
 * {@link DefaultRpcMessageTransformer} maps method arguments by parameter name into
 * the request data map (and back), so no bespoke message adapter is required unless
 * a future method shape defeats reflective dispatch (plan guide: verify default
 * reflective dispatch first, add an adapter only if insufficient).
 *
 * <p>Server selection rationale (Phase 2 Decision 3): {@code MessageRpcServer} is
 * chosen over {@code SimpleRpcServer} (socket) for consistency with the Stage 40
 * data-plane {@code IMessageService} backend and to avoid per-node port allocation.
 */
@Internal
public class StreamControlRpcServer extends LifeCycleSupport {

    private final MessageRpcServer server;

    /**
     * Builds and starts-not a control-plane RPC server. Call {@link #start()} to
     * subscribe to the topic.
     *
     * @param serviceName      logical RPC service name (used for header/routing)
     * @param serviceInterface the RPC interface ({@link IStreamTaskRpcService} or
     *                         {@link IStreamCoordinatorRpcService})
     * @param serviceImpl      the implementation that handles dispatched calls
     * @param messageService   the transport (e.g. {@code LocalMessageService} for
     *                         in-JVM tests, {@code SysDaoMessageService}/
     *                         {@code PulsarMessageService} in production)
     * @param topic            the topic the server subscribes to (per-nodeId for
     *                         task side, per-jobId for coordinator side)
     */
    public StreamControlRpcServer(String serviceName,
                                  Class<?> serviceInterface,
                                  Object serviceImpl,
                                  IMessageService messageService,
                                  String topic) {
        ReflectiveRpcService rpcService = new ReflectiveRpcService(
                serviceName, serviceInterface, serviceImpl, DefaultRpcMessageTransformer.INSTANCE);

        MessageRpcServer srv = new MessageRpcServer();
        srv.setTopic(topic);
        srv.setMessageService(messageService);
        // MessageRpcServer (unlike SimpleRpcServer) does not call
        // IRpcMessageTransformer.enrichResponse, so request-response calls would
        // time out on the client (no relId correlation). Wrap the handler so every
        // success response carries the request id as relId, enabling
        // MessageRpcClient response correlation.
        srv.setRpcService(new CorrelatingRpcService(rpcService));
        srv.setMessageAdapter(DefaultRpcMessageAdapter.INSTANCE);
        this.server = srv;
    }

    public String getTopic() {
        return server.getTopic();
    }

    @Override
    protected void doStart() {
        server.start();
    }

    @Override
    protected void doStop() {
        server.stop();
    }

    /**
     * Wraps an {@link IRpcService} so every success response carries the request id
     * as {@code relId}, enabling {@code MessageRpcClient} request-response
     * correlation. {@code MessageRpcServer} does not invoke
     * {@code IRpcMessageTransformer.enrichResponse} itself (unlike
     * {@code SimpleRpcServer}), so without this wrapper non-void control calls
     * (e.g. {@code getJobStatus()}) time out on the client.
     */
    static final class CorrelatingRpcService implements IRpcService {
        private final IRpcService delegate;

        CorrelatingRpcService(IRpcService delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                         ICancelToken cancelToken) {
            String reqId = ApiHeaders.getId(request);
            CompletionStage<ApiResponse<?>> stage = delegate.callAsync(serviceMethod, request, cancelToken);
            if (stage == null) {
                return null;
            }
            return stage.handle((res, ex) -> {
                ApiResponse<?> out;
                if (ex != null) {
                    out = DefaultRpcMessageAdapter.INSTANCE.getErrorResponse(
                            ex instanceof Exception ? (Exception) ex : new RuntimeException(ex), request);
                } else {
                    out = res;
                }
                if (out != null && reqId != null && ApiHeaders.getRelId(out) == null) {
                    ApiHeaders.setRelId(out, reqId);
                }
                return out;
            });
        }
    }
}
