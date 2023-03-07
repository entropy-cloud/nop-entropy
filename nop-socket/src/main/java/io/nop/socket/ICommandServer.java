/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.socket;

import io.nop.commons.service.ILifeCycle;

import java.util.function.Consumer;

public interface ICommandServer extends ILifeCycle {

    ServerConfig getServerConfig();

    void setCommandHandler(ICommandHandler handler);

    void start();

    void stop();

    boolean isActive();

    void setOnChannelOpen(Consumer<String> onChannelOpen);

    void setOnChannelClose(Consumer<String> onChannelClose);

    default BinaryCommand newCommand(short cmd, short flags, String data) {
        return BinaryCommand.newCommand(getServerConfig(), cmd, flags, data);
    }

    void broadcast(BinaryCommand command);

    void sendTo(String addr, BinaryCommand command);

    boolean waitConnected(long timeout);
}