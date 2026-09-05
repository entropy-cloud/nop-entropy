/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

import io.nop.api.core.util.progress.IProgressListener;

public class DownloadOptions {
    private long initialOffset;
    private IProgressListener progressListener;

    /**
     * 是否启用断点续传：目标路径旁的 {name}.part 临时文件为续传状态
     */
    private boolean resume = true;

    /**
     * 调用方显式指定的期望 SHA-256（hex）。优先级高于响应头与 sidecar 校验文件
     */
    private String expectedSha256;

    /**
     * 调用方显式指定的期望 SHA-1（hex）。仅在无 SHA-256 来源时使用
     */
    private String expectedSha1;

    /**
     * 无显式/响应头校验来源时，是否探测同目录 sidecar 校验文件（{url}.sha256/.sha1）
     */
    private boolean fetchSidecarChecksum = true;

    /**
     * 要求必须有校验来源：探测后仍无来源时报错，否则按尽力而为语义跳过校验
     */
    private boolean requireChecksum = false;

    public long getInitialOffset() {
        return initialOffset;
    }

    public void setInitialOffset(long initialOffset) {
        this.initialOffset = initialOffset;
    }

    public IProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(IProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public boolean isResume() {
        return resume;
    }

    public void setResume(boolean resume) {
        this.resume = resume;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }

    public String getExpectedSha1() {
        return expectedSha1;
    }

    public void setExpectedSha1(String expectedSha1) {
        this.expectedSha1 = expectedSha1;
    }

    public boolean isFetchSidecarChecksum() {
        return fetchSidecarChecksum;
    }

    public void setFetchSidecarChecksum(boolean fetchSidecarChecksum) {
        this.fetchSidecarChecksum = fetchSidecarChecksum;
    }

    public boolean isRequireChecksum() {
        return requireChecksum;
    }

    public void setRequireChecksum(boolean requireChecksum) {
        this.requireChecksum = requireChecksum;
    }
}
