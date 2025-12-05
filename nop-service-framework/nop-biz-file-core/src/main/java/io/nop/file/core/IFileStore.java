/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.core;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;

public interface IFileStore {
    default CompletionStage<String> saveFileAsync(UploadRequestBean record, long maxLength) {
        return FutureHelper.futureCall(() -> saveFile(record, maxLength));
    }

    default String saveFile(UploadRequestBean record, long maxLength) {
        return FutureHelper.syncGet(saveFileAsync(record, maxLength));
    }

    String getFileLink(String fileId);

    IFileRecord getFile(String fileId);
}