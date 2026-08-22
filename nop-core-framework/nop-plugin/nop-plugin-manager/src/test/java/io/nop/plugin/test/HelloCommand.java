package io.nop.plugin.test;

import io.nop.api.core.util.FutureHelper;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 定义级命令路由测试 bean（bean id = nopPluginCommand_hello）：invokeCommand 分发于
 * 本插件激活容器（单容器定义级路由，非激活态抛 INACTIVE）。
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
