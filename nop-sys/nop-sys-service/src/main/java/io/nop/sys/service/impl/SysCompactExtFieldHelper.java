package io.nop.sys.service.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.support.IOrmCompactExtFieldHelper;
import io.nop.orm.support.IOrmCompactExtFieldSupport;
import io.nop.sys.dao.entity.NopSysCompactExtField;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SysCompactExtFieldHelper implements IOrmCompactExtFieldHelper {

    @Inject
    protected IDaoProvider daoProvider;

    private final Map<String, Map<String, Integer>> entityFieldPositions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, NopSysCompactExtField>> entityFieldConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        try {
            IEntityDao<NopSysCompactExtField> dao = daoProvider.daoFor(NopSysCompactExtField.class);
            List<NopSysCompactExtField> fields = dao.findAll();
            
            Map<String, Map<String, Integer>> positions = new ConcurrentHashMap<>();
            Map<String, Map<String, NopSysCompactExtField>> configs = new ConcurrentHashMap<>();
            
            for (NopSysCompactExtField field : fields) {
                String entityName = field.getEntityName();
                String propName = field.getPropName();
                
                positions.computeIfAbsent(entityName, k -> new ConcurrentHashMap<>())
                        .put(propName, field.getPosition());
                configs.computeIfAbsent(entityName, k -> new ConcurrentHashMap<>())
                        .put(propName, field);
            }
            
            entityFieldPositions.clear();
            entityFieldPositions.putAll(positions);
            entityFieldConfigs.clear();
            entityFieldConfigs.putAll(configs);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String getExtValue(IOrmCompactExtFieldSupport entity, String extName) {
        String flags = entity.getExtFlags();
        if (StringHelper.isEmpty(flags)) {
            return getDefaultValue(entity.orm_entityName(), extName);
        }

        int position = getPosition(entity.orm_entityName(), extName);
        if (position < 0) {
            position = parsePosition(extName);
            if (position < 0) {
                return null;
            }
        }

        if (position >= flags.length()) {
            return getDefaultValue(entity.orm_entityName(), extName);
        }

        char c = flags.charAt(position);
        if (c == ' ') {
            return getDefaultValue(entity.orm_entityName(), extName);
        }
        return String.valueOf(c);
    }

    @Override
    public void setExtValue(IOrmCompactExtFieldSupport entity, String extName, String value) {
        int position = getPosition(entity.orm_entityName(), extName);
        
        if (position < 0) {
            position = parsePosition(extName);
            if (position < 0) {
                return;
            }
        }

        if (value == null) {
            value = "";
        }

        String flags = entity.getExtFlags();
        char[] chars = flags != null ? flags.toCharArray() : new char[0];

        if (position >= chars.length) {
            char[] newChars = new char[position + 1];
            for (int i = 0; i < chars.length; i++) {
                newChars[i] = chars[i];
            }
            for (int i = chars.length; i <= position; i++) {
                newChars[i] = ' ';
            }
            chars = newChars;
        }

        if (!value.isEmpty()) {
            chars[position] = value.charAt(0);
        } else {
            chars[position] = ' ';
        }

        entity.setExtFlags(new String(chars));
    }

    @Override
    public Map<String, String> getExtValues(IOrmCompactExtFieldSupport entity) {
        String flags = entity.getExtFlags();
        String entityName = entity.orm_entityName();
        Map<String, String> result = new LinkedHashMap<>();

        Map<String, Integer> positions = entityFieldPositions.get(entityName);
        if (positions != null) {
            for (Map.Entry<String, Integer> entry : positions.entrySet()) {
                String propName = entry.getKey();
                int position = entry.getValue();
                
                String value;
                if (flags != null && position < flags.length()) {
                    char c = flags.charAt(position);
                    value = c == ' ' ? getDefaultValue(entityName, propName) : String.valueOf(c);
                } else {
                    value = getDefaultValue(entityName, propName);
                }
                result.put(propName, value);
            }
        }

        if (flags != null) {
            for (int i = 0; i < flags.length(); i++) {
                String extName = "ext" + (i + 1);
                if (!result.containsKey(extName)) {
                    result.put(extName, String.valueOf(flags.charAt(i)));
                }
            }
        }

        return result;
    }

    @Override
    public void setExtValues(IOrmCompactExtFieldSupport entity, Map<String, String> values) {
        String flags = entity.getExtFlags();
        char[] chars = flags != null ? flags.toCharArray() : new char[0];
        String entityName = entity.orm_entityName();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String extName = entry.getKey();
            String value = entry.getValue();

            int position = getPosition(entityName, extName);
            if (position < 0) {
                position = parsePosition(extName);
                if (position < 0) {
                    continue;
                }
            }

            if (position >= chars.length) {
                char[] newChars = new char[position + 1];
                for (int i = 0; i < chars.length; i++) {
                    newChars[i] = chars[i];
                }
                for (int i = chars.length; i <= position; i++) {
                    newChars[i] = ' ';
                }
                chars = newChars;
            }

            if (value != null && !value.isEmpty()) {
                chars[position] = value.charAt(0);
            } else {
                chars[position] = ' ';
            }
        }

        entity.setExtFlags(new String(chars));
    }

    private int getPosition(String entityName, String extName) {
        Map<String, Integer> positions = entityFieldPositions.get(entityName);
        if (positions != null) {
            Integer pos = positions.get(extName);
            if (pos != null) {
                return pos;
            }
        }
        return -1;
    }

    private String getDefaultValue(String entityName, String extName) {
        Map<String, NopSysCompactExtField> configs = entityFieldConfigs.get(entityName);
        if (configs != null) {
            NopSysCompactExtField config = configs.get(extName);
            if (config != null) {
                return config.getDefaultValue();
            }
        }
        return null;
    }

    private int parsePosition(String extName) {
        if (extName == null) {
            return -1;
        }
        if (extName.startsWith("ext")) {
            try {
                return Integer.parseInt(extName.substring(3)) - 1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
