package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 录制 wrapper：捕获 IEvalOutput 完整 API 调用序列（comment/value/text + loc + value），
 * 用于副作用层比对。不使用会丢事件信息的裸 StringBuilderEvalOutput（其 comment() 为 no-op）。
 */
public final class RecordingEvalOutput implements IEvalOutput {
    private final List<RecordedOutputCall> calls = new ArrayList<>();

    @Override
    public void comment(String comment) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.COMMENT, null, comment));
    }

    @Override
    public void value(SourceLocation loc, Object value) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.VALUE, loc, value));
    }

    @Override
    public void text(SourceLocation loc, String text) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.TEXT, loc, text));
    }

    public List<RecordedOutputCall> getCalls() {
        return Collections.unmodifiableList(calls);
    }
}
