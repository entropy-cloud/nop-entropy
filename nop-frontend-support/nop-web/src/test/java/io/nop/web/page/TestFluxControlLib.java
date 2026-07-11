package io.nop.web.page;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFluxControlLib extends JunitBaseTestCase {

    static final String FLUX_CONTROL_LIB = "/nop/web/xlib/flux-control.xlib";

    static final Set<String> ALL_TAGS = new HashSet<>(java.util.Arrays.asList(
            "edit-tree-parent", "edit-double", "edit-decimal", "edit-short", "edit-byte",
            "edit-int", "edit-long", "edit-email", "edit-url", "edit-ascii", "edit-asciiNum",
            "edit-path", "edit-phone", "edit-telephone", "edit-date", "edit-datetime",
            "edit-timestamp", "edit-list-select", "edit-radios", "edit-enum",
            "view-enum", "view-labelProp", "edit-textarea", "edit-html", "view-html",
            "edit-longtext", "edit-remark", "edit-tag-list", "view-tag-list",
            "view-image", "list-view-image", "edit-image", "edit-file", "edit-file-list",
            "view-file", "view-file-list", "view-images", "edit-images",
            "edit-string-array", "edit-deptId", "view-boolean", "edit-boolean",
            "view-boolFlag", "edit-boolFlag", "view-pre", "edit-string",
            "query-string", "query-datetime", "query-date", "query-timestamp",
            "edit-relation", "edit-roleId", "edit-userId", "view-relation",
            "view-ref-ids", "view-ref-id", "edit-ref-ids", "edit-ref-id",
            "edit-to-one", "view-to-one", "query-to-one", "query-to-many",
            "edit-to-many", "view-to-many", "edit-password", "view-password",
            "edit-hidden", "view-hidden", "edit-any", "view-any",
            "view-xml", "edit-xml", "view-xpl", "edit-xpl", "edit-select"
    ));

    @Test
    public void testLoadLib() {
        IXplTagLib lib = XplLibHelper.loadLib(FLUX_CONTROL_LIB);
        assertNotNull(lib);

        for (String tagName : ALL_TAGS) {
            IXplTag tag = lib.getTag(tagName);
            assertNotNull(tag, "Tag not found: " + tagName);
        }
        assertEquals(75, ALL_TAGS.size(), "Expected 75 tags");
    }

    @Test
    public void testPrototypeInheritance() {
        IXplTagLib lib = XplLibHelper.loadLib(FLUX_CONTROL_LIB);

        IXplTag timestampTag = lib.getTag("edit-timestamp");
        assertNotNull(timestampTag, "edit-timestamp should exist via x:prototype");

        IXplTag datetimeTag = lib.getTag("edit-datetime");
        assertNotNull(datetimeTag);

        IXplTag longtextTag = lib.getTag("edit-longtext");
        assertNotNull(longtextTag, "edit-longtext should exist via x:prototype");

        IXplTag remarkTag = lib.getTag("edit-remark");
        assertNotNull(remarkTag, "edit-remark should exist via x:prototype");
    }

    @Test
    public void testFluxTypeNames() {
        IXplTagLib lib = XplLibHelper.loadLib(FLUX_CONTROL_LIB);

        assertNotNull(lib.getTag("edit-double"), "edit-double should exist");
        assertNotNull(lib.getTag("edit-string"), "edit-string should exist");
        assertNotNull(lib.getTag("edit-enum"), "edit-enum should exist");
        assertNotNull(lib.getTag("edit-boolFlag"), "edit-boolFlag should exist");
        assertNotNull(lib.getTag("edit-date"), "edit-date should exist");
        assertNotNull(lib.getTag("edit-html"), "edit-html should exist (maps to markdown-editor)");
        assertNotNull(lib.getTag("edit-relation"), "edit-relation should exist");
        assertNotNull(lib.getTag("edit-file"), "edit-file should exist");
        assertNotNull(lib.getTag("edit-password"), "edit-password should exist");
        assertNotNull(lib.getTag("view-xml"), "view-xml should exist (maps to json-view)");

        assertTrue(lib.getTag("edit-any") != null || lib.getTag("view-any") != null,
                "Fallback tags should exist");
    }
}
