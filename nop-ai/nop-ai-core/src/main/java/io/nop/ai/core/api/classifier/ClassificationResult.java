package io.nop.ai.core.api.classifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.stream.Collectors;

@DataBean
/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），分类器 SPI 契约族的 value 类型（与 {@code ITextClassifier}/{@code IDocumentClassifier}
 * 的 reserved 裁定一致）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
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