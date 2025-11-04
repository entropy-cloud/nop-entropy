/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.util.ApiStringHelper;

@GraphQLObject
@GraphQLBean
@DataBean
public class FileStatusBean {
    private String fileId;
    private String name;
    private long size;
    private long lastModified;
    private String permissions;
    private String externalPath;
    private String previewPath;

    public FileStatusBean() {
    }

    public FileStatusBean(String name, long size, long lastModified, String permissions) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.permissions = permissions;
    }

    @PropMeta(propId = 1)
    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    @PropMeta(propId = 2)
    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @PropMeta(propId = 3)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @PropMeta(propId = 4)
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @PropMeta(propId = 5)
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    /**
     * INTENTIONAL 返回计算属性简化前端编程
     */
    @PropMeta(propId = 6)
    public String getFileSize() {
        return ApiStringHelper.fileSizeString(getSize());
    }

    @PropMeta(propId = 7)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getExternalPath() {
        return externalPath;
    }

    public void setExternalPath(String externalPath) {
        this.externalPath = externalPath;
    }

    @PropMeta(propId = 8)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getPreviewPath() {
        return previewPath;
    }

    public void setPreviewPath(String previewPath) {
        this.previewPath = previewPath;
    }
}
