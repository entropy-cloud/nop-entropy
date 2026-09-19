package io.nop.rg.core.search;

/**
 * 单个命中：文件内绝对字节偏移 + 命中长度（当前策略均为字面量模式，长度=模式长度）。
 */
public class MatchResult {
    private final long offset;
    private final int length;

    public MatchResult(long offset, int length) {
        this.offset = offset;
        this.length = length;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }
}
