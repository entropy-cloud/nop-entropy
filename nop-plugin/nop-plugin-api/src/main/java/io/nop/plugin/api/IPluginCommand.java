package io.nop.plugin.api;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface IPluginCommand {
    CompletionStage<Map<String, Object>> invokeCommandAsync(String command,
                                                            Map<String, Object> args,
                                                            String fieldSelection,
                                                            IPluginCancelToken cancelToken);
}