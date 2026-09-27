package io.nop.code.api.dto;

import io.nop.api.core.annotations.data.DataBean;
import java.io.Serializable;

/**
 * Result of NopCodeIndex__triggerRebuildFromCommit.
 */
@DataBean
public class RebuildFromCommitResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private int changedCount;
    private boolean debounced;
    private boolean skippedNoChanges;
    private String statusMessage;

    public int getChangedCount() { return changedCount; }
    public void setChangedCount(int changedCount) { this.changedCount = changedCount; }
    public boolean isDebounced() { return debounced; }
    public void setDebounced(boolean debounced) { this.debounced = debounced; }
    public boolean isSkippedNoChanges() { return skippedNoChanges; }
    public void setSkippedNoChanges(boolean skippedNoChanges) { this.skippedNoChanges = skippedNoChanges; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
}
