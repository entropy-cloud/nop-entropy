package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._OfficeImageModel;

public class OfficeImageModel extends _OfficeImageModel implements OfficeBlock{
    public OfficeImageModel(){

    }
    public OfficeImageTemplateModel makeModel() {
        OfficeImageTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeImageTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }
}
