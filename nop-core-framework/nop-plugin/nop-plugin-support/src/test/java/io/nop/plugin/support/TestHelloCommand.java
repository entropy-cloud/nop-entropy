package io.nop.plugin.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 测试命令 bean（装配进 /nop/plugin/plugin.beans.xml，验证 aware invokeCommand 定义级路由
 * 分发于本插件激活容器——不落入全局容器）。
 */
public class TestHelloCommand implements IPluginCommand {

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                    String fieldSelection,
                                                                    IPluginCancelToken cancelToken) {
        String who = args == null ? "" : String.valueOf(args.get("who"));
        return CompletableFuture.completedFuture(Map.of("result", "hello:" + who));
    }
}
