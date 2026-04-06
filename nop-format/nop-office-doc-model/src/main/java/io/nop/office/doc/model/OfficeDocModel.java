package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._OfficeDocModel;

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
}
