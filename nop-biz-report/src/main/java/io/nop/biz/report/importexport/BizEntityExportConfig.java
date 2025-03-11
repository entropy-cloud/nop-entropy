package io.nop.biz.report.importexport;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;

import java.util.List;

@DataBean
public class BizEntityExportConfig {
    private TreeBean filter;
    private long maxCount;
    private String exportFormat;
    private String exportFileName;
    private List<String> exportFieldNames;
    private List<String> exportFieldLabels;

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = exportFormat;
    }

    public long getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(long maxCount) {
        this.maxCount = maxCount;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public String getExportFileName() {
        return exportFileName;
    }

    public void setExportFileName(String exportFileName) {
        this.exportFileName = exportFileName;
    }

    public List<String> getExportFieldNames() {
        return exportFieldNames;
    }

    public void setExportFieldNames(List<String> exportFieldNames) {
        this.exportFieldNames = exportFieldNames;
    }

    public List<String> getExportFieldLabels() {
        return exportFieldLabels;
    }

    public void setExportFieldLabels(List<String> exportFieldLabels) {
        this.exportFieldLabels = exportFieldLabels;
    }
}
