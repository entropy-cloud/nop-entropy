package io.nop.batch.biz.importexport;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class BizEntityImportRequestBean extends BizEntityImportConfig {
    private String filePath;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
