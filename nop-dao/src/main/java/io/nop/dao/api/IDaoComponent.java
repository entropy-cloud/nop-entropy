package io.nop.dao.api;

public interface IDaoComponent {
    Object orm_propValueByName(String propName);

    void orm_propValueByName(String propName, Object value);
}
