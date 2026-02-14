/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 聊天消息附件
 */
@DataBean
public class ChatAttachment {

    /**
     * 资源类型（image/audio/file等）
     */
    private String resourceType;

    /**
     * 资源URL或Data URI
     */
    private String resourceUrl;

    /**
     * 文件名（可选）
     */
    private String fileName;

    public ChatAttachment() {
    }

    public ChatAttachment(String resourceType, String resourceUrl) {
        this.resourceType = resourceType;
        this.resourceUrl = resourceUrl;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getResourceUrl() {
        return resourceUrl;
    }

    public void setResourceUrl(String resourceUrl) {
        this.resourceUrl = resourceUrl;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 创建图片附件
     */
    public static ChatAttachment forImage(String url) {
        return new ChatAttachment("image", url);
    }

    /**
     * 创建音频附件
     */
    public static ChatAttachment forAudio(String url) {
        return new ChatAttachment("audio", url);
    }

    /**
     * 创建文件附件
     */
    public static ChatAttachment forFile(String url, String fileName) {
        ChatAttachment attachment = new ChatAttachment("file", url);
        attachment.setFileName(fileName);
        return attachment;
    }

    /**
     * 创建附件的深拷贝
     */
    public ChatAttachment copy() {
        ChatAttachment copy = new ChatAttachment();
        copy.resourceType = this.resourceType;
        copy.resourceUrl = this.resourceUrl;
        copy.fileName = this.fileName;
        return copy;
    }
}
