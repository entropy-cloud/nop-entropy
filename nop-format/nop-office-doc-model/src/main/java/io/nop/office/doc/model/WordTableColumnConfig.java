package io.nop.office.doc.model;

import io.nop.core.model.table.IColumnConfig;
import io.nop.office.doc.model._gen._WordTableColumnConfig;

public class WordTableColumnConfig extends _WordTableColumnConfig implements IColumnConfig {
    public WordTableColumnTemplateModel makeModel() {
        WordTableColumnTemplateModel model = getModel();
        if (model == null) {
            model = new WordTableColumnTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }

    public WordTableColumnConfig cloneInstance() {
        WordTableColumnConfig col = new WordTableColumnConfig();
        copyExtPropsTo(col);
        col.setLocation(getLocation());
        col.setWidth(getWidth());
        col.setStyleId(getStyleId());
        col.setHidden(isHidden());
        col.setModel(getModel());
        return col;
    }
}
