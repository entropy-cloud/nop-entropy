/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.watch;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.io.File;
import java.nio.file.Path;

@DataBean
public class FileChangeEvent {
    public static final int CHANGE_TYPE_ADD = 1;
    public static final int CHANGE_TYPE_MODIFY = 2;
    public static final int CHANGE_TYPE_DELETE = 3;

    private final int changeType;
    private final Path path;

    public FileChangeEvent(@JsonProperty("changeType") int changeType,
                           @JsonProperty("path") Path path) {
        this.changeType = changeType;
        this.path = path;
    }

    public int hashCode() {
        return 31 * changeType + path.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof FileChangeEvent)) {
            return false;
        }

        FileChangeEvent other = (FileChangeEvent) o;
        return changeType == other.changeType && path.equals(other.path);
    }

    public String toString() {
        return "Change[path=" + path + ",changeType=" + changeType + "]";
    }

    public boolean isAdd() {
        return changeType == CHANGE_TYPE_ADD;
    }

    public boolean isModify() {
        return changeType == CHANGE_TYPE_MODIFY;
    }

    public boolean isDelete() {
        return changeType == CHANGE_TYPE_DELETE;
    }

    public int getChangeType() {
        return changeType;
    }

    public Path getPath() {
        return path;
    }

    public File getFile() {
        return path.toFile();
    }
}
