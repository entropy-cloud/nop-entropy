package io.nop.file.core;

import io.nop.api.core.util.FutureHelper;
import io.nop.orm.IOrmEntityFileStore;

import java.util.concurrent.CompletionStage;

public interface IFileStore extends IOrmEntityFileStore {
    default CompletionStage<String> saveFileAsync(UploadRequestBean record, long maxLength) {
        return FutureHelper.futureCall(() -> saveFile(record, maxLength));
    }

    default String saveFile(UploadRequestBean record, long maxLength) {
        return FutureHelper.syncGet(saveFileAsync(record, maxLength));
    }

    String getFileLink(String fileId);

    IFileRecord getFile(String fileId);
}