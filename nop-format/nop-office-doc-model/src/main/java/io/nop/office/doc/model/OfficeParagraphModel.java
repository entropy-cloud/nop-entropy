package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._OfficeParagraphModel;

public class OfficeParagraphModel extends _OfficeParagraphModel implements OfficeBlock {
    public OfficeParagraphTemplateModel makeModel() {
        OfficeParagraphTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeParagraphTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }
}
