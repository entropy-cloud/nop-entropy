package io.nop.plugin.api;

import java.util.function.Consumer;

public interface IPluginCancelToken {

    boolean isCancelled();

    String getCancelReason();

    /**
     * 增加取消操作时的回调函数。
     *
     * @param task 回调函数，参数为cancelReason
     */
    void appendOnCancel(Consumer<String> task);

    default void appendOnCancelTask(Runnable task) {
        if (task == null)
            return;

        appendOnCancel(r -> task.run());
    }

    void removeOnCancel(Consumer<String> task);
}
