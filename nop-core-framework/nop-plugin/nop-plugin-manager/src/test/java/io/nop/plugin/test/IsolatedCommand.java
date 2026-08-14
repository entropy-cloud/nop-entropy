package io.nop.plugin.test;

import io.nop.api.core.util.FutureHelper;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * per-instance 命令隔离测试 bean（bean id = nopPluginCommand_isolated）：
 * tag 属性经 ${agent.only-instance:none} 从实例配置域解析——同一定义的两个实例
 * 配置不同值时命令结果按实例可区分（多实例命令隔离的可观测证明）。
 */
public class IsolatedCommand implements IPluginCommand {
    private String tag;

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        return FutureHelper.success(Map.of("tag", tag));
    }
}
