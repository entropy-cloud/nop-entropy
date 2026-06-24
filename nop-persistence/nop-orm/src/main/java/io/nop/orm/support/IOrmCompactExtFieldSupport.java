package io.nop.orm.support;

import io.nop.api.core.ioc.BeanContainer;

import java.util.Map;

public interface IOrmCompactExtFieldSupport {

    String orm_entityName();

    String getExtFlags();

    void setExtFlags(String flags);

    default String getExtValue(String extName) {
        return getExtFieldHelper().getExtValue(this, extName);
    }

    default void setExtValue(String extName, String value) {
        getExtFieldHelper().setExtValue(this, extName, value);
    }

    default Boolean getExtBoolean(String extName) {
        String value = getExtValue(extName);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return !"0".equals(value);
    }

    default void setExtBoolean(String extName, Boolean value) {
        if (value == null) {
            setExtValue(extName, null);
        } else {
            setExtValue(extName, value ? "1" : "0");
        }
    }

    default Map<String, String> getExtValues() {
        return getExtFieldHelper().getExtValues(this);
    }

    default void setExtValues(Map<String, String> values) {
        getExtFieldHelper().setExtValues(this, values);
    }

    default IOrmCompactExtFieldHelper getExtFieldHelper() {
        return BeanContainer.instance().getBeanByType(IOrmCompactExtFieldHelper.class);
    }
}