package io.nop.ai.coder.code;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class CodeFileInfo {
    @DataBean
    public static class CodeSymbols {
        private List<String> funcs;
        private List<String> classes;
        private List<String> vars;
        private List<String> imports;

        public List<String> getFuncs() {
            return funcs;
        }

        public void setFuncs(List<String> funcs) {
            this.funcs = funcs;
        }

        public List<String> getClasses() {
            return classes;
        }

        public void setClasses(List<String> classes) {
            this.classes = classes;
        }

        public List<String> getVars() {
            return vars;
        }

        public void setVars(List<String> vars) {
            this.vars = vars;
        }

        public List<String> getImports() {
            return imports;
        }

        public void setImports(List<String> imports) {
            this.imports = imports;
        }
    }

    private CodeSymbols symbols;
    private long lastModified;
    private String md5;

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
}
