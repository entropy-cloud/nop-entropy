/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.imp.block;

import io.nop.excel.imp.model.IFieldContainer;

import java.util.ArrayList;
import java.util.List;

public class FieldsBlock extends BlockBase {
    private List<BlockBase> children = new ArrayList<>();

    private int minChildRowIndex;
    private int maxChildRowIndex;

    // children将会重复几遍
    private int childrenRepeatCount;

    private IFieldContainer fieldContainer;

    private boolean list;
    private boolean cardList;

    public boolean isList() {
        return list;
    }

    public boolean isCardList() {
        return cardList;
    }

    public void setList(boolean list) {
        this.list = list;
    }

    public IFieldContainer getFieldContainer() {
        return fieldContainer;
    }

    public void setFieldContainer(IFieldContainer fieldContainer) {
        this.fieldContainer = fieldContainer;
    }

    public void addChild(BlockBase block) {
        children.add(block);
    }

    public List<BlockBase> getChildren() {
        return children;
    }

    public void setChildren(List<BlockBase> children) {
        this.children = children;
    }

    @Override
    public void init() {
        int min = 0, max = 0;
        for (BlockBase block : children) {
            block.init();
            min = Math.min(min, block.getRowIndex());
            max = Math.max(max, block.getMaxRowIndex());
        }
        this.minChildRowIndex = min;
        this.maxChildRowIndex = max;

        if (list) {
            cardList = !allChildSameLine();
        }
    }

    public int getMinChildRowIndex() {
        return minChildRowIndex;
    }

    public void setMinChildRowIndex(int minChildRowIndex) {
        this.minChildRowIndex = minChildRowIndex;
    }

    public int getMaxChildRowIndex() {
        return maxChildRowIndex;
    }

    public void setMaxChildRowIndex(int maxChildRowIndex) {
        this.maxChildRowIndex = maxChildRowIndex;
    }

    public int getChildrenRepeatCount() {
        return childrenRepeatCount;
    }

    public void setChildrenRepeatCount(int childrenRepeatCount) {
        this.childrenRepeatCount = childrenRepeatCount;
    }

    public void setCardList(boolean cardList) {
        this.cardList = cardList;
    }

    private boolean allChildSameLine() {
        int index = -1;
        for (BlockBase block : children) {
            if (index == -1) {
                index = block.getRowIndex();
            } else if (index != block.getRowIndex()) {
                return false;
            }
        }
        return true;
    }
}