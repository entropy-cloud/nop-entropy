package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * The result of the proven split, across the GraphQL boundary.
 *
 * <p>Two mutually exclusive shapes in one bean, discriminated by {@code proven}: when
 * it is true, {@code commands} is the proven sequence; when it is false, {@code reason}
 * says why the script could not be proven and {@code commands} is <b>absent</b> —
 * not empty. An empty list would read as "this script runs no commands", which is the
 * single most dangerous thing this API could say.</p>
 *
 * <p>{@code reason} is a domain fact, not an error channel: the request itself
 * succeeded. Failures use the framework's {@code ApiResponse} error mechanism with
 * {@code nop.err.treesitter.*} codes.</p>
 */
@DataBean
public class ShellProvenSplitBean {

    private boolean proven;
    private List<ShellProvenCommandBean> commands;
    private String reason;
    private String nodeType;
    private int byteOffset;
    private String detail;

    public boolean isProven() {
        return proven;
    }

    public void setProven(boolean proven) {
        this.proven = proven;
    }

    /** Present only when {@link #isProven()} is true. */
    public List<ShellProvenCommandBean> getCommands() {
        return commands;
    }

    public void setCommands(List<ShellProvenCommandBean> commands) {
        this.commands = commands;
    }

    /** The closed refusal reason; present only when {@link #isProven()} is false. */
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public int getByteOffset() {
        return byteOffset;
    }

    public void setByteOffset(int byteOffset) {
        this.byteOffset = byteOffset;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
