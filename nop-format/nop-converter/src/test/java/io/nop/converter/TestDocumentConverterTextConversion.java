package io.nop.converter;

import io.nop.api.core.exceptions.NopException;
import io.nop.converter.impl.JsonDocumentConverter;
import io.nop.converter.impl.JsonDocumentObjectBuilder;
import io.nop.converter.impl.ResourceDocumentObject;
import io.nop.converter.impl.SameTypeDocumentConverter;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentConverterManager 转换语义：文本转换经 builder 构建文档对象后走转换器；
 * 同类型转换返回原文；未注册转换链路抛 NopException；文件扩展名可回退查找 builder
 */
public class TestDocumentConverterTextConversion {

    private DocumentConverterManager newManagerWithJson() {
        DocumentConverterManager manager = new DocumentConverterManager();
        manager.registerDocumentObjectBuilder("json", new JsonDocumentObjectBuilder());
        manager.registerConverter("json", "yaml", new JsonDocumentConverter());
        manager.registerConverter("json", "json5", new JsonDocumentConverter());
        return manager;
    }

    @Test
    public void testConvertJsonToYaml() {
        DocumentConverterManager manager = newManagerWithJson();
        String yaml = manager.convertText("/text/a.json", "{\"a\":1,\"b\":\"x\"}", "json", "yaml",
                DocumentConvertOptions.create());
        // YAML 输出必须保留键值语义
        assertTrue(yaml.contains("a: 1"), yaml);
        assertTrue(yaml.contains("b: x"), yaml);
    }

    @Test
    public void testConvertJsonToJson5PrettyPrint() {
        DocumentConverterManager manager = newManagerWithJson();
        String out = manager.convertText("/text/a.json", "{\"a\":1}", "json", "json5",
                DocumentConvertOptions.create());
        // json5 目标走 pretty JSON 序列化
        assertTrue(out.contains("\"a\": 1"), out);
    }

    @Test
    public void testSameFileTypeConversionReturnsOriginalText() {
        DocumentConverterManager manager = newManagerWithJson();
        String text = "{\"a\":1}";
        String out = manager.convertText("/text/a.json", text, "json", "json",
                DocumentConvertOptions.create());
        assertEquals(text, out);
    }

    @Test
    public void testMissingBuilderThrows() {
        DocumentConverterManager manager = new DocumentConverterManager();
        NopException e = assertThrows(NopException.class, () -> manager.convertText(
                "/text/a.json", "{}", "json", "yaml", DocumentConvertOptions.create()));
        assertEquals("json", e.getParam("fileType"));
    }

    @Test
    public void testMissingConverterRouteThrows() {
        DocumentConverterManager manager = newManagerWithJson();
        NopException e = assertThrows(NopException.class, () -> manager.convertText(
                "/text/a.json", "{}", "json", "xml", DocumentConvertOptions.create()));
        assertEquals("json", e.getParam("fromFileType"));
        assertEquals("xml", e.getParam("toFileType"));
    }

    @Test
    public void testGetConverterDirectAndMissing() {
        DocumentConverterManager manager = newManagerWithJson();
        assertEquals(JsonDocumentConverter.class, manager.getConverter("json", "yaml", false).getClass());
        // 未注册且不允许链式时返回 null；requireConverter 才抛错
        assertNull(manager.getConverter("json", "xml", false));
    }

    @Test
    public void testChainedConversionThroughIntermediateType() {
        // json 与 yaml 之间无直接转换器，仅经 json5 中转
        DocumentConverterManager manager = new DocumentConverterManager();
        manager.registerDocumentObjectBuilder("json", new JsonDocumentObjectBuilder());
        manager.registerDocumentObjectBuilder("json5", new JsonDocumentObjectBuilder());
        manager.registerConverter("json", "json5", new JsonDocumentConverter());
        manager.registerConverter("json5", "yaml", new JsonDocumentConverter());

        String yaml = manager.convertText("/text/a.json", "{\"a\":2}", "json", "yaml",
                DocumentConvertOptions.create().allowChained());
        assertTrue(yaml.contains("a: 2"), yaml);
    }

    @Test
    public void testBuilderLookupFallsBackToFileExt() {
        DocumentConverterManager manager = newManagerWithJson();
        // "a.xlsx.json" 这类带点 fileType 回退到扩展名 "json" 查找 builder
        assertEquals(JsonDocumentObjectBuilder.class,
                manager.getDocumentObjectBuilder("a.xlsx.json").getClass());
        assertNull(manager.getDocumentObjectBuilder("no-such-type"));
        assertThrows(NopException.class, () -> manager.requireDocumentObjectBuilder("no-such-type"));
    }

    @Test
    public void testSameTypeConverterReturnsResourceText() {
        IResource resource = new InMemoryTextResource("/text/b.json5", "raw text");
        ResourceDocumentObject doc = new ResourceDocumentObject("json5", resource);
        assertEquals("raw text",
                SameTypeDocumentConverter.INSTANCE.convertToText(doc, "json5", DocumentConvertOptions.create()));
    }

    @Test
    public void testGetToFileTypesDirectOnly() {
        DocumentConverterManager manager = newManagerWithJson();
        Set<String> direct = manager.getToFileTypes("json", false);
        assertEquals(2, direct.size());
        assertTrue(direct.contains("yaml"));
        assertTrue(direct.contains("json5"));
        // 没有任何出边的类型返回空集
        assertTrue(manager.getToFileTypes("yaml", false).isEmpty());
    }
}
