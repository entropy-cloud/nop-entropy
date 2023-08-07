package io.nop.file.core;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.auth.IBizAuthChecker;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.file.core.FileErrors.ARG_ALLOWED_FILE_EXTS;
import static io.nop.file.core.FileErrors.ARG_BIZ_OBJ_ID;
import static io.nop.file.core.FileErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.file.core.FileErrors.ARG_FIELD_NAME;
import static io.nop.file.core.FileErrors.ARG_FILE_EXT;
import static io.nop.file.core.FileErrors.ARG_LENGTH;
import static io.nop.file.core.FileErrors.ARG_MAX_LENGTH;
import static io.nop.file.core.FileErrors.ERR_FILE_LENGTH_EXCEED_LIMIT;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_ALLOW_ACCESS_FILE;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_ALLOW_FILE_EXT;

@BizModel("NopFileStore")
public class NopFileStoreBizModel {

    protected IFileStore fileStore;

    protected long maxFileLength;

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

    @InjectValue("@cfg:nop.file.upload.max-length|16777216")
    public void setMaxFileLength(long maxFileLength) {
        this.maxFileLength = maxFileLength;
    }


    @InjectValue("@cfg:nop.file.upload.allowed-file-exts:")
    public void setAllowedFileExts(Set<String> allowedFileExts) {
        this.allowedFileExts = allowedFileExts;
    }


    protected void checkMaxLength(long length) {
        if (length > maxFileLength)
            throw new NopException(ERR_FILE_LENGTH_EXCEED_LIMIT)
                    .param(ARG_LENGTH, length).param(ARG_MAX_LENGTH, maxFileLength);
    }

    protected void checkFileExt(String fileExt) {
        if (allowedFileExts != null && !allowedFileExts.isEmpty()) {
            if (!allowedFileExts.contains(fileExt))
                throw new NopException(ERR_FILE_NOT_ALLOW_FILE_EXT)
                        .param(ARG_FILE_EXT, fileExt).param(ARG_ALLOWED_FILE_EXTS, allowedFileExts);
        }
    }

    @BizMutation
    public ApiResponse<?> upload(@RequestBean UploadRequestBean record, IServiceContext context) {
        checkMaxLength(record.getLength());
        checkFileExt(record.getFileExt());

        String fileId = fileStore.saveFile(record, maxFileLength);

        Map<String, Object> data = new HashMap<>();
        data.put("value", fileStore.getFileLink(fileId));
        ApiResponse<Map<String, Object>> res = ApiResponse.buildSuccess(data);
        return res;
    }

    @BizQuery
    public WebContentBean download(@Name("fileId") String fileId,
                                   @Name("contentType") String contentType) {
        IFileRecord record = fileStore.getFile(fileId);
        if (StringHelper.isEmpty(contentType))
            contentType = MediaType.APPLICATION_OCTET_STREAM;

        return new WebContentBean(contentType, record.getResource(), record.getFileName());
    }

    protected IFileRecord loadFileRecord(String fileId, IServiceContext ctx) {
        IFileRecord record = fileStore.getFile(fileId);
        if (bizAuthChecker != null) {
            if (!bizAuthChecker.isPermitted(record.getBizObjName(), record.getBizObjId(), record.getFieldName(), ctx))
                throw new NopException(ERR_FILE_NOT_ALLOW_ACCESS_FILE)
                        .param(ARG_BIZ_OBJ_NAME, record.getBizObjName())
                        .param(ARG_BIZ_OBJ_ID, record.getBizObjId())
                        .param(ARG_FIELD_NAME, record.getFileName());
        }
        return record;
    }
}