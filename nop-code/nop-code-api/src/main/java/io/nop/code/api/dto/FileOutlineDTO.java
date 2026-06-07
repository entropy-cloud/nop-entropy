package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class FileOutlineDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filePath;
    private String packageName;
    private List<String> imports;
    private int lineCount;
    private List<SymbolInfoDTO> types;

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

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public List<SymbolInfoDTO> getTypes() {
        return types;
    }

    public void setTypes(List<SymbolInfoDTO> types) {
        this.types = types;
    }
}
