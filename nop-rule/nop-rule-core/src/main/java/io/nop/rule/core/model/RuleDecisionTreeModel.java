package io.nop.rule.core.model;

import io.nop.rule.core.model._gen._RuleDecisionTreeModel;

import java.util.function.Consumer;

public class RuleDecisionTreeModel extends _RuleDecisionTreeModel {

    public RuleDecisionTreeModel() {

    }

    public void forEachLeaf(Consumer<RuleDecisionTreeModel> consumer) {
        if (getChildren() == null || getChildren().isEmpty()) {
            consumer.accept(this);
        } else {
            for (RuleDecisionTreeModel child : getChildren()) {
                child.forEachLeaf(consumer);
            }
        }
    }
}