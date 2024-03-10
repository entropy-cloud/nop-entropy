/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.core.chunk;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class StartChunkResponseBean {
    /**
     * 这次上传的唯一 ID。
     */
    private String fileId;

    /**
     * 记录后端文件存储路径。
     */
    private String key;

    private List<PartInfo> partList;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<PartInfo> getPartList() {
        return partList;
    }

    public void setPartList(List<PartInfo> partList) {
        this.partList = partList;
    }
}