package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._OfficeListModel;

public class OfficeListModel extends _OfficeListModel implements OfficeBlock{
    public OfficeListModel(){

    }
    public OfficeListTemplateModel makeModel() {
        OfficeListTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeListTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }
}
