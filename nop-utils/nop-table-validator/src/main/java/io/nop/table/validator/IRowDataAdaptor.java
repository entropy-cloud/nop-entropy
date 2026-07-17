package io.nop.table.validator;

public interface IRowDataAdaptor<T> {
    Object getValue(T row, int columnIndex);
}
