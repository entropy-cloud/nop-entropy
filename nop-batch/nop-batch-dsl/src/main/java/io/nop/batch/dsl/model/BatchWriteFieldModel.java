package io.nop.batch.dsl.model;

import io.nop.batch.dsl.model._gen._BatchWriteFieldModel;
import io.nop.dataset.IDataFieldMeta;

public class BatchWriteFieldModel extends _BatchWriteFieldModel implements IDataFieldMeta {
    public BatchWriteFieldModel() {

    }

    @Override
    public String getFieldName() {
        return getName();
    }

    @Override
    public String getSourceFieldName() {
        return getFrom();
    }

    @Override
    public String getFieldOwnerEntityName() {
        return null;
    }
}