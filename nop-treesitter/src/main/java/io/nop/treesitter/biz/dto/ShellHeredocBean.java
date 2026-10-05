package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

/**
 * The heredoc body of a script that consists of nothing but that heredoc, or the
 * reason no supported shape matched.
 *
 * <p>When {@code found} is false the body fields are <b>absent</b>, not empty — "no
 * supported heredoc shape" and "an empty heredoc body" are different answers, and a
 * caller validating text must be able to tell them apart.</p>
 */
@DataBean
public class ShellHeredocBean {

    private boolean found;
    private String delimiter;
    private String body;
    private boolean quotedDelimiter;
    private boolean bodyIsLiteral;
    private String commandName;
    private int bodyStartByte;
    private int bodyEndByte;
    private String reason;

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    /** Whether the delimiter was quoted, which is what disables expansion. */
    public boolean isQuotedDelimiter() {
        return quotedDelimiter;
    }

    public void setQuotedDelimiter(boolean quotedDelimiter) {
        this.quotedDelimiter = quotedDelimiter;
    }

    public boolean isBodyIsLiteral() {
        return bodyIsLiteral;
    }

    public void setBodyIsLiteral(boolean bodyIsLiteral) {
        this.bodyIsLiteral = bodyIsLiteral;
    }

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public int getBodyStartByte() {
        return bodyStartByte;
    }

    public void setBodyStartByte(int bodyStartByte) {
        this.bodyStartByte = bodyStartByte;
    }

    public int getBodyEndByte() {
        return bodyEndByte;
    }

    public void setBodyEndByte(int bodyEndByte) {
        this.bodyEndByte = bodyEndByte;
    }

    /** Why no supported shape matched; present only when {@code found} is false. */
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
