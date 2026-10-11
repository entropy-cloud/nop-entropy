package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * One command of a proven command sequence, as it crosses the GraphQL boundary.
 *
 * <p>{@code separator} is the connector that joins this command to the next one in
 * the sequence, so a caller can reconstruct the control flow rather than infer it.</p>
 */
@DataBean
public class ShellProvenCommandBean {

    private String name;
    private List<String> arguments;
    private String separator;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }
}
