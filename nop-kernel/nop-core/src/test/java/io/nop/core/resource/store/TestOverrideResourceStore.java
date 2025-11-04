/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOverrideResourceStore {
    @Test
    public void testOverride() {
        File dir = MavenDirHelper.getClassesDir(TestOverrideResourceStore.class);
        FileHelper.setCurrentDir(new File(dir, "sub"));

        VirtualFileSystem.registerInstance(new DefaultVirtualFileSystem());

        IResource resource = VirtualFileSystem.instance().getResource("/my.txt");
        assertEquals("b", ResourceHelper.readText(resource));

        IResource base = VirtualFileSystem.instance().getResource("/dict/test/my.dict.yaml");
        assertTrue(base.exists());
    }
}
