package io.nop.record.model;

import io.nop.record.model._gen._RecordTemplateFieldModel;

public class RecordTemplateFieldModel extends _RecordTemplateFieldModel {
    private RecordTemplateModel templateModel;

    public RecordTemplateFieldModel() {

    }


    public String getTemplateName() {
        return templateModel == null ? null : templateModel.getName();
    }

    public RecordTemplateModel getTemplateModel() {
        return templateModel;
    }

    public void setTemplateModel(RecordTemplateModel templateModel) {
        this.templateModel = templateModel;
    }
}
