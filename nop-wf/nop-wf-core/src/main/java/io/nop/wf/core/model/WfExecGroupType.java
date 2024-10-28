package io.nop.wf.core.model;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

@Locale("zh-CN")
public enum WfExecGroupType {
    @Option("none")
    NONE("none"),

    @Option("or-group")
    @Description("只要有一个步骤完成，整个组就完成")
    OR_GROUP("or-group"),

    @Option("and-group")
    @Description("所有步骤并行完成，且只有所有步骤都完成，整个组才完成")
    AND_GROUP("and-group"),

    @Option("seq-group")
    @Description("所有步骤顺序完成，且只有所有步骤都完成，整个组才完成")
    SEQ_GROUP("seq-group"),

    @Option("vote-group")
    @Description("所有步骤并行完成，且所有成功步骤的权重和超过指定最低权重，则整个组才完成")
    VOTE_GROUP("vote-group");

    private String text;

    WfExecGroupType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    private static final Map<String, WfExecGroupType> textMap = new HashMap<>();

    static {
        for (WfExecGroupType value : WfExecGroupType.values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static WfExecGroupType fromText(String text) {
        return textMap.get(text);
    }
}
