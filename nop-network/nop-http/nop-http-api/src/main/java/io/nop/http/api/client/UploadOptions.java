/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

import io.nop.api.core.util.progress.IProgressListener;

public class UploadOptions {
    private IProgressListener progressListener;

    /**
     * 上传模式，默认二进制流直传
     */
    private UploadMode mode = UploadMode.BINARY;

    /**
     * BINARY 模式使用的 HTTP 方法，默认 PUT（幂等语义），可改为 POST
     */
    private String httpMethod = "PUT";

    /**
     * BINARY 模式是否计算并携带 x-file-sha256 头，供服务端校验
     */
    private boolean computeSha256 = true;

    /**
     * BASE64_FORM 模式中承载文件内容的表单字段名
     */
    private String fieldName = "file";

    /**
     * BASE64_FORM 模式中承载原始文件名的表单字段名
     */
    private String fileNameField = "filename";

    public IProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(IProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public UploadMode getMode() {
        return mode;
    }

    public void setMode(UploadMode mode) {
        this.mode = mode;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public boolean isComputeSha256() {
        return computeSha256;
    }

    public void setComputeSha256(boolean computeSha256) {
        this.computeSha256 = computeSha256;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFileNameField() {
        return fileNameField;
    }

    public void setFileNameField(String fileNameField) {
        this.fileNameField = fileNameField;
    }
}
