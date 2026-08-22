package io.nop.plugin.test;

import io.nop.api.core.util.FutureHelper;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 宿主容器命令 bean（装配进 /main/beans/app-host-commands.beans.xml，随 CoreInitialization
 * 进入全局容器）——验证 VFS 轨定义级路由回退链的"宿主回退"分支：插件激活容器未命中时命中
 * 宿主 bean。结果标记 "host:{who}" 与 jar 轨 io.nop.plugin.support.TestHostCommand 一致
 * （两轨回退链行为一致的观测锚点）。
 */
public class HostCommand implements IPluginCommand {

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        String who = args == null ? "" : String.valueOf(args.get("who"));
        return FutureHelper.success(Map.of("result", "host:" + who));
    }
}
