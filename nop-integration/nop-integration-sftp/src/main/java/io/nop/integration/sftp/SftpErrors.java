/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.sftp;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface SftpErrors {
    String ARG_LOCAL_PATH = "localPath";
    String ARG_REMOTE_PATH = "remotePath";
    String ARG_HOST = "host";
    String ARG_PORT = "port";

    ErrorCode ERR_SFTP_CONNECT_FAIL =
            define("nop.err.sftp.connect-fail",
                    "连接远程文件服务[{host}:{port}]失败", ARG_HOST, ARG_PORT);

    ErrorCode ERR_SFTP_DELETE_FILE_FAIL =
            define("nop.err.sftp.delete-file-fail", "删除远程文件失败:{remotePath}", ARG_REMOTE_PATH);

    ErrorCode ERR_SFTP_UPLOAD_FILE_FAIL =
            define("nop.err.sftp.upload-file-fail", "上传文件失败:localPath={localPath},remotePath={}",
                    ARG_LOCAL_PATH, ARG_REMOTE_PATH);

    ErrorCode ERR_SFTP_DOWNLOAD_FILE_FAIL =
            define("nop.err.sftp.download-file-fail", "下载文件失败:localPath={localPath},remotePath={}",
                    ARG_LOCAL_PATH, ARG_REMOTE_PATH);

    ErrorCode ERR_SFTP_DOWNLOAD_FAIL =
            define("nop.err.sftp.download-fail", "下载文件失败:remotePath={}",
                    ARG_REMOTE_PATH);

    ErrorCode ERR_SFTP_LIST_FILE_FAIL =
            define("nop.err.sftp.list-file-fail",
                    "查看远程文件列表失败:{remotePath}", ARG_REMOTE_PATH);
}
