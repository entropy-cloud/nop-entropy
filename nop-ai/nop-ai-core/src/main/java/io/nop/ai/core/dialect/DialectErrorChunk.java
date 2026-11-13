package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;

/**
 * Plan 2519 (裁定⑧/C3-032): typed in-band error chunk. Provider error events
 * arriving inside a 200-OK SSE stream (e.g. Anthropic {@code type=error})
 * were previously rendered as a {@code [ERROR] ...} text DELTA — polluting
 * the assistant content. The typed chunk carries the error explicitly.
 *
 * <p><b>Carrier constraint (条件申报② NOT triggered)</b>: {@code StreamItemType}
 * / {@code ChatStreamChunk} live in {@code nop-ai-api}, which this batch must
 * not touch — the type is carried as a module-internal chunk SUBTYPE
 * (design 裁定⑧ "error itemType 或等价字段"). External consumers that only know
 * the base class still observe a well-formed terminal chunk
 * ({@code phase=DONE, finishReason="error"}) with the message in {@code delta};
 * module-internal consumers branch on {@code instanceof} for the typed signal.
 */
public class DialectErrorChunk extends ChatStreamChunk {

    private String errorType;
    private String errorMessage;

    public DialectErrorChunk() {
        setPhase(StreamItemPhase.DONE);
        setFinishReason("error");
    }

    public DialectErrorChunk(String errorType, String errorMessage) {
        this();
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "DialectErrorChunk{errorType=" + errorType
                + ", errorMessage=" + errorMessage + "}";
    }
}
