package io.nop.ai.code_analyzer.code;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;

@DataBean
public class CodeFileInfo {
    @DataBean
    public static class CodeSymbol {
        private String name;
        private int line;
        private int endLineNumber; // optional
        private String signature; // optional

        public CodeSymbol() {
        }

        public CodeSymbol(String name, int line) {
            this.name = name;
            this.line = line;
        }

        public CodeSymbol(String name, int line, int endLineNumber, String signature) {
            this.name = name;
            this.line = line;
            this.endLineNumber = endLineNumber;
            this.signature = signature;
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public int getEndLineNumber() {
            return endLineNumber;
        }

        public void setEndLineNumber(int endLineNumber) {
            this.endLineNumber = endLineNumber;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }
    }

    @DataBean
    public static class CodeSymbols {
        private List<CodeSymbol> functions;
        private List<CodeSymbol> classes;
        private List<CodeSymbol> variables;
        private List<String> imports;
        private Map<String, String> metadata; // for additional info

        public List<CodeSymbol> getFunctions() {
            return functions;
        }

        public void setFunctions(List<CodeSymbol> functions) {
            this.functions = functions;
        }

        public List<CodeSymbol> getClasses() {
            return classes;
        }

        public void setClasses(List<CodeSymbol> classes) {
            this.classes = classes;
        }

        public List<CodeSymbol> getVariables() {
            return variables;
        }

        public void setVariables(List<CodeSymbol> variables) {
            this.variables = variables;
        }

        public List<String> getImports() {
            return imports;
        }

        public void setImports(List<String> imports) {
            this.imports = imports;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    private String filePath;
    private CodeSymbols symbols;
    private long lastModified;
    private String md5;
    private String language; // e.g., "java", "python", etc.
    private int lineCount; // total lines in file

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public CodeSymbols getSymbols() {
        return symbols;
    }

    public void setSymbols(CodeSymbols symbols) {
        this.symbols = symbols;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }
}