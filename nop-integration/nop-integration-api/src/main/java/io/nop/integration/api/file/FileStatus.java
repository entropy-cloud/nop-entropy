/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.api.file;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class FileStatus {
    private String name;
    private long size;
    private long lastModified;
    private String permissions;

    public FileStatus() {
    }

    public FileStatus(String name, long size, long lastModified, String permissions) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.permissions = permissions;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}
