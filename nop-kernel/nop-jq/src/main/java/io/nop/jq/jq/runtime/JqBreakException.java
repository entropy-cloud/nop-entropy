package io.nop.jq.jq.runtime;

/**
 * Exception used for label/break control flow in jq execution.
 * Not a real error - used as a control flow mechanism.
 */
public class JqBreakException extends RuntimeException {
    private final String labelName;
    private final JqValue value;

    public JqBreakException(String labelName, JqValue value) {
        super("break $" + labelName);
        this.labelName = labelName;
        this.value = value;
    }

    public String labelName() { return labelName; }
    public JqValue value() { return value; }
}
