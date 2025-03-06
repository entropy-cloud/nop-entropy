package io.nop.ai.core.api.classifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ScoredLabel {
    private final String label;
    private final double score;

    @JsonCreator
    public ScoredLabel(@JsonProperty("label") String label,
                       @JsonProperty("score") double score) {
        this.label = label;
        this.score = score;
    }

    public String getLabel() {
        return label;
    }

    public double getScore() {
        return score;
    }
}
