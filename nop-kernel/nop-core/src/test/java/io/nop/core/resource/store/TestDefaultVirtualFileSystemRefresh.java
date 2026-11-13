/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.commons.concurrent.ResourceRetirementQueue;
import io.nop.core.resource.impl.ZipEntryResource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N2 钉改写（plan 2504 裁定⑤，与 C1-014 修复同变更集）：refresh 的旧代 zip 关闭时机按退役代际契约。
 * <p>
 * 改写后断言语义（原「refresh 返回后旧 zip 立即 closed」断言废止）：
 * <ol>
 * <li>保留：「重建期间不得提前关闭」——先发布后关闭性质在退役契约下依旧成立且加强；</li>
 * <li>改写为二段断言：(a) refresh 返回时旧 zip 未被同步关闭（已退役代宽限期内存活）；
 * (b) 经退役队列确定性触发排空后旧 zip 必然关闭（关闭时机可观测，零 sleep）；</li>
 * <li>新增接线断言：refresh 后经 getResource 读取解析到新代内容。</li>
 * </ol>
 * 修复前基线可红（现行为立即关闭旧 zip）；关闭档（grace ≤0）即旧语义特例（可红通道）。
 */
public class TestDefaultVirtualFileSystemRefresh {

    private static boolean isZipClosed(ZipFile zipFile) {
        try {
            zipFile.size();
            return false;
        } catch (IllegalStateException e) {
            // zip file closed
            return true;
        }
    }

    private static void setZipFiles(DefaultVirtualFileSystem vfs, List<ZipFile> zipFiles) throws Exception {
        Field field = DefaultVirtualFileSystem.class.getDeclaredField("zipFiles");
        field.setAccessible(true);
        field.set(vfs, zipFiles);
    }

    private static File buildZip(String content) throws Exception {
        File zipFile = File.createTempFile("nop-core-test", ".zip");
        zipFile.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            out.putNextEntry(new ZipEntry("a.txt"));
            out.write(content.getBytes());
        }
        return zipFile;
    }

    /**
     * N2 改写主体：重建期不提前关闭（保留）+ 退役二段断言（宽限内存活 / 确定性排空后关闭）
     */
    @Test
    public void testRefreshRetiresOldZipWithGrace() throws Exception {
        File zipFile = buildZip("v1");

        try (ZipFile oldZip = new ZipFile(zipFile)) {
            AtomicBoolean closedDuringRebuild = new AtomicBoolean();

            DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem() {
                @Override
                protected void buildResourceStore() {
                    // 模拟重建耗时期间检查旧zip是否已被提前关闭
                    closedDuringRebuild.set(isZipClosed(oldZip));
                }
            };

            setZipFiles(vfs, List.of(oldZip));

            vfs.refresh(false);

            // 断言1（保留）：重建期间不得提前关闭
            assertFalse(closedDuringRebuild.get(), "old zip files must not be closed while rebuilding resource store");
            // 断言2a（改写）：refresh 返回时旧 zip 未被同步关闭（已退役，宽限期内存活）
            assertFalse(isZipClosed(oldZip),
                    "retired old zip must survive within grace window (immediate close is pre-fix semantics)");

            // 断言2b（改写）：确定性触发排空后旧 zip 必然关闭（销毁/关闭时机可观测，零 sleep）
            ResourceRetirementQueue.global().drainAllNow();
            assertTrue(isZipClosed(oldZip), "retired old zip must be closed after deterministic drain");
        }
    }

    /**
     * 在飞读者回归（C1-014 本体）：发布前已捕获旧代 store 的读者，refresh 之后仍能在宽限期内
     * 经共享 ZipFile 句柄完成读取（修复前红形态 = zip file closed）；排空后旧代关闭（时机可观测）。
     */
    @Test
    public void testInFlightReaderSurvivesRefresh() throws Exception {
        File zipFile = buildZip("v1");

        try (ZipFile oldZip = new ZipFile(zipFile)) {
            DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
            setZipFiles(vfs, List.of(oldZip));

            // 在飞读者：refresh 前捕获旧代资源引用
            ZipEntryResource inFlight = new ZipEntryResource("/test/retire/a.txt", oldZip, oldZip.getEntry("a.txt"));

            vfs.refresh(false);

            // 宽限期内在飞读取完成（修复前此处抛 zip file closed）
            byte[] buf = inFlight.getInputStream().readAllBytes();
            assertEquals("v1", new String(buf), "in-flight reader must complete on the retired generation");

            // 排空后旧代关闭：延迟关闭语义非 javadoc 声明，真实代码路径
            ResourceRetirementQueue.global().drainAllNow();
            assertTrue(isZipClosed(oldZip), "retired generation must be closed after drain");
        }
    }

    /**
     * 新增接线断言（裁定⑤③）：refresh 后读取解析到新代内容（重建真实生效，非仅句柄生命周期）。
     */
    @Test
    public void testRefreshResolvesNewGenerationContent() throws Exception {
        File oldZipFile = buildZip("v1");
        File newZipFile = buildZip("v2");

        AtomicBoolean closedDuringRebuild = new AtomicBoolean();

        try (ZipFile oldZip = new ZipFile(oldZipFile); ZipFile newZip = new ZipFile(newZipFile)) {
            DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem() {
                @Override
                protected void buildResourceStore() {
                    closedDuringRebuild.set(isZipClosed(oldZip));
                    // 发布新代：store 内注册指向新 zip 的资源（发布点形态保持，经父类字段装配）
                    try {
                        Field field = DefaultVirtualFileSystem.class.getDeclaredField("zipFiles");
                        field.setAccessible(true);
                        field.set(this, List.of(newZip));

                        InMemoryResourceStore mem = new InMemoryResourceStore();
                        mem.addResource(new ZipEntryResource("/test/retire/new.txt", newZip, newZip.getEntry("a.txt")));
                        DeltaResourceStore deltaStore = new DeltaResourceStore();
                        deltaStore.setStore(mem);
                        Field storeField = DefaultVirtualFileSystem.class.getDeclaredField("deltaResourceStore");
                        storeField.setAccessible(true);
                        storeField.set(this, deltaStore);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            };

            setZipFiles(vfs, List.of(oldZip));

            vfs.refresh(false);

            assertFalse(closedDuringRebuild.get(), "old zip must not be closed while rebuilding");
            assertFalse(isZipClosed(oldZip), "old generation retired (grace), new generation published");

            // 接线断言：经真实 getResource 读取解析到新代内容
            Object resource = vfs.getResource("/test/retire/new.txt", true);
            assertNotNull(resource, "new generation resource must resolve after refresh");
            byte[] buf = ((ZipEntryResource) resource).getInputStream().readAllBytes();
            assertEquals("v2", new String(buf), "refresh must resolve reads to the new generation content");
        }
    }

    /**
     * destroy 路径同族面：destroy 同样走退役登记（延迟关闭），空 store 立即发布
     */
    @Test
    public void testDestroyRetiresZipGeneration() throws Exception {
        File zipFile = buildZip("v1");

        try (ZipFile oldZip = new ZipFile(zipFile)) {
            DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem();
            setZipFiles(vfs, List.of(oldZip));

            vfs.destroy();

            assertFalse(isZipClosed(oldZip), "destroy must retire (not inline close) the zip generation");
            ResourceRetirementQueue.global().drainAllNow();
            assertTrue(isZipClosed(oldZip), "retired generation must be closed after drain");
        }
    }
}
