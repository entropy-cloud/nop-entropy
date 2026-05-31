package io.nop.code.service.api.dto;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class CodeSearchResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filePath;
    private String matchedSymbolName;
    private String matchedQualifiedName;
    private String matchType;
    private int line;
    private String context;
    private double score;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getMatchedSymbolName() {
        return matchedSymbolName;
    }

    public void setMatchedSymbolName(String matchedSymbolName) {
        this.matchedSymbolName = matchedSymbolName;
    }

    public String getMatchedQualifiedName() {
        return matchedQualifiedName;
    }

    public void setMatchedQualifiedName(String matchedQualifiedName) {
        this.matchedQualifiedName = matchedQualifiedName;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
