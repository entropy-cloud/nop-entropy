package io.nop.ai.core.processor;

import io.nop.ai.core.api.messages.Prompt;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface IAiTextProcessor {
    CompletionStage<String> processAsync(String text, Prompt prompt, Function<Prompt, CompletionStage<String>> chat);
}
