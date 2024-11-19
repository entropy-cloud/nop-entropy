package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.record.model._gen._RecordDefinitions;

import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_OBJ_TYPE;

public class RecordDefinitions extends _RecordDefinitions implements INeedInit {
    public RecordDefinitions() {

    }

    public RecordTypeMeta resolveType(String typeName) {
        RecordTypeMeta type = getType(typeName);
        if (type == null)
            throw new NopException(ERR_RECORD_UNKNOWN_OBJ_TYPE)
                    .param(ARG_TYPE_NAME, typeName);
        return type;
    }

    @Override
    public void init() {
        for (RecordTypeMeta type : getTypes()) {
            type.init(this);
        }
    }
}
