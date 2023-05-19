package io.nop.rpc.api.flowcontrol;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface IFlowControlRunner {

    <T> CompletionStage<T> runAsync(FlowControlEntry entry,
                                    Supplier<CompletionStage<T>> task);

    <T> T run(FlowControlEntry entry, Supplier<T> task);
}