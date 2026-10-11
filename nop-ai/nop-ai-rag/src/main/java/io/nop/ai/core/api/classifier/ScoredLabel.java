package io.nop.ai.core.api.classifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），分类器 SPI 契约族的 value 类型（与 {@code ClassificationResult} 的 reserved 裁定
 * 一致）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
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
