package io.nop.plugin.support;

import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 宿主容器命令 bean（装配进 /main/beans/host-commands.beans.xml，随 CoreInitialization 进入
 * 全局容器）——验证 getCommandBean 回退链的"宿主回退"分支：插件容器未命中时经
 * BeanContainer.tryGetBean 命中宿主 bean。结果标记 "host:{who}" 与 manager 轨
 * io.nop.plugin.test.HostCommand 一致（两轨回退链行为一致的观测锚点）。
 */
public class TestHostCommand implements IPluginCommand {

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                    String fieldSelection,
                                                                    IPluginCancelToken cancelToken) {
        String who = args == null ? "" : String.valueOf(args.get("who"));
        return CompletableFuture.completedFuture(Map.of("result", "host:" + who));
    }
}
