package io.nop.biz.report.importexport;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.query.QueryBean;

import java.util.List;

@DataBean
public class BizEntityExportConfig {
    private QueryBean queryBean;
    private String exportFormat;
    private boolean exportAll;
    private String exportFileName;
    private List<String> keyPropNames;

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = exportFormat;
    }

    public boolean isExportAll() {
        return exportAll;
    }

    public void setExportAll(boolean exportAll) {
        this.exportAll = exportAll;
    }

    public QueryBean getQueryBean() {
        return queryBean;
    }

    public void setQueryBean(QueryBean queryBean) {
        this.queryBean = queryBean;
    }

    public String getExportFileName() {
        return exportFileName;
    }

    public void setExportFileName(String exportFileName) {
        this.exportFileName = exportFileName;
    }

    public List<String> getKeyPropNames() {
        return keyPropNames;
    }

    public void setKeyPropNames(List<String> keyPropNames) {
        this.keyPropNames = keyPropNames;
    }
}
