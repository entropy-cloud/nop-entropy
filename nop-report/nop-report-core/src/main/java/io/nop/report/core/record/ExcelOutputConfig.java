package io.nop.report.core.record;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ExcelOutputConfig {
    private String templatePath;
    private boolean useSeqCol;
    private int maxCountPerSheet = 20_0000;

    public boolean isUseSeqCol() {
        return useSeqCol;
    }

    public void setUseSeqCol(boolean useSeqCol) {
        this.useSeqCol = useSeqCol;
    }

    public int getMaxCountPerSheet() {
        return maxCountPerSheet;
    }

    public void setMaxCountPerSheet(int maxCountPerSheet) {
        this.maxCountPerSheet = maxCountPerSheet;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }
}