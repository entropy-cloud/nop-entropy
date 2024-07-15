package io.nop.core.i18n;

import io.nop.commons.util.StringHelper;

public class I18nHelper {
    public static String getEntityDisplayName(String locale, String entityName) {
        String displayName = I18nMessageManager.instance().getMessage(locale, "entity.label." + entityName, null);
        if (displayName == null) {
            int pos = entityName.lastIndexOf('.');
            if (pos > 0) {
                displayName = I18nMessageManager.instance().getMessage(locale,
                        "entity.label." + entityName.substring(pos + 1), null);
            }
        }
        if (displayName != null) {
            return displayName;// return displayName + '(' + entityName + ')';
        }
        return entityName;
    }

    public static String getFieldDisplayName(String locale, String entityName, String fieldName,
                                             boolean includeOriginal) {
        String fullFieldName = fieldName;
        boolean useFullName = false;
        if (!StringHelper.isEmpty(entityName)) {
            fullFieldName = entityName + '.' + fieldName;
            useFullName = true;
        }

        String displayName = I18nMessageManager.instance().getMessage(locale, "prop.label." + fullFieldName, null);
        if (displayName == null && useFullName) {
            int pos = entityName.lastIndexOf('.');
            if (pos > 0) {
                entityName = entityName.substring(pos + 1);
                fullFieldName = entityName + '.' + fieldName;
                displayName = I18nMessageManager.instance().getMessage(locale, "prop.label." + fullFieldName, null);
            }

            if (displayName == null) {
                if (entityName.indexOf('_') > 0) {
                    // 例如 NopAuthResource_main取到第一部分对应于 NopAuthResource
                    fullFieldName = StringHelper.firstPart(entityName, '_') + '.' + fieldName;
                    displayName = I18nMessageManager.instance().getMessage(locale, "prop.label." + fullFieldName, null);
                }
            }
        }
        if (displayName != null && includeOriginal) {
            return displayName + '(' + StringHelper.lastPart(fieldName, '.') + ')';
        }
        return fieldName;
    }
}
