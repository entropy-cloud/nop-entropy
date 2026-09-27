package io.nop.code.api.dto;

import io.nop.api.core.annotations.data.DataBean;
import java.io.Serializable;
import java.util.List;

/**
 * A machine-executable exploration question derived from graph signals
 * (graph-discovery-and-export-design.md §3.2).
 *
 * When no signal exists the generator returns a single item with type="no_signal",
 * question=null (explicit exception to the empty-list convention).
 */
@DataBean
public class ExplorationQuestionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String type;
    private String question;
    private String why;
    private List<String> targetSymbolIds;
    private String suggestedQuery;
    private int priority;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }
    public List<String> getTargetSymbolIds() { return targetSymbolIds; }
    public void setTargetSymbolIds(List<String> targetSymbolIds) { this.targetSymbolIds = targetSymbolIds; }
    public String getSuggestedQuery() { return suggestedQuery; }
    public void setSuggestedQuery(String suggestedQuery) { this.suggestedQuery = suggestedQuery; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
