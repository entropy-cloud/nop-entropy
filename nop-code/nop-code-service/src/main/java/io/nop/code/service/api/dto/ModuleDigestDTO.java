package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.List;

@DataBean
public class ModuleDigestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filePath;
    private String packageName;
    private List<SymbolInfoDTO> symbols;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public List<SymbolInfoDTO> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<SymbolInfoDTO> symbols) {
        this.symbols = symbols;
    }
}
