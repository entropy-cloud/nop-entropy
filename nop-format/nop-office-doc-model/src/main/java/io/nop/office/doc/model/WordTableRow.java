package io.nop.office.doc.model;

import io.nop.core.model.table.ICell;
import io.nop.office.doc.model._gen._WordTableRow;

import java.util.ArrayList;

public class WordTableRow extends _WordTableRow {
    public WordTableRow() {
        setCells(new ArrayList<>());
    }

    public WordTableRowTemplateModel makeModel() {
        WordTableRowTemplateModel model = getModel();
        if (model == null) {
            model = new WordTableRowTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }

    @Override
    public ICell makeCell(int colIndex) {
        ICell cell = getCell(colIndex);
        if (cell == null) {
            WordTableCell wordCell = new WordTableCell();
            internalSetCell(colIndex, wordCell);
            cell = wordCell;
        }
        return cell;
    }
}
