package io.nop.orm.support;

import java.util.Map;

public interface IOrmCompactExtFieldHelper {

    String getExtValue(IOrmCompactExtFieldSupport entity, String extName);

    void setExtValue(IOrmCompactExtFieldSupport entity, String extName, String value);

    Map<String, String> getExtValues(IOrmCompactExtFieldSupport entity);

    void setExtValues(IOrmCompactExtFieldSupport entity, Map<String, String> values);
}