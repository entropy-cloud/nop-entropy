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

    /**
     * 双 map 合一不可变快照（MA-06/W-01 消化）：原两个 volatile 字段两行赋值无法原子替换，
     * 并发读在纳秒级窗内拿到新 positions × 旧 configs 的代际错配（默认值读到 null）。
     * 构造后字段引用不再变更，单 volatile 引用发布保证读点同代际。
     */
    private record ExtFieldSnapshot(Map<String, Map<String, Integer>> positions,
                                    Map<String, Map<String, NopSysCompactExtField>> configs) {
    }

    private volatile ExtFieldSnapshot snapshot = new ExtFieldSnapshot(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

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

            // 写时复制整体替换：clear与putAll之间存在空窗，并发读会短暂读到空映射
            this.snapshot = new ExtFieldSnapshot(positions, configs);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String getExtValue(IOrmCompactExtFieldSupport entity, String extName) {
        // 快照单读：positions与configs必须同代际（W-01/C1-004），跨方法两次读字段会重开代际窗
        ExtFieldSnapshot snap = snapshot;
        String flags = entity.getExtFlags();
        if (StringHelper.isEmpty(flags)) {
            return getDefaultValue(snap, entity.orm_entityName(), extName);
        }

        int position = getPosition(snap, entity.orm_entityName(), extName);
        if (position < 0) {
            position = parsePosition(extName);
            if (position < 0) {
                return null;
            }
        }

        if (position >= flags.length()) {
            return getDefaultValue(snap, entity.orm_entityName(), extName);
        }

        char c = flags.charAt(position);
        if (c == ' ') {
            return getDefaultValue(snap, entity.orm_entityName(), extName);
        }
        return String.valueOf(c);
    }

    @Override
    public void setExtValue(IOrmCompactExtFieldSupport entity, String extName, String value) {
        ExtFieldSnapshot snap = snapshot;
        int position = getPosition(snap, entity.orm_entityName(), extName);
        
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

        ExtFieldSnapshot snap = snapshot;
        Map<String, Integer> positions = snap.positions().get(entityName);
        if (positions != null) {
            for (Map.Entry<String, Integer> entry : positions.entrySet()) {
                String propName = entry.getKey();
                int position = entry.getValue();
                
                String value;
                if (flags != null && position < flags.length()) {
                    char c = flags.charAt(position);
                    value = c == ' ' ? getDefaultValue(snap, entityName, propName) : String.valueOf(c);
                } else {
                    value = getDefaultValue(snap, entityName, propName);
                }
                result.put(propName, value);
            }
        }

        if (flags != null) {
            for (int i = 0; i < flags.length(); i++) {
                String extName = "ext" + (i + 1);
                if (!result.containsKey(extName)) {
                    // 未配置槽位的空格语义与已配置字段对齐：已配置字段空格回落默认值
                    // （未配置时为null），泛化槽位同样返回null，避免' '与null两种空值表示并存
                    char c = flags.charAt(i);
                    result.put(extName, c == ' ' ? null : String.valueOf(c));
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
        ExtFieldSnapshot snap = snapshot;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String extName = entry.getKey();
            String value = entry.getValue();

            int position = getPosition(snap, entityName, extName);
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

    private int getPosition(ExtFieldSnapshot snap, String entityName, String extName) {
        Map<String, Integer> positions = snap.positions().get(entityName);
        if (positions != null) {
            Integer pos = positions.get(extName);
            if (pos != null) {
                return pos;
            }
        }
        return -1;
    }

    private String getDefaultValue(ExtFieldSnapshot snap, String entityName, String extName) {
        Map<String, NopSysCompactExtField> configs = snap.configs().get(entityName);
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
