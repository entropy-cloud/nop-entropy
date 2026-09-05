package io.nop.converter;

import io.nop.converter.impl.SameTypeDocumentConverter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：默认注册表含双向转换对（json&lt;-&gt;json5等），allowChained=true 此前
 * 对双向对无限互递归导致 StackOverflowError
 */
public class TestDocumentConverterManager {

    @Test
    public void testChainedFileTypesWithBidirectionalPair() {
        DocumentConverterManager manager = new DocumentConverterManager();
        manager.registerConverter("json", "json5", SameTypeDocumentConverter.INSTANCE);
        manager.registerConverter("json5", "json", SameTypeDocumentConverter.INSTANCE);
        manager.registerConverter("json", "yaml", SameTypeDocumentConverter.INSTANCE);

        // 修复前此处 StackOverflowError
        Set<String> types = manager.getToFileTypes("json", true);
        assertTrue(types.contains("json5"));
        assertTrue(types.contains("yaml"));
        assertEquals(2, types.size());

        Set<String> reverse = manager.getToFileTypes("json5", true);
        assertTrue(reverse.contains("json"));
        assertTrue(reverse.contains("yaml"));

        assertTrue(manager.getToFileTypes("yaml", false).isEmpty());
    }

    /**
     * 链式查找时某个中间类型缺少builder应跳过继续尝试（返回null表示无可用链路），
     * 修复前直接抛 ERR_NO_DOCUMENT_OBJECT_BUILDER 中断整个查找
     */
    @Test
    public void testChainedConverterSkipsIntermediateWithoutBuilder() {
        DocumentConverterManager manager = new DocumentConverterManager();
        manager.registerConverter("a", "md", SameTypeDocumentConverter.INSTANCE);
        manager.registerConverter("md", "b", SameTypeDocumentConverter.INSTANCE);
        // 不注册 md 的 builder

        // 修复前此处抛 NopException(ERR_NO_DOCUMENT_OBJECT_BUILDER)
        assertEquals(null, manager.getConverter("a", "b", true));
    }
}
