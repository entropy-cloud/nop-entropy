package io.nop.plugin.test;

import io.nop.api.core.util.FutureHelper;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * per-instance 命令路由测试 bean（bean id = nopPluginCommand_hello）。
 */
public class HelloCommand implements IPluginCommand {

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        AgentInstanceRecorder.event("command:" + command);
        return FutureHelper.success(Map.of("result", "hello:" + args.get("who")));
    }
}
