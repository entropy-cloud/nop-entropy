package io.nop.ai.coder.orm;

import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class AiDictModelChecker {
    static final Logger LOG = LoggerFactory.getLogger(AiDictModelChecker.class);

    public void fixDictProp(XNode dictsNode, Stream<XNode> nodeIt, String dictProp, String dictPrefix) {
        Map<String, XNode> dictMap = new HashMap<>();
        dictsNode.forEachChild(child -> {
            String name = child.attrText("name");
            if (dictPrefix != null && !name.startsWith(dictPrefix)) {
                child.setAttr("name", dictPrefix + name);
            }
            dictMap.put(name, child);
        });

        nodeIt.forEach(node -> {
            String propName = node.attrText(dictProp);
            if (propName == null)
                return;
            XNode dict = dictMap.get(propName);
            if (dict == null) {
                node.removeAttr(dictProp);
                LOG.debug("nop.remove-invalid-dict-prop: {},node={}", propName, dict);
                return;
            }
            node.setAttr(dictProp, dict.attrText("name"));
        });
    }
}
