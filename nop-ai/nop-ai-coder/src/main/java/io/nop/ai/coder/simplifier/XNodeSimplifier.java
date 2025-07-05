package io.nop.ai.coder.simplifier;

import io.nop.core.lang.xml.XNode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 简化XML节点结构，保留关键属性和定位信息
 */
public class XNodeSimplifier {
    // 需要保留的属性和标签名
    private final Set<String> keysToKeep;
    // 需要特殊保留的定位属性
    private final Set<String> positioningKeys;

    public XNodeSimplifier(Set<String> keysToKeep, Set<String> positioningKeys) {
        this.keysToKeep = keysToKeep != null ? keysToKeep : Collections.emptySet();
        this.positioningKeys = positioningKeys != null ? positioningKeys : Collections.emptySet();
    }

    /**
     * 简化节点：保留keysToKeep中的属性和标签，以及positioningKeys中的定位属性
     *
     * @return 简化后的节点，如果没有需要保留的内容则返回null
     */
    public XNode simplify(XNode input) {
        if (input == null) {
            return null;
        }

        XNode result = XNode.make(input.getTagName());
        result.setLocation(input.getLocation());

        boolean keepNode = this.keysToKeep.contains(result.getTagName());

        // 处理属性
        simplifyAttrs(input, result);
        if (result.hasAttr()) {
            keepNode = true;
        }

        // 处理子节点
        List<XNode> simplifiedChildren = simplifyChildren(input);
        if (!simplifiedChildren.isEmpty()) {
            keepNode = true;
            simplifiedChildren.forEach(result::appendChild);
        }

        if (keepNode) {
            // 确保定位属性不丢失
            positioningKeys.forEach(posAttr -> {
                if (input.hasAttr(posAttr) && !result.hasAttr(posAttr)) {
                    result.attrValueLoc(posAttr, input.attrValueLoc(posAttr));
                }
            });
            return result;
        }
        return null;
    }

    // 只保留keysToKeep中的属性
    void simplifyAttrs(XNode input, XNode node) {
        keysToKeep.forEach(attr -> {
            if (input.hasAttr(attr)) {
                node.attrValueLoc(attr, input.attrValueLoc(attr));
            }
        });
    }

    // 递归简化子节点并过滤掉null结果
    private List<XNode> simplifyChildren(XNode node) {
        return node.getChildren().stream()
                .map(this::simplify)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}