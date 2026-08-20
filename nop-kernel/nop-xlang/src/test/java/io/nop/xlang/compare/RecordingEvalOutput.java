package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 录制 wrapper：捕获 IEvalOutput 完整 API 调用序列（comment/value/text + loc + value），
 * 用于副作用层比对。不使用会丢事件信息的裸 StringBuilderEvalOutput（其 comment() 为 no-op）。
 *
 * <p>I4 §8 裁定：扩展实现 {@link IXNodeHandler}——GenNode 族把 out cast 为 IXNodeHandler
 * 直发节点事件，录制 wrapper 记录 beginNode/endNode/simpleNode（attrs 取平值映射），
 * 节点通路对解释器/java 两列对称可达（无 CCE）。endDoc 为收集 API，录制事件后返回 null
 * （本 wrapper 是事件汇而非收集器）。
 */
public final class RecordingEvalOutput implements IXNodeHandler {
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

    @Override
    public void beginDoc(String encoding, String docType, String instruction) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.BEGIN_DOC, null, docType));
    }

    @Override
    public void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.SIMPLE_NODE, loc, tagName, plainAttrs(attrs)));
    }

    @Override
    public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.BEGIN_NODE, loc, tagName, plainAttrs(attrs)));
    }

    @Override
    public void endNode(String tagName) {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.END_NODE, null, tagName));
    }

    @Override
    public XNode endDoc() {
        calls.add(new RecordedOutputCall(RecordedOutputCall.Op.END_DOC, null, null));
        return null;
    }

    private static Map<String, Object> plainAttrs(Map<String, ValueWithLocation> attrs) {
        if (attrs == null || attrs.isEmpty())
            return Collections.emptyMap();
        Map<String, Object> ret = new LinkedHashMap<>();
        for (Map.Entry<String, ValueWithLocation> entry : attrs.entrySet())
            ret.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().getValue());
        return ret;
    }

    public List<RecordedOutputCall> getCalls() {
        return Collections.unmodifiableList(calls);
    }
}
