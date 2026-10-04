package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * Everything the shell analysis layer found about a script.
 *
 * <p>{@code proofStatus} is a value, not an absence. A summary that said "not proven"
 * and a caller that received the original script text with no analysis would look
 * alike from outside, and the second would be read as the first — as if the script
 * had been analysed and found acceptable. Callers branch on this field.</p>
 */
@DataBean
public class ShellAnalysisBean {

    private String proofStatus;
    private String unprovenReason;
    private List<String> commandNames;
    private List<String> connectors;
    private boolean hasDynamicConstructs;
    private List<String> dynamicConstructKinds;
    private List<ShellPathBean> paths;
    private List<ShellDynamicConstructBean> dynamicConstructs;

    /**
     * {@code PROVEN} or {@code NOT_PROVEN}. There is no third "could not analyse"
     * state: a tree that cannot be produced at all makes the parse raise, so a
     * summary that reaches a caller has always been computed.
     */
    public String getProofStatus() {
        return proofStatus;
    }

    public void setProofStatus(String proofStatus) {
        this.proofStatus = proofStatus;
    }

    /** The closed refusal reason when the status is {@code NOT_PROVEN}, else empty. */
    public String getUnprovenReason() {
        return unprovenReason;
    }

    public void setUnprovenReason(String unprovenReason) {
        this.unprovenReason = unprovenReason;
    }

    /** Command names in source order. For an unproven script, check the status first. */
    public List<String> getCommandNames() {
        return commandNames;
    }

    public void setCommandNames(List<String> commandNames) {
        this.commandNames = commandNames;
    }

    public List<String> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<String> connectors) {
        this.connectors = connectors;
    }

    public boolean isHasDynamicConstructs() {
        return hasDynamicConstructs;
    }

    public void setHasDynamicConstructs(boolean hasDynamicConstructs) {
        this.hasDynamicConstructs = hasDynamicConstructs;
    }

    public List<String> getDynamicConstructKinds() {
        return dynamicConstructKinds;
    }

    public void setDynamicConstructKinds(List<String> dynamicConstructKinds) {
        this.dynamicConstructKinds = dynamicConstructKinds;
    }

    public List<ShellPathBean> getPaths() {
        return paths;
    }

    public void setPaths(List<ShellPathBean> paths) {
        this.paths = paths;
    }

    public List<ShellDynamicConstructBean> getDynamicConstructs() {
        return dynamicConstructs;
    }

    public void setDynamicConstructs(List<ShellDynamicConstructBean> dynamicConstructs) {
        this.dynamicConstructs = dynamicConstructs;
    }
}
