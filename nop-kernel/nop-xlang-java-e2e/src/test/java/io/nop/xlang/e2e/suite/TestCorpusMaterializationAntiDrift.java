package io.nop.xlang.e2e.suite;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CorpusCoverageA;
import io.nop.xlang.compare.CorpusCoverageB;
import io.nop.xlang.compare.CorpusV1;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * corpus 物化反漂移断言（I12 Phase 1 D1）：e2e `_vfs` 物化 corpus 资源与 nop-xlang
 * test-jar 原件（单一事实源，`xlang-compare/{static,static-a,static-b}`）逐文件字节一致。
 * corpus 原件变更未同步物化（或反向漂移）即红灯——物化是拷贝不是分叉。
 */
public class TestCorpusMaterializationAntiDrift {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMaterializedCorpusBytesMatchSingleSourceOfTruth() {
        List<CompareUnit> staticUnits = new ArrayList<>();
        for (CompareUnit u : CorpusV1.units())
            if (u.getKind() == CompareUnitKind.STATIC)
                staticUnits.add(u);
        for (CompareUnit u : CorpusCoverageA.units())
            if (u.getKind() == CompareUnitKind.STATIC)
                staticUnits.add(u);
        for (CompareUnit u : CorpusCoverageB.units())
            if (u.getKind() == CompareUnitKind.STATIC)
                staticUnits.add(u);
        assertTrue(staticUnits.size() == 48, "corpus static units must be 48 (11+20+17): " + staticUnits.size());

        for (CompareUnit unit : staticUnits) {
            String corpusPath = unit.getSourceLocationPath(); // xlang-compare/static*/name.xpl
            byte[] original = readClasspath(corpusPath);
            byte[] materialized = readVfs(E2eCorpusUnits.vfsPath(corpusPath));
            assertArrayEquals(original, materialized,
                    "materialized corpus file must be byte-identical with the nop-xlang single source: "
                            + corpusPath);
        }
    }

    private static byte[] readClasspath(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            cl = TestCorpusMaterializationAntiDrift.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            assertNotNull(in, "corpus original not found on classpath (nop-xlang test-jar): " + path);
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("read corpus original failed: " + path, e);
        }
    }

    private static byte[] readVfs(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        assertNotNull(resource, "materialized corpus resource missing in e2e _vfs: " + vfsPath);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("read materialized corpus resource failed: " + vfsPath, e);
        }
    }
}
