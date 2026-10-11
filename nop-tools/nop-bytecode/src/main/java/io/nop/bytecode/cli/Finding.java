package io.nop.bytecode.cli;

import java.util.Objects;

/**
 * One discovery-channel finding — the bytecode-channel counterpart of nop-lint's
 * {@code Diagnostic(ruleId, severity, message, range, fix)}: the structural fields align
 * (ruleId / severity / message), the location uses the bytecode coordinate system
 * {@code class#method@insnIndex} instead of a source range, and the fix axis does not apply
 * (autofix never enters this channel — source-level concern, roadmap scope anchor 4).
 *
 * <p>Severity is fixed to {@code warning} by the channel (v1 single level; the string
 * vocabulary matches nop-lint's severity strings).
 *
 * @param ruleId      namespaced rule id ({@link RuleIds})
 * @param severity    fixed "warning" in v1
 * @param message     human-readable description
 * @param className   dotted binary name of the analyzed class
 * @param methodName  method name
 * @param insnIndex   instruction index within the method
 * @param ref         dereference target description ([array] / Owner#field / owner.method)
 */
public record Finding(String ruleId, String severity, String message,
                      String className, String methodName, int insnIndex, String ref) {

    public static final String SEVERITY_WARNING = "warning";

    public Finding {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(ref, "ref");
    }

    public String location() {
        return className + "#" + methodName + "@" + insnIndex;
    }
}
