package io.nop.file.core;

import io.nop.api.core.util.FutureHelper;
import io.nop.biz.api.IBizEntityFileStore;

import java.util.concurrent.CompletionStage;

public interface IFileStore extends IBizEntityFileStore {
    default CompletionStage<String> saveFileAsync(UploadRequestBean record, long maxLength) {
        return FutureHelper.futureCall(() -> saveFile(record, maxLength));
    }

    default String saveFile(UploadRequestBean record, long maxLength) {
        return FutureHelper.syncGet(saveFileAsync(record, maxLength));
    }

    String getFileLink(String fileId);

    IFileRecord getFile(String fileId);
}