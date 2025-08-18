package io.nop.ai.tools.sequential_thinking.model;

import io.nop.api.core.annotations.data.DataBean;
import java.util.List;

@DataBean
public class ProcessThoughtRequest {
    private String thought;
    private int thoughtNumber;
    private int totalThoughts;
    private boolean nextThoughtNeeded;
    private String stage;
    private List<String> tags;
    private List<String> axiomsUsed;
    private List<String> assumptionsChallenged;

    public ProcessThoughtRequest() {
    }

    // Getter和Setter方法
    public String getThought() {
        return thought;
    }

    public void setThought(String thought) {
        this.thought = thought;
    }

    public int getThoughtNumber() {
        return thoughtNumber;
    }

    public void setThoughtNumber(int thoughtNumber) {
        this.thoughtNumber = thoughtNumber;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    public void setTotalThoughts(int totalThoughts) {
        this.totalThoughts = totalThoughts;
    }

    public boolean isNextThoughtNeeded() {
        return nextThoughtNeeded;
    }

    public void setNextThoughtNeeded(boolean nextThoughtNeeded) {
        this.nextThoughtNeeded = nextThoughtNeeded;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getAxiomsUsed() {
        return axiomsUsed;
    }

    public void setAxiomsUsed(List<String> axiomsUsed) {
        this.axiomsUsed = axiomsUsed;
    }

    public List<String> getAssumptionsChallenged() {
        return assumptionsChallenged;
    }

    public void setAssumptionsChallenged(List<String> assumptionsChallenged) {
        this.assumptionsChallenged = assumptionsChallenged;
    }
}