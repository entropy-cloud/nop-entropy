package io.nop.ai.core.api.classifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.stream.Collectors;

@DataBean
public class ClassificationResult {
    private final List<ScoredLabel> scoredLabels;

    @JsonCreator
    public ClassificationResult(@JsonProperty("scoredLabels") List<ScoredLabel> scoredLabels) {
        this.scoredLabels = scoredLabels;
    }

    public List<String> getLabels() {
        return scoredLabels.stream().map(ScoredLabel::getLabel).collect(Collectors.toList());
    }

    public List<ScoredLabel> getScoredLabels() {
        return scoredLabels;
    }
}