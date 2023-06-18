package io.nop.http.api.server;

import java.util.concurrent.CompletionStage;

public interface IAsyncBody {
    CompletionStage<String> getTextAsync();
}