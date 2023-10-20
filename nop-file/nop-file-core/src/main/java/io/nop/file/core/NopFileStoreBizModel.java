/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.core;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.auth.IBizAuthChecker;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;

import static io.nop.file.core.FileErrors.ARG_ALLOWED_FILE_EXTS;
import static io.nop.file.core.FileErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.file.core.FileErrors.ARG_FILE_EXT;
import static io.nop.file.core.FileErrors.ARG_LENGTH;
import static io.nop.file.core.FileErrors.ARG_MAX_LENGTH;
import static io.nop.file.core.FileErrors.ERR_FILE_INVALID_BIZ_OBJ_NAME;
import static io.nop.file.core.FileErrors.ERR_FILE_LENGTH_EXCEED_LIMIT;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_ALLOW_FILE_EXT;

@BizModel("NopFileStore")
public class NopFileStoreBizModel {

    protected IFileStore fileStore;

    protected long maxFileSize;

    private IBizAuthChecker bizAuthChecker;

    private Set<String> allowedFileExts;

    @Inject
    @Nullable
    public void setBizAuthChecker(IBizAuthChecker bizAuthChecker) {
        this.bizAuthChecker = bizAuthChecker;
    }

    @Inject
    public void setFileStore(IFileStore fileStore) {
        this.fileStore = fileStore;
    }

    /**
     * 这里配置的变量名需要和XuiConfigs中的配置名一致
     *
     * @param maxFileSize 最大允许上传的文件大小
     */
    @InjectValue("@cfg:nop.file.upload.max-size|16777216")
    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }


    @InjectValue("@cfg:nop.file.upload.allowed-file-exts|")
    public void setAllowedFileExts(Set<String> allowedFileExts) {
        this.allowedFileExts = allowedFileExts;
    }


    protected void checkMaxSize(long length) {
        if (length > maxFileSize)
            throw new NopException(ERR_FILE_LENGTH_EXCEED_LIMIT)
                    .param(ARG_LENGTH, length).param(ARG_MAX_LENGTH, maxFileSize);
    }

    protected void checkFileExt(String fileExt) {
        if (allowedFileExts != null && !allowedFileExts.isEmpty()) {
            if (!allowedFileExts.contains(fileExt))
                throw new NopException(ERR_FILE_NOT_ALLOW_FILE_EXT)
                        .param(ARG_FILE_EXT, fileExt).param(ARG_ALLOWED_FILE_EXTS, allowedFileExts);
        }
    }

    protected void checkBizObjName(String bizObjName) {
        if (!StringHelper.isValidSimpleVarName(bizObjName))
            throw new NopException(ERR_FILE_INVALID_BIZ_OBJ_NAME)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName);
    }

    @BizMutation
    public UploadResponseBean upload(@RequestBean UploadRequestBean record, IServiceContext context) {
        checkMaxSize(record.getLength());
        checkFileExt(record.getFileExt());
        checkBizObjName(record.getBizObjName());

        String fileId = fileStore.saveFile(record, maxFileSize);

        UploadResponseBean ret = new UploadResponseBean();
        ret.setValue(fileStore.getFileLink(fileId));
        ret.setFilename(record.getFileName());
        return ret;
    }

    @BizQuery
    public WebContentBean download(@Name("fileId") String fileId,
                                   @Name("contentType") String contentType, IServiceContext ctx) {
        IFileRecord record = loadFileRecord(fileId,ctx);
        if (StringHelper.isEmpty(contentType))
            contentType = MediaType.APPLICATION_OCTET_STREAM;

        return new WebContentBean(contentType, record.getResource(), record.getFileName());
    }

    protected IFileRecord loadFileRecord(String fileId, IServiceContext ctx) {
        IFileRecord record = fileStore.getFile(fileId);
        if (bizAuthChecker != null) {
            bizAuthChecker.checkAuth(record.getBizObjName(), record.getBizObjId(), record.getFieldName(), ctx);
        }
        return record;
    }
}