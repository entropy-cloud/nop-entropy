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

    public int calcLeafIndex(int startIndex) {
        if (getChildren() == null || getChildren().isEmpty()) {
            setLeafIndex(startIndex);
            return startIndex + 1;
        } else {
            for (RuleDecisionTreeModel child : getChildren()) {
                startIndex = child.calcLeafIndex(startIndex);
            }
            return startIndex;
        }
    }
}