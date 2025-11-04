package io.nop.core.reflect.bean;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FieldSelectionHelper {
    public static Object pluckSelected(Object src, FieldSelectionBean selection) {
        if (src == null)
            return null;

        if (!selection.hasField())
            return src;

        if (src instanceof Collection) {
            Collection<?> col = (Collection<?>) src;

            List<Object> ret = new ArrayList<>(col.size());
            for (Object item : col) {
                ret.add(pluckSelected(item, selection));
            }
            return ret;
        }

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(selection.getFieldCount());
        for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
            String name = entry.getKey();
            FieldSelectionBean sel = entry.getValue();
            String propName = sel.getName();
            if (propName == null)
                propName = name;

            Object value = BeanTool.getProperty(src, propName);
            value = pluckSelected(value, sel);
            ret.put(name, value);
        }
        return ret;
    }
}