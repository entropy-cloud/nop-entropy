package io.nop.dataset.rowmapper;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;

public class DetachRowMapper implements IRowMapper<IDataRow> {
    @Override
    public IDataRow mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        return row.toDetachedDataRow();
    }
}