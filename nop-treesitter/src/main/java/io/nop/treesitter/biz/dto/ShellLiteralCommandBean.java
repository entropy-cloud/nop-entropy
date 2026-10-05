package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * One command found by the literal harvest.
 *
 * <p>{@code partial} is the load-bearing field: it means at least one argument could
 * not be read as a literal, so {@code arguments} is known to be a subset of the real
 * argv. The whole result is incomplete by construction and may never be used to
 * conclude that a script is safe — this bean carries {@code partial} per row so that
 * fact travels with the data instead of living in a method name.</p>
 */
@DataBean
public class ShellLiteralCommandBean {

    private String name;
    private List<String> arguments;
    private boolean partial;
    private int byteOffset;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public int getByteOffset() {
        return byteOffset;
    }

    public void setByteOffset(int byteOffset) {
        this.byteOffset = byteOffset;
    }
}
