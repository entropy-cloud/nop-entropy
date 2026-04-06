package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._OfficeRunModel;

public class OfficeRunModel extends _OfficeRunModel {
    public OfficeRunTemplateModel makeModel() {
        OfficeRunTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeRunTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }
}
