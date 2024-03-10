/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.block;

import io.nop.core.model.table.ICellView;
import io.nop.excel.imp.ITableDataEventListener;
import io.nop.excel.imp.LabelData;
import io.nop.excel.imp.model.IFieldContainer;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;

import java.util.ArrayList;
import java.util.List;

public class TableBlockCollector implements ITableDataEventListener {
    private FieldsBlock rootBlock;

    private List<FieldsBlock> blockStack;

    @Override
    public void beginSheet(String sheetName, ImportSheetModel sheetModel) {
        rootBlock = new FieldsBlock();
        blockStack = new ArrayList<>();
        blockStack.add(rootBlock);
    }

    public FieldsBlock getRootBlock() {
        return rootBlock;
    }

    @Override
    public void endSheet(ImportSheetModel sheetModel) {
        rootBlock.init();
    }

    @Override
    public void beginList(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                          IFieldContainer fieldModel, boolean cardList) {
        FieldsBlock block = pushFieldsBlock(rowIndex, colIndex, maxRowIndex, maxColIndex, fieldModel);
        block.setList(true);
    }

    @Override
    public void endList(int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
        blockStack.remove(blockStack.size() - 1);
    }

    @Override
    public void beginObject(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
        pushFieldsBlock(rowIndex, colIndex, maxRowIndex, maxColIndex, fieldModel);
    }

    @Override
    public void endObject(IFieldContainer fieldModel) {
        blockStack.remove(blockStack.size() - 1);
    }

    private FieldsBlock pushFieldsBlock(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
        FieldsBlock block = new FieldsBlock();
        block.setRowIndex(rowIndex);
        block.setColIndex(colIndex);
        block.setMaxRowIndex(maxRowIndex);
        block.setMaxColIndex(maxColIndex);
        block.setFieldContainer(fieldModel);

        FieldsBlock parent = blockStack.get(blockStack.size() - 1);
        parent.addChild(block);
        blockStack.add(block);
        return block;
    }

    @Override
    public void simpleField(int rowIndex, int colIndex, ICellView cell, LabelData labelData) {
        FieldBlock block = new FieldBlock();
        block.setRowIndex(rowIndex);
        block.setMaxRowIndex(rowIndex + (cell == null ? 0 : cell.getMergeDown()));
        block.setColIndex(colIndex);
        block.setMaxColIndex(colIndex + (cell == null ? 0 : cell.getMergeAcross()));
        block.setFieldModel(labelData.getField());

        FieldsBlock parent = blockStack.get(blockStack.size() - 1);
        parent.addChild(block);
    }
}