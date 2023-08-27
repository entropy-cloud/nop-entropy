package io.nop.stream.cep.model;

public enum AfterMatchSkipStrategyKind {
    /**
     * 每个成功的匹配都会被输出
     */
    NO_SKIP,

    /**
     * 丢弃以相同事件开始的所有部分匹配
     */
    SKIP_TO_NEXT,

    /**
     * 丢弃起始在这个匹配的开始和结束之间的所有部分匹配
     */
    SKIP_PAST_LAST_EVENT,

    /**
     * 丢弃起始在这个匹配的开始和第一个出现的名称为PatternName事件之间的所有部分匹配
     */
    SKIP_TO_FIRST,

    /**
     * 丢弃起始在这个匹配的开始和最后一个出现的名称为PatternName事件之间的所有部分匹配。
     */
    SKIP_TO_LAST;
}
