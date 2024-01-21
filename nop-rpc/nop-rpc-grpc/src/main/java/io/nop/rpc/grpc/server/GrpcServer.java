package io.nop.rpc.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class GrpcServer extends LifeCycleSupport {
    private Server server;

    private IThreadPoolExecutor executor;

    private boolean ownExecutor;

    private GrpcServerConfig config;

    private ServiceSchemaManager schemaManager;

    public void setConfig(GrpcServerConfig config) {
        this.config = config;
    }

    public void setExecutor(IThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Inject
    public void setServiceSchemaManager(ServiceSchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    @Override
    protected void doStart() {
        // 创建gRPC服务器
        ServerBuilder<?> builder = newBuilder();

        addServices(builder);
        this.server = builder
                .addService(ProtoReflectionService.newInstance()) // 添加Proto Reflection服务
                .build();

        // 启动服务器
        try {
            server.start();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    protected ServerBuilder<?> newBuilder() {
        Guard.notNull(config, "serverConfig");

        ServerBuilder<?> builder = ServerBuilder.forPort(config.getPort());
        if (executor == null) {
            executor = DefaultThreadPoolExecutor.newExecutor(getThreadPoolConfig());
            ownExecutor = true;
            builder.executor(executor);
        }

        if (config.getPrivateKey() != null) {
            builder.useTransportSecurity(config.getCertChain(), config.getPrivateKey());
        }

        if (config.getHandshakeTimeout() != null) {
            builder.handshakeTimeout(config.getHandshakeTimeout().get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
        }


        if (config.getKeepAliveTimeout() != null) {
            builder.keepAliveTimeout(config.getKeepAliveTimeout().get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
        }


        if (config.getMaxConnectionIdle() != null) {
            builder.maxConnectionIdle(config.getMaxConnectionIdle().get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
        }


        if (config.getMaxConnectionAge() != null) {
            builder.maxConnectionAge(config.getMaxConnectionAge().get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
        }


        if (config.getMaxConnectionArgGrace() != null) {
            builder.maxConnectionAgeGrace(config.getMaxConnectionArgGrace().get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
        }


        if (config.getPermitKeepAliveTime() != null) {
            builder.permitKeepAliveTime(config.getPermitKeepAliveTime().get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
        }

        if (config.getPermitKeepAliveWithoutCalls() != null) {
            builder.permitKeepAliveWithoutCalls(config.getPermitKeepAliveWithoutCalls());
        }

        if (config.getMaxInboundMessageSize() > 0) {
            builder.maxInboundMessageSize(config.getMaxInboundMessageSize());
        }

        if (config.getMaxInboundMetadataSize() > 0) {
            builder.maxInboundMetadataSize(config.getMaxInboundMetadataSize());
        }
        return builder;
    }

    private ThreadPoolConfig getThreadPoolConfig() {
        ThreadPoolConfig threadPoolConfig = config.getThreadPool();
        if (threadPoolConfig == null) {
            threadPoolConfig = new ThreadPoolConfig();
        }
        String name = threadPoolConfig.getName();
        if (StringHelper.isEmpty(name)) {
            name = "nop-grpc-thread-pool";
            threadPoolConfig.setName(name);
            threadPoolConfig.setThreadDaemon(true);
        }
        return threadPoolConfig;
    }

    @Override
    protected void doStop() {
        if (server != null) {
            try {
                server.shutdownNow();
                server = null;
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }

        if (executor != null && ownExecutor) {
            executor.destroy();
            executor = null;
            ownExecutor = false;
        }
    }

    private void addServices(ServerBuilder<?> builder) {
        for (String objType : schemaManager.getGraphQLObjectTypes()) {
            ServerServiceDefinition serviceDef = schemaManager.getServiceDefinition(objType);
            builder.addService(serviceDef);
        }
    }
}
