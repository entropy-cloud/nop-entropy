package io.nop.file.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.file.dao.entity.NopFileRecord;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-01 去重引用计数竞态红证钉（plan 2526 裁定①，C10 补测-1）。
 *
 * <p>构造性编码交错结果：T1 detach 判定「唯一引用」发生在 T2 copy 记录提交之前——
 * 按旧实现（判-删同路径立即删物理文件），T2 提交的新记录将永久悬挂（下载 404）；
 * 按裁定①两段式延迟清理，物理删除推迟至宽限间隔后复验，复验看到 T2 记录即取消删除。</p>
 *
 * <p>确定性说明：以「先 detach 完成判删、后提交共享路径 copy 记录」的顺序编码交错终态，
 * 等价于 latch 交错的确定性结果形态（T2 提交晚于 T1 判定），无时序敏感。</p>
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestDaoResourceFileStoreDedupRace_W01 extends JunitAutoTestCase {

    @Inject
    DaoResourceFileStore fileStore;

    @Inject
    IDaoProvider daoProvider;


    private IUserContext newUser(String userId, String... roles) {
        Set<String> roleSet = io.nop.commons.util.CollectionHelper.buildImmutableSet(roles);
        return (IUserContext) Proxy.newProxyInstance(IUserContext.class.getClassLoader(),
                new Class[]{IUserContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUserId":
                        case "getUserName":
                            return userId;
                        case "getRoles":
                            return roleSet;
                        case "isUserInRole":
                            return roleSet.contains(args[0]);
                        case "isUserInAnyRole":
                            return !java.util.Collections.disjoint(roleSet, (java.util.Collection<String>) args[0]);
                        default:
                            return null;
                    }
                });
    }

    private String createPhysicalFile(String fileId) throws Exception {
        String path = "/test/dedup-race/" + fileId + ".txt";
        IResource res = fileStore.getResourceStore().getResource(path);
        FileResource fileResource = (FileResource) res;
        fileResource.toFile().getParentFile().mkdirs();
        Files.write(fileResource.toFile().toPath(),
                ("dedup-race-" + fileId).getBytes(StandardCharsets.UTF_8));
        assertTrue(res.exists(), "physical file must exist after setup");
        return path;
    }

    private NopFileRecord newRecord(String fileId, String originFileId, String filePath) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.newEntity();
        record.setFileId(fileId);
        record.setFileName(fileId + ".txt");
        record.setFilePath(filePath);
        record.setFileExt("txt");
        record.setMimeType("text/plain");
        record.setFileLength(16L);
        record.setBizObjName("TestObj");
        record.setBizObjId("obj-1");
        record.setFieldName("attach");
        record.setOriginFileId(originFileId);
        record.setIsPublic(false);
        dao.saveEntity(record);
        return record;
    }

    /**
     * copy 记录提交晚于 detach 判删 → 物理文件必须保留（裁定①延迟清理复验取消删除）。
     */
    @Test
    public void testCopyCommittedAfterDetachJudgmentKeepsPhysicalFile() throws Exception {
        String fileId = "w01-a-" + StringHelper.generateUUID();
        String originFileId = "w01-orig-" + StringHelper.generateUUID();
        String filePath = createPhysicalFile(fileId);

        NopFileRecord recordA = newRecord(fileId, originFileId, filePath);
        NopFileRecord saved = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(recordA.getFileId());
        IUserContext.set(newUser(saved.getCreatedBy(), "user"));

        // T1：detach 完成判删（旧实现：立即删物理文件；裁定①：登记待清理）
        fileStore.detachFile(fileId, "TestObj", "obj-1", "attach");

        // T2：copy 记录提交晚于 T1 判定——共享 originFileId/filePath 的新记录落库
        String copyId = "w01-b-" + StringHelper.generateUUID();
        newRecord(copyId, originFileId, filePath);
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        assertEquals(1L, dao.countByQuery(newQueryByOrigin(originFileId)),
                "copy record must be committed after detach judgment");
        assertEquals(0L, dao.countByQuery(newQueryById(fileId)),
                "detached record must be gone");

        // 延迟清理复验（测试缝同步冲刷，等价宽限间隔届满）：复验必须看到 T2 记录并取消删除
        assertEquals(1, fileStore.pendingDedupCleanupCountForTest(),
                "detach must register deferred cleanup (not delete physical file inline)");
        fileStore.runPendingDedupCleanupsForTest();

        assertTrue(fileStore.getResourceStore().getResource(filePath).exists(),
                "physical file must survive: copy committed after detach judgment "
                        + "(dedup judgment was stale at delete time)");
    }

    /**
     * 无并发 copy 的常规路径：宽限届满复验引用计数为零 → 物理文件正常删除（清理能力不回归）。
     */
    @Test
    public void testDedupCleanupStillDeletesWhenNoCopyRemains() throws Exception {
        String fileId = "w01-c-" + StringHelper.generateUUID();
        String originFileId = "w01-orig-" + StringHelper.generateUUID();
        String filePath = createPhysicalFile(fileId);

        NopFileRecord recordA = newRecord(fileId, originFileId, filePath);
        NopFileRecord saved = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(recordA.getFileId());
        IUserContext.set(newUser(saved.getCreatedBy(), "user"));

        fileStore.detachFile(fileId, "TestObj", "obj-1", "attach");
        fileStore.runPendingDedupCleanupsForTest();

        assertTrue(!fileStore.getResourceStore().getResource(filePath).exists(),
                "deferred cleanup must delete the physical file when no reference remains");
    }

    private io.nop.api.core.beans.query.QueryBean newQueryByOrigin(String originFileId) {
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq("originFileId", originFileId));
        return q;
    }

    private io.nop.api.core.beans.query.QueryBean newQueryById(String fileId) {
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq("fileId", fileId));
        return q;
    }
}
