package io.nop.plugin.test;

import io.nop.api.core.util.FutureHelper;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 宿主容器 default 命令兜底 bean（bean id = nopPluginCommand_default，装配进
 * /main/beans/app-host-commands.beans.xml）——验证 VFS 轨定义级路由回退链的
 * "default bean 兜底"分支：指定命令 bean 在插件容器与宿主均未命中时经 default bean 兜底
 * （不静默返回）。结果标记 "default:{command}" 使兜底命中可观测。
 */
public class HostDefaultCommand implements IPluginCommand {

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        return FutureHelper.success(Map.of("result", "default:" + command));
    }
}
