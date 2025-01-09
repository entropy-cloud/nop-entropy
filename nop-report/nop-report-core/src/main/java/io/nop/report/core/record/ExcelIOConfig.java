package io.nop.report.core.record;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ExcelIOConfig {
    static final ExcelIOConfig DEFAULT = new ExcelIOConfig();

    private String templatePath;
    private String dataSheetName;
    private String headerSheetName;
    private String trailerSheetName;
    private boolean useSeqCol;
    private int headerRowCount = 1;
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

    public int getHeaderRowCount() {
        return headerRowCount;
    }

    public void setHeaderRowCount(int headerRowCount) {
        this.headerRowCount = headerRowCount;
    }

    public String getDataSheetName() {
        return dataSheetName;
    }

    public void setDataSheetName(String dataSheetName) {
        this.dataSheetName = dataSheetName;
    }

    public String getHeaderSheetName() {
        return headerSheetName;
    }

    public void setHeaderSheetName(String headerSheetName) {
        this.headerSheetName = headerSheetName;
    }

    public String getTrailerSheetName() {
        return trailerSheetName;
    }

    public void setTrailerSheetName(String trailerSheetName) {
        this.trailerSheetName = trailerSheetName;
    }
}