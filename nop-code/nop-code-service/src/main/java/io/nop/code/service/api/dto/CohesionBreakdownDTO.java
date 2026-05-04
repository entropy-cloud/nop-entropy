package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class CohesionBreakdownDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int extractedCount;
    private int inferredCount;
    private double extractedPercent;
    private double inferredPercent;

    public int getExtractedCount() {
        return extractedCount;
    }

    public void setExtractedCount(int extractedCount) {
        this.extractedCount = extractedCount;
    }

    public int getInferredCount() {
        return inferredCount;
    }

    public void setInferredCount(int inferredCount) {
        this.inferredCount = inferredCount;
    }

    public double getExtractedPercent() {
        return extractedPercent;
    }

    public void setExtractedPercent(double extractedPercent) {
        this.extractedPercent = extractedPercent;
    }

    public double getInferredPercent() {
        return inferredPercent;
    }

    public void setInferredPercent(double inferredPercent) {
        this.inferredPercent = inferredPercent;
    }
}
