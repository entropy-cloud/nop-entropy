package io.nop.file.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.file.dao.entity.NopFileRecord;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static io.nop.file.core.FileErrors.ERR_FILE_NO_ATTACH_PERMISSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1-3.8-002 归属校验判别测试（plan 2386）：attachFile/detachFile 仅创建者/admin 可调用，
 * 非创建者抛 ERR_FILE_NO_ATTACH_PERMISSION（修复前仅比对 bizObjName，任何人可劫持并
 * 经 detachFile 物理销毁他人文件记录）。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestDaoResourceFileStoreAttachPermission extends JunitAutoTestCase {

    @Inject
    DaoResourceFileStore fileStore;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    /** IUserContext 动态代理：nop-file-dao 无 nop-auth-core 依赖，测试仅需 userId/roles 语义 */
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

    private NopFileRecord newRecord(String creator) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.newEntity();
        record.setFileId("attach-perm-" + StringHelper.generateUUID());
        record.setFileName("test.txt");
        record.setFilePath("/test/attach-perm/test.txt");
        record.setFileExt("txt");
        record.setMimeType("text/plain");
        record.setFileLength(1L);
        record.setBizObjName("TestObj");
        record.setBizObjId("obj-1");
        record.setFieldName("attach");
        record.setCreatedBy(creator);
        record.setOriginFileId("test");
        record.setIsPublic(false);
        dao.saveEntity(record);
        // flush 并清空 session，使后续 attach/detach 的加载走新 session（模拟真实跨请求场景）
        ormTemplate.runInSession(session -> {
            session.flush();
            session.clear();
            return null;
        });
        // clear 后重新加载并立即 evict，避免后续 attach/detach 的 saveOrUpdate 命中 MANAGED 状态
        NopFileRecord reloaded = dao.requireEntityById(record.getFileId());
        ormTemplate.runInSession(session -> {
            session.clear();
            return null;
        });
        return reloaded;
    }

    @Test
    public void testNonCreatorAttachRejected() {
        NopFileRecord record = newRecord("creator-a");

        // autotest 覆写 createdBy 后，以非创建者身份 attach 必须被拒
        NopFileRecord saved = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(record.getFileId());
        IUserContext.set(newUser("other-user", "user"));

        NopException ex = assertThrows(NopException.class,
                () -> fileStore.attachFile(saved.getFileId(), "TestObj", "obj-2", "attach"));
        assertEquals(ERR_FILE_NO_ATTACH_PERMISSION.getErrorCode(), ex.getErrorCode(),
                "non-creator must be rejected with no-attach-permission");
    }

    @Test
    public void testCreatorAttachAllowed() {
        NopFileRecord record = newRecord("creator-a");

        // autotest 框架会把 createdBy 覆写为 autotest-ref——以落库后的真实创建者为当前用户
        NopFileRecord saved = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(record.getFileId());
        IUserContext.set(newUser(saved.getCreatedBy(), "user"));
        fileStore.attachFile(record.getFileId(), "TestObj", "obj-2", "attach");

        NopFileRecord reloaded = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(record.getFileId());
        assertEquals("obj-2", reloaded.getBizObjId(), "creator attach must succeed");
    }

    @Test
    public void testAdminAttachAllowed() {
        NopFileRecord record = newRecord("creator-a");

        IUserContext.set(newUser("admin-user", "admin"));
        // attach/detach 使用 runInSession：有外层 session 时复用，无外层时退化为 newSession
        ormTemplate.runInSession(session -> {
            fileStore.attachFile(record.getFileId(), "TestObj", "obj-3", "attach");
            return null;
        });

        NopFileRecord reloaded = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(record.getFileId());
        assertEquals("obj-3", reloaded.getBizObjId(), "admin attach must succeed");
    }

    /**
     * 作用域判别（作者裁定 2026-10-10）：store 内部 load/save 必须复用外层 session
     * （runInSession），而不是强制新开 session（runInNewSession）。判别方式：外层 session
     * 中先加载的实体实例，attach 后必须反映新值（同一持久化上下文共享实例）；
     * 若 store 强制 newSession，外层实例将保持旧值。
     */
    @Test
    public void testAttachReusesAmbientSession() {
        NopFileRecord record = newRecord("creator-a");
        NopFileRecord saved = daoProvider.daoFor(NopFileRecord.class)
                .requireEntityById(record.getFileId());
        IUserContext.set(newUser(saved.getCreatedBy(), "user"));

        ormTemplate.runInSession(session -> {
            NopFileRecord preLoaded = daoProvider.daoFor(NopFileRecord.class)
                    .getEntityById(record.getFileId());
            fileStore.attachFile(record.getFileId(), "TestObj", "obj-ambient", "attach");
            assertEquals("obj-ambient", preLoaded.getBizObjId(),
                    "store must join the ambient session (runInSession), not force a new one");
            return null;
        });
    }

    @Test
    public void testNonCreatorDetachRejected() {
        NopFileRecord record = newRecord("creator-a");

        IUserContext.set(newUser("user-b", "user"));
        NopException ex = assertThrows(NopException.class,
                () -> fileStore.detachFile(record.getFileId(), "TestObj", "obj-1", "attach"));
        assertEquals(ERR_FILE_NO_ATTACH_PERMISSION.getErrorCode(), ex.getErrorCode(),
                "non-creator detach must be rejected (prevents physical deletion hijack)");
    }
}
