package io.nop.converter;

import io.nop.converter.utils.DocConvertHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocConvertHelper 辅助语义：二进制类型缺省判定、zipEntryTime 继承规则
 * （首个资源的 lastModified>0 时写入 options，否则保持为空且不覆盖已有值）
 */
public class TestDocConvertHelper {

    @Test
    public void testDefaultBinaryOnlyForBinaryFormats() {
        assertTrue(DocConvertHelper.defaultBinaryOnly("pdf"));
        assertTrue(DocConvertHelper.defaultBinaryOnly("docx"));
        assertTrue(DocConvertHelper.defaultBinaryOnly("xlsx"));
        assertTrue(DocConvertHelper.defaultBinaryOnly("pptx"));
        assertTrue(DocConvertHelper.defaultBinaryOnly("zip"));
        assertTrue(DocConvertHelper.defaultBinaryOnly("jar"));
    }

    @Test
    public void testDefaultBinaryOnlyFalseForTextFormats() {
        assertFalse(DocConvertHelper.defaultBinaryOnly("json"));
        assertFalse(DocConvertHelper.defaultBinaryOnly("xml"));
        assertFalse(DocConvertHelper.defaultBinaryOnly("md"));
    }

    @Test
    public void testInheritZipEntryTimeFromFirstResource() {
        IResource resource = new InMemoryTextResource("/text/a.json", "{}");
        resource.setLastModified(1700000000000L);

        DocumentConvertOptions options = DocumentConvertOptions.create();
        DocConvertHelper.inheritZipEntryTime(Collections.singletonList(resource), options);
        assertEquals(1700000000000L, options.getProperty(DocConvertHelper.OPTION_ZIP_ENTRY_TIME));
    }

    @Test
    public void testInheritZipEntryTimeKeepsExistingProperty() {
        DocumentConvertOptions options = DocumentConvertOptions.create();
        options.setProperty(DocConvertHelper.OPTION_ZIP_ENTRY_TIME, 42L);

        IResource resource = new InMemoryTextResource("/text/a.json", "{}");
        resource.setLastModified(1700000000000L);
        DocConvertHelper.inheritZipEntryTime(Collections.singletonList(resource), options);
        // 已有属性时不覆盖
        assertEquals(42L, options.getProperty(DocConvertHelper.OPTION_ZIP_ENTRY_TIME));
    }

    @Test
    public void testInheritZipEntryTimeIgnoresEmptyOrNullInputs() {
        DocumentConvertOptions options = DocumentConvertOptions.create();
        DocConvertHelper.inheritZipEntryTime(Collections.emptyList(), options);
        DocConvertHelper.inheritZipEntryTime(null, options);
        // lastModified<=0 的资源不写入属性
        DocConvertHelper.inheritZipEntryTime(
                Collections.singletonList((IResource) new InMemoryTextResource("/text/a.json", "{}")), options);
        assertNull(options.getProperty(DocConvertHelper.OPTION_ZIP_ENTRY_TIME));
        // options 为 null 时不得抛错
        DocConvertHelper.inheritZipEntryTime(null, null);
    }
}
