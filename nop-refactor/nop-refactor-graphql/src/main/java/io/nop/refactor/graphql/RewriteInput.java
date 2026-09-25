package io.nop.refactor.graphql;

import java.util.List;

/**
 * The RewriteInput contract (baseline §三, v1): a ruleset VFS prefix (the
 * verified safe surface — template rendering, fail-closed matrix, resource
 * gates) plus the target file set (explicit files or directories;
 * directories expand recursively). "pattern 直给" is a v1 Non-Goal
 * (adjudication: bare-pattern edit-plan assembly is a new engine face, see
 * the biz model javadoc).
 */
public class RewriteInput {

    private String rulesetPrefix;
    private List<String> paths;

    public String getRulesetPrefix() {
        return rulesetPrefix;
    }

    public void setRulesetPrefix(String rulesetPrefix) {
        this.rulesetPrefix = rulesetPrefix;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }
}
