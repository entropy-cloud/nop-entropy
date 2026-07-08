package io.nop.batch.dsl.runner;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.FutureHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface IBatchTaskRunner {

    CompletionStage<Void> executeAsync(@Name("taskPath") String taskPath,
                                        @Name("params") Map<String, Object> params);

    default void execute(@Name("taskPath") String taskPath,
                          @Name("params") Map<String, Object> params) {
        FutureHelper.syncGet(executeAsync(taskPath, params));
    }

    default void execute(@Name("taskPath") String taskPath) {
        execute(taskPath, null);
    }
}
