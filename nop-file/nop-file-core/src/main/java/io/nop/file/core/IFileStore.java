package io.nop.file.core;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;

public interface IFileStore {
    default CompletionStage<String> saveFileAsync(IFileRecord record, long maxLength) {
        return FutureHelper.futureCall(() -> saveFile(record, maxLength));
    }

    default String saveFile(IFileRecord record, long maxLength) {
        return FutureHelper.syncGet(saveFileAsync(record, maxLength));
    }

    IFileRecord getFile(String fileId);
}