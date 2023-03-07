/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.api.file;

import io.nop.api.core.resource.IResourceReference;

import java.io.OutputStream;
import java.util.List;

public interface IFileServiceClient extends AutoCloseable {
    List<FileStatus> listFiles(String remotePath);

    void deleteFile(String remotePath);

    /**
     * @param localPath  本地文件路径
     * @param remotePath 远程文件路径
     * @return 远程文件的绝对路径
     */
    String uploadFile(String localPath, String remotePath);

    String downloadFile(String remotePath, String localPath);

    String uploadResource(IResourceReference file, String remotePath);

    void downloadToStream(String remotePath, OutputStream out);
}