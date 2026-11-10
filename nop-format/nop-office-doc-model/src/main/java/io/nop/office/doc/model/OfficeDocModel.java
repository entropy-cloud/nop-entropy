package io.nop.office.doc.model;

import io.nop.commons.collections.KeyedList;
import io.nop.office.doc.model._gen._OfficeDocModel;
import io.nop.office.model.OfficeStyle;

public class OfficeDocModel extends _OfficeDocModel {
    public OfficeDocTemplateModel makeModel() {
        OfficeDocTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeDocTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }

    /**
     * Named style lookup by id. The styles container dispatches five concrete style tags
     * (run/paragraph/cell/table/list), so the generated base only exposes the list
     * accessors — the singular helper lives here.
     */
    public OfficeStyle getStyle(String styleId) {
        if (getStyles() == null || styleId == null)
            return null;
        for (OfficeStyle style : getStyles()) {
            if (styleId.equals(style.getId()))
                return style;
        }
        return null;
    }

    /**
     * Append a named style, lazily replacing the frozen empty default with a mutable list.
     */
    public void addStyle(OfficeStyle style) {
        KeyedList<OfficeStyle> styles = (KeyedList<OfficeStyle>) getStyles();
        if (styles.frozen()) {
            styles = new KeyedList<>(OfficeStyle::getId);
            setStyles(styles);
        }
        styles.add(style);
    }
}
