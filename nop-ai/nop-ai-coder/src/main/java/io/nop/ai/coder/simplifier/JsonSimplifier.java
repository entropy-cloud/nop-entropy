package io.nop.ai.coder.simplifier;

import io.nop.ai.coder.AiCoderConstants;
import io.nop.commons.util.CollectionHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 简化JSON结构，保留指定字段和定位信息
 */
public class JsonSimplifier {
    // 需要保留的字段名
    private final Set<String> keysToKeep;
    // 需要特殊保留的定位字段
    private final Set<String> positioningKeys;

    public JsonSimplifier(Set<String> keysToKeep, Set<String> positioningKeys) {
        this.keysToKeep = keysToKeep != null ? keysToKeep : Collections.emptySet();
        this.positioningKeys = positioningKeys != null ? positioningKeys : Collections.emptySet();
    }

    public JsonSimplifier(Set<String> keysToKeep) {
        this(keysToKeep, AiCoderConstants.DEFAULT_POSITIONING_KEYS);
    }

    /**
     * 简化JSON对象
     *
     * @return 简化后的对象，无保留内容时返回null
     */
    public Object simplify(Object input) {
        if (input instanceof Map) {
            return simplifyMap((Map<String, Object>) input);
        }
        if (input instanceof List) {
            return simplifyList((List<Object>) input);
        }
        return input; // 基本类型直接返回
    }

    // 简化Map结构
    private Map<String, Object> simplifyMap(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        boolean keepNode = false;

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object simplifiedValue = simplify(entry.getValue());

            if (simplifiedValue != null) {
                if (keysToKeep.contains(key)) {
                    result.put(key, simplifiedValue);
                    keepNode = true;
                }

                // 检查子节点是否有内容
                if ((simplifiedValue instanceof Map && !((Map<?, ?>) simplifiedValue).isEmpty()) ||
                        (simplifiedValue instanceof List && !((List<?>) simplifiedValue).isEmpty())) {
                    keepNode = true;
                    result.put(key, simplifiedValue);
                }
            }
        }

        if (keepNode) {
            // 保留定位字段
            positioningKeys.forEach(posKey -> {
                if (input.containsKey(posKey) && !result.containsKey(posKey)) {
                    result.put(posKey, input.get(posKey));
                }
            });
        }
        return result;
    }

    // 简化List结构，过滤掉null元素
    private List<Object> simplifyList(List<Object> input) {
        return input.stream()
                .map(this::simplify)
                .filter(CollectionHelper::notEmpty)
                .collect(Collectors.toList());
    }
}