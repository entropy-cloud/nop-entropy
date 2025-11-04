package io.nop.api.core.util.progress;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ProgressState {
    private final String progressMessage;
    private final long progressTotal;
    private final long currentProgress;

    public ProgressState(@Name("progressMessage") String progressMessage,
                         @Name("currentProgress") long currentProgress,
                         @Name("progressTotal") long progressTotal) {
        this.progressMessage = progressMessage;
        this.progressTotal = progressTotal;
        this.currentProgress = currentProgress;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public long getProgressTotal() {
        return progressTotal;
    }

    public long getCurrentProgress() {
        return currentProgress;
    }
}