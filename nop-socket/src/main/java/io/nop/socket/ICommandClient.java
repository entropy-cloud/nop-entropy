/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface ICommandClient extends AutoCloseable {
    ClientConfig getClientConfig();

    default BinaryCommand newCommand(short cmd, short flags, String data) {
        return BinaryCommand.newCommand(getClientConfig(), cmd, flags, data);
    }

    void setOnChannelOpen(Runnable onOpen);

    void setOnChannelClose(Runnable onClose);

    void connect();

    void send(BinaryCommand cmd, boolean flush);

    void ping();

    void reconnect();

    /**
     * recv函数只能由一个线程调用
     *
     * @param handler
     */
    void recv(Executor executor, Consumer<BinaryCommand> handler);
}
