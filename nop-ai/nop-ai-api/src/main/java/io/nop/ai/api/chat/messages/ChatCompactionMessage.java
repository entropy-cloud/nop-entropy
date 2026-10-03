package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 压缩产物类型化条目（plan 2296 / WI15）：session 历史内"发生过一次压缩"的权威
 * 记录——summary 全文 + 压缩计量（tokensBefore/After、originalSize/compactedSize、
 * shadowedTokenCount）+ 压缩前原文快照指针（snapshotId）+ 产出的压缩层名与时间。
 *
 * <p><b>形态裁定</b>：继承 {@link ChatUserMessage}——压缩 summary 的本质是"模型应
 * 看到的上下文内容"，作为 user 子类后全部既有消费方（dialect 请求构建只读
 * {@link #getContent()}、{@code instanceof ChatUserMessage} 分支、summary 探测）
 * 零改动兼容，wire 格式不变（扩展字段不外发给 provider）。
 * {@link #getRole()} 覆写为 {@code "compaction"}，与 ORM 侧 nop_ai_session_message
 * 的 role=30 compaction 映射语义一致。
 *
 * <p><b>迁移说明（additive）</b>：旧版本构建可以读取不含本类型的历史（多态注册
 * 只增不改）；反之，<b>旧构建读取含本条目的历史会因未注册的
 * {@code type:"compaction"} 抛 InvalidTypeIdException</b>——回滚到旧版本时需接受
 * session load 失败或清理对应 session。summary 文本仍以 {@code SUMMARY_MARKER}
 * 前缀放在继承的 content 中（对位既有 Layer3 探测协议，双形态兼容）。
 *
 * <p><b>职责边界</b>（owner doc session-and-storage 快照增量节）：本条目是历史内
 * 压缩事实的唯一权威记录；COMPACTION checkpoint（恢复水位）、snapshot archive
 * （压缩前原文）、COMPACTION 事件（瞬态通知）职责不变，互不重复。
 */
@DataBean
public class ChatCompactionMessage extends ChatUserMessage {

    /**
     * 压缩前 token 估算值（与 CompactionResult.tokensBefore 同源同值）。
     */
    private Long tokensBefore;

    /**
     * 压缩后 token 估算值（与 CompactionResult.tokensAfter 同源同值）。
     */
    private Long tokensAfter;

    /**
     * 压缩前消息数（真实历史长度，非 CompactionResult 兼容代理值）。
     */
    private Long originalSize;

    /**
     * 压缩后消息数（真实压缩结果长度）。
     */
    private Long compactedSize;

    /**
     * 被压缩静默移出可见上下文的 prompt token 数（派生量，由 coordinator 富化）。
     */
    private Long shadowedTokenCount;

    /**
     * 压缩前原文在 snapshot archive 中的寻址 id（由 coordinator 富化；archive
     * 不可解析时为 null——"archive 不可用"，非失败）。
     */
    private String snapshotId;

    /**
     * 产出本条目的压缩层名（strategy NAME，如 "layer3-full-summary"）。
     */
    private String compactionLayer;

    /**
     * 条目产出时间（epoch millis）。
     */
    private Long compactedAt;

    public ChatCompactionMessage() {
    }

    public ChatCompactionMessage(String summaryContent) {
        // SUMMARY_MARKER 前缀由调用方拼入 content——保持与既有 Layer3 探测
        // 协议（startsWith + substring）的双形态兼容。
        super(summaryContent);
    }

    @Override
    public String getRole() {
        return "compaction";
    }

    @Override
    public ChatCompactionMessage copy() {
        // 不覆写则继承 ChatUserMessage.copy() 返回纯 user 消息：新字段全丢、
        // role 回落 "user"——任何 copy 路径都会静默降级本条目。
        ChatCompactionMessage copy = new ChatCompactionMessage(getContent());
        copy.setTokensBefore(tokensBefore);
        copy.setTokensAfter(tokensAfter);
        copy.setOriginalSize(originalSize);
        copy.setCompactedSize(compactedSize);
        copy.setShadowedTokenCount(shadowedTokenCount);
        copy.setSnapshotId(snapshotId);
        copy.setCompactionLayer(compactionLayer);
        copy.setCompactedAt(compactedAt);
        return copy;
    }

    public Long getTokensBefore() {
        return tokensBefore;
    }

    public void setTokensBefore(Long tokensBefore) {
        this.tokensBefore = tokensBefore;
    }

    public Long getTokensAfter() {
        return tokensAfter;
    }

    public void setTokensAfter(Long tokensAfter) {
        this.tokensAfter = tokensAfter;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(Long originalSize) {
        this.originalSize = originalSize;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getCompactedSize() {
        return compactedSize;
    }

    public void setCompactedSize(Long compactedSize) {
        this.compactedSize = compactedSize;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getShadowedTokenCount() {
        return shadowedTokenCount;
    }

    public void setShadowedTokenCount(Long shadowedTokenCount) {
        this.shadowedTokenCount = shadowedTokenCount;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCompactionLayer() {
        return compactionLayer;
    }

    public void setCompactionLayer(String compactionLayer) {
        this.compactionLayer = compactionLayer;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getCompactedAt() {
        return compactedAt;
    }

    public void setCompactedAt(Long compactedAt) {
        this.compactedAt = compactedAt;
    }
}
