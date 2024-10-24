package io.nop.record.codec.impl;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordTypeMeta;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DefaultFieldCodecContext implements IFieldCodecContext {
    private final IEvalScope scope;

    // 用于记录当前处理的字段的路径，发生错误时可以用于错误定位
    private final List<String> fieldPaths = new ArrayList<>();

    private final Function<String, RecordTypeMeta> typeProvider;

    public DefaultFieldCodecContext(IEvalScope scope, Function<String, RecordTypeMeta> typeProvider) {
        this.scope = scope;
        this.typeProvider = typeProvider;
    }

    public DefaultFieldCodecContext(Function<String, RecordTypeMeta> typeProvider) {
        this(XLang.newEvalScope(), typeProvider);
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public String getFieldPath() {
        return StringHelper.join(fieldPaths, ".");
    }

    @Override
    public void enterField(String name) {
        fieldPaths.add(name);
    }

    @Override
    public void leaveField(String name) {
        fieldPaths.remove(fieldPaths.size() - 1);
    }

    @Override
    public RecordTypeMeta getType(String name) {
        return typeProvider.apply(name);
    }
}
