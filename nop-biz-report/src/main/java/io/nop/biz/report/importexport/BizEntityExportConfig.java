package io.nop.biz.report.importexport;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;

import java.util.List;

@DataBean
public class BizEntityExportConfig implements IBizEntityExecutorConfig{
    private TreeBean filter;
    private List<OrderFieldBean> orderBy;
    private int concurrency;
    private int batchSize;
    private long maxCount;
    private String exportFormat;
    private String exportFileName;
    private String csvFormat;
    private boolean useFieldLabels;
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

    public boolean isUseFieldLabels() {
        return useFieldLabels;
    }

    public void setUseFieldLabels(boolean useFieldLabels) {
        this.useFieldLabels = useFieldLabels;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public List<OrderFieldBean> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderFieldBean> orderBy) {
        this.orderBy = orderBy;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public String getCsvFormat() {
        return csvFormat;
    }

    public void setCsvFormat(String csvFormat) {
        this.csvFormat = csvFormat;
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
