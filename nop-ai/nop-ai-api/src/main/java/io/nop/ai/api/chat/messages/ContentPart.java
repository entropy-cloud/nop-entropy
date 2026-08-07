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
 * 多模态内容片段（plan 326）。作为 {@link ChatUserMessage#getParts()} 的元素，使多模态（text/image/audio）
 * 成为一等公民，根治既有 {@code attachments} 无方言序列化的问题。
 * <p>
 * 字段语义（与 {@code type} 配合）：
 * <ul>
 *   <li>{@link #TYPE_TEXT}：{@link #getText} 为文本内容</li>
 *   <li>{@link #TYPE_IMAGE}：{@link #getImageUrl}（URL）或 {@link #getData}（data URL / base64），
 *       {@link #getDetail} 为可选的细节级别（low/high/auto）</li>
 *   <li>{@link #TYPE_AUDIO}：{@link #getData} 为 data URL / base64 音频数据</li>
 * </ul>
 * 本计划只建模型，不实现方言序列化（330 ResponsesDialect 负责）。
 */
@DataBean
public class ContentPart {

    public static final String TYPE_TEXT = "text";

    public static final String TYPE_IMAGE = "image";

    public static final String TYPE_AUDIO = "audio";

    private String type;

    private String text;

    private String detail;

    private String imageUrl;

    private String data;

    public ContentPart() {
    }

    public static ContentPart textOf(String text) {
        ContentPart part = new ContentPart();
        part.type = TYPE_TEXT;
        part.text = text;
        return part;
    }

    public static ContentPart imageOfUrl(String imageUrl, String detail) {
        ContentPart part = new ContentPart();
        part.type = TYPE_IMAGE;
        part.imageUrl = imageUrl;
        part.detail = detail;
        return part;
    }

    public static ContentPart imageOfData(String data) {
        ContentPart part = new ContentPart();
        part.type = TYPE_IMAGE;
        part.data = data;
        return part;
    }

    public static ContentPart audioOfData(String data) {
        ContentPart part = new ContentPart();
        part.type = TYPE_AUDIO;
        part.data = data;
        return part;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public ContentPart copy() {
        ContentPart copy = new ContentPart();
        copy.type = this.type;
        copy.text = this.text;
        copy.detail = this.detail;
        copy.imageUrl = this.imageUrl;
        copy.data = this.data;
        return copy;
    }
}
