package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._OfficeDocPageModel;

public class OfficeDocPageModel extends _OfficeDocPageModel {
    public OfficeDocPageTemplateModel makeModel() {
        OfficeDocPageTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeDocPageTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }
}
