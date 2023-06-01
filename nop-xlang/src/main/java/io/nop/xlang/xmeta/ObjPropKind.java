package io.nop.xlang.xmeta;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Option;

@Locale("zh-CN")
public enum ObjPropKind {

    @Option("default")
    @Label("缺省类型")
    DEFAULT("default"),

    @Option("to-one")
    @Label("关联对象")
    TO_ONE("to-one"),

    @Option("to-many")
    @Label("关联集合")
    TO_MANY("to-many");

    private final String text;

    ObjPropKind(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }
}
