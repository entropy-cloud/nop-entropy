/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.dao.store;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.io.stream.LimitedInputStream;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.InputStreamResource;
import io.nop.core.resource.store.LocalResourceStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.file.core.FileConstants;
import io.nop.file.core.IFileRecord;
import io.nop.file.core.IFileStore;
import io.nop.file.core.UploadRequestBean;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.orm.IOrmEntityFileStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.ne;
import static io.nop.file.core.FileErrors.ARG_BIZ_OBJ_ID;
import static io.nop.file.core.FileErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.file.core.FileErrors.ARG_FIELD_NAME;
import static io.nop.file.core.FileErrors.ARG_FILE_ID;
import static io.nop.file.core.FileErrors.ARG_FILE_OBJ_NAME;
import static io.nop.file.core.FileErrors.ARG_LENGTH;
import static io.nop.file.core.FileErrors.ARG_MAX_LENGTH;
import static io.nop.file.core.FileErrors.ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ;
import static io.nop.file.core.FileErrors.ERR_FILE_NO_ATTACH_PERMISSION;
import static io.nop.file.core.FileErrors.ERR_FILE_LENGTH_EXCEED_LIMIT;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_ALLOW_ACCESS_FILE;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_EXISTS;

/**
 * 将上传文件存放在本地目录下
 */
public class DaoResourceFileStore implements IFileStore, IOrmEntityFileStore {
    static final Logger LOG = LoggerFactory.getLogger(DaoResourceFileStore.class);

    static final ErrorCode ERR_FILE_INVALID_BIZ_OBJ_NAME =
            ErrorCode.define("nop.err.file.invalid-biz-obj-name", "非法的业务对象名[{bizObjName}]");
    static final ErrorCode ERR_FILE_INVALID_FILE_EXT =
            ErrorCode.define("nop.err.file.invalid-file-ext", "非法的文件扩展名[{fileExt}]");
    static final ErrorCode ERR_FILE_DIGEST_NOT_AVAILABLE =
            ErrorCode.define("nop.err.file.digest-not-available", "SHA-256 digest algorithm is not available");
    private IDaoProvider daoProvider;

    @Inject
    private IOrmTemplate ormTemplate;

    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    private IResourceStore resourceStore;

    private boolean keepFileExt = true;

    public IDaoProvider getDaoProvider() {
        return daoProvider;
    }

    public IResourceStore getResourceStore() {
        return resourceStore;
    }

    public boolean isKeepFileExt() {
        return keepFileExt;
    }

    public void setKeepFileExt(boolean keepFileExt) {
        this.keepFileExt = keepFileExt;
    }

    @PostConstruct
    public void init() {
        Guard.notNull(resourceStore, "resourceStore");
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setResourceStore(IResourceStore resourceStore) {
        this.resourceStore = resourceStore;
    }

    public void setLocalDir(File dir) {
        this.resourceStore = new LocalResourceStore("/", dir);
    }

    @Override
    public String getFileLink(String fileId) {
        return FileConstants.PATH_DOWNLOAD + "/" + fileId;
    }

    @Override
    public String decodeFileId(String fileLink) {
        if (StringHelper.isEmpty(fileLink))
            return null;
        if (!fileLink.startsWith(FileConstants.PATH_DOWNLOAD + "/"))
            return null;
        String fileId = StringHelper.lastPart(fileLink, '/');
        // 除去文件后缀名
        fileId = StringHelper.removeFileExt(fileId);
        return fileId;
    }

    /**
     * 用于性能优化的函数，将对应的FileRecord加载到内存中。如果是同步加载，则返回null即可
     */
    @Override
    public CompletionStage<?> batchLoadResourceAsync(Collection<String> fileIds) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        dao.batchGetEntitiesByIds(fileIds);
        return null;
    }

    public void removeTempFileByOwner(String ownerId) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.and(
                eq(NopFileRecord.PROP_NAME_createdBy, ownerId),
                eq(NopFileRecord.PROP_NAME_bizObjId, FileConstants.TEMP_BIZ_OBJ_ID)));
        List<NopFileRecord> records = dao.findAllByQuery(query);
        for (NopFileRecord record : records) {
            dao.deleteEntity(record);
            // 与 detachFile 对齐：仅删DB行会让物理文件成为永久孤儿，存储目录持续膨胀
            // W-01（plan 2526 裁定①）：判-删竞态窗口窄化——物理删除走两段式延迟清理
            if (isUniqueRef(dao, record)) {
                registerDedupCleanup(record);
            }
        }
    }

    @Override
    public IFileRecord getFile(String fileId) {
        NopFileRecord record = daoProvider.daoFor(NopFileRecord.class).requireEntityById(fileId);
        IResource resource = resourceStore.getResource(record.getFilePath());
        return new DaoFileRecord(record, resource);
    }

    protected boolean isDefaultPublic(UploadRequestBean record) {
        return false;
    }

    public String saveFile(UploadRequestBean record, long maxLength) {
        checkMaxSize(record.getLength(), maxLength);

        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord entity = dao.newEntity();
        entity.setFileName(record.getFileName());
        entity.setFieldName(record.getFieldName());
        entity.setFileExt(record.getFileExt());
        entity.setFileLength(record.getLength());
        entity.setIsPublic(isDefaultPublic(record));
        if (StringHelper.isEmpty(record.getBizObjId())) {
            // 标记为临时对象。如果最终没有提交，则会应该自动删除这些记录
            entity.setBizObjId(FileConstants.TEMP_BIZ_OBJ_ID);
        } else {
            entity.setBizObjId(record.getBizObjId());
        }
        entity.setBizObjName(record.getBizObjName());
        entity.setMimeType(record.getMimeType());
        if (StringHelper.isEmpty(entity.getMimeType()))
            entity.setMimeType(MediaType.APPLICATION_OCTET_STREAM);

        String fileId = newFileId();
        String filePath = newPath(record.getBizObjName(), fileId, entity.getFileExt());
        entity.setFileId(fileId);
        entity.setOriginFileId(fileId);
        entity.setFilePath(filePath);

        IResource tempResource = null;
        InputStream is = null;
        try {
            is = record.getInputStream();
            // [G9-04-01] persist the content SHA-256 digest into FILE_HASH. The stream is
            // wrapped in a DigestInputStream so the digest is computed during the single
            // write pass below instead of re-reading the stored content.
            MessageDigest contentDigest = newSha256Digest();
            is = new DigestInputStream(is, contentDigest);
            if (record.getLength() > 0) {
                is = new LimitedInputStream(is, record.getLength());
                filePath = resourceStore.saveResource(filePath, new InputStreamResource(filePath, is, record.getLastModified(), record.getLength()), null, null);
            } else {
                tempResource = ResourceHelper.getTempResource("upload-file");
                if (maxLength > 0) {
                    is = new LimitedInputStream(is, maxLength);
                }
                ResourceHelper.writeStream(tempResource, is);
                entity.setFileLength(tempResource.length());
                filePath = resourceStore.saveResource(filePath, tempResource, null, null);
            }

            entity.setFilePath(filePath);
            entity.setFileHash(HexFormat.of().formatHex(contentDigest.digest()));
            dao.saveEntity(entity);
            return entity.getFileId();
        } catch (Exception e) {
            removeResource(filePath);
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(is);
            if (tempResource != null)
                tempResource.delete();
        }
    }

    protected void checkMaxSize(long length, long maxFileSize) {
        if (maxFileSize > 0 && length > maxFileSize)
            throw new NopException(ERR_FILE_LENGTH_EXCEED_LIMIT)
                    .param(ARG_LENGTH, length).param(ARG_MAX_LENGTH, maxFileSize);
    }

    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new NopException(ERR_FILE_DIGEST_NOT_AVAILABLE, e);
        }
    }


    private boolean removeResource(String path) {
        try {
            IResource resource = resourceStore.getResource(path);
            return resource.delete();
        } catch (Exception e) {
            LOG.error("nop.file.remove-file-fail:path={}", path, e);
            return false;
        }
    }

    // ==================== W-01 两段式延迟清理（plan 2526 裁定①） ====================

    /**
     * 去重延迟清理宽限间隔（10min）：isUniqueRef 命中后物理删除推迟到该间隔之后复验执行，
     * 并发 copy 记录（共享 originFileId/filePath）只要在宽限间隔内提交即被复验看见并取消删除。
     * 去重判-删竞态窗口从微秒级（判-删与并发 copy 提交交错）窄化至该间隔；
     * 残余窗口（登记后间隔届满仍未提交的极端长事务）为已声明语义（owner doc 登记）。
     */
    static final long DEDUP_CLEANUP_GRACE_MILLIS = 600_000L;
    /** 物理删除失败重试上限（同宽限间隔步进）；仍失败输出结构化 give-up WARN（path 可 grep 对账）。 */
    static final int DEDUP_CLEANUP_MAX_ATTEMPTS = 3;

    private final ConcurrentMap<String, PendingDedupCleanup> pendingDedupCleanups = new ConcurrentHashMap<>();

    private volatile ScheduledExecutorService dedupCleanupExecutor;

    static final class PendingDedupCleanup {
        final String filePath;
        final String originFileId;
        volatile int attempts;

        PendingDedupCleanup(String filePath, String originFileId) {
            this.filePath = filePath;
            this.originFileId = originFileId;
        }
    }

    private ScheduledExecutorService dedupCleanupExecutor() {
        ScheduledExecutorService executor = dedupCleanupExecutor;
        if (executor == null) {
            synchronized (this) {
                if (dedupCleanupExecutor == null) {
                    executor = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "nop-file-dedup-cleanup");
                        t.setDaemon(true);
                        return t;
                    });
                    dedupCleanupExecutor = executor;
                } else {
                    executor = dedupCleanupExecutor;
                }
            }
        }
        return executor;
    }

    @PreDestroy
    public void shutdownDedupCleanupExecutor() {
        ScheduledExecutorService executor = dedupCleanupExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 登记去重延迟清理（裁定①）：isUniqueRef 命中（判明唯一引用）后调用——不立即删物理文件，
     * 登记后由延迟任务复验。同 filePath 重复登记幂等（首登记生效）。
     */
    void registerDedupCleanup(NopFileRecord record) {
        String filePath = record.getFilePath();
        if (StringHelper.isEmpty(filePath))
            return;
        PendingDedupCleanup entry = new PendingDedupCleanup(filePath, record.getOriginFileId());
        if (pendingDedupCleanups.putIfAbsent(filePath, entry) != null)
            return;
        dedupCleanupExecutor().schedule(() -> runDedupCleanup(entry), DEDUP_CLEANUP_GRACE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void runDedupCleanup(PendingDedupCleanup entry) {
        try {
            // 复验引用计数：同 originFileId 的记录数 >0（并发 copy 已提交，或原记录删除事务回滚）→ 取消删除
            Long refs = ormTemplate.runInNewSession(session -> {
                QueryBean query = new QueryBean();
                query.addFilter(eq(NopFileRecord.PROP_NAME_originFileId, entry.originFileId));
                return daoProvider.daoFor(NopFileRecord.class).countByQuery(query);
            });
            if (refs != null && refs > 0) {
                pendingDedupCleanups.remove(entry.filePath);
                LOG.info("nop.file.dedup-cleanup.cancelled:path={},originFileId={},refCount={}",
                        entry.filePath, entry.originFileId, refs);
                return;
            }
            IResource resource = resourceStore.getResource(entry.filePath);
            if (resource == null || !resource.exists() || resource.delete()) {
                pendingDedupCleanups.remove(entry.filePath);
                return;
            }
            retryOrGiveUpDedupCleanup(entry);
        } catch (Exception e) {
            LOG.error("nop.file.dedup-cleanup.fail:path={},originFileId={}", entry.filePath, entry.originFileId, e);
            retryOrGiveUpDedupCleanup(entry);
        }
    }

    private void retryOrGiveUpDedupCleanup(PendingDedupCleanup entry) {
        entry.attempts++;
        if (entry.attempts >= DEDUP_CLEANUP_MAX_ATTEMPTS) {
            // 结构化 give-up 日志：path 可 grep，供导出产物对账任务（Non-Blocking Follow-up）消费
            LOG.error("nop.file.dedup-cleanup.give-up:path={},originFileId={},attempts={}",
                    entry.filePath, entry.originFileId, entry.attempts);
            pendingDedupCleanups.remove(entry.filePath);
            return;
        }
        dedupCleanupExecutor().schedule(() -> runDedupCleanup(entry), DEDUP_CLEANUP_GRACE_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** 测试缝（package-private）：同步冲刷全部待清理条目（红①不依赖真实宽限等待）。 */
    void runPendingDedupCleanupsForTest() {
        for (PendingDedupCleanup entry : pendingDedupCleanups.values().toArray(new PendingDedupCleanup[0])) {
            runDedupCleanup(entry);
        }
    }

    int pendingDedupCleanupCountForTest() {
        return pendingDedupCleanups.size();
    }

    protected String newFileId() {
        return StringHelper.generateUUID();
    }

    protected String newPath(String bizObjName, String fileId, String fileExt) {
        // 纵深防御：bizObjName/fileExt 直接拼入存储路径，非受控调用方传入 ../ 等即可越出存储根目录
        if (!StringHelper.isValidSimpleVarName(bizObjName))
            throw new NopException(ERR_FILE_INVALID_BIZ_OBJ_NAME).param("bizObjName", bizObjName);
        if (!StringHelper.isEmpty(fileExt) && !fileExt.matches("[A-Za-z0-9]+"))
            throw new NopException(ERR_FILE_INVALID_FILE_EXT).param("fileExt", fileExt);

        LocalDate now = DateHelper.currentDate();
        StringBuilder sb = new StringBuilder();

        // 支持不同的业务实体保存到不同的bucket中
        String bucketName = getBucketName(bizObjName, fileExt);
        if (bucketName != null)
            sb.append(IFileServiceClient.BUCKET_PREFIX + bucketName).append('/');

        sb.append('/').append(bizObjName);
        sb.append("/").append(StringHelper.leftPad(String.valueOf(now.getYear()), 4, '0'));
        sb.append('/').append(StringHelper.leftPad(String.valueOf(now.getMonthValue()), 2, '0'));
        sb.append('/').append(StringHelper.leftPad(String.valueOf(now.getDayOfMonth()), 2, '0'));
        sb.append('/').append(fileId);
        if (keepFileExt && !StringHelper.isEmpty(fileExt))
            sb.append('.').append(fileExt);
        return sb.toString();
    }

    protected String getBucketName(String bizObjName, String fileExt) {
        return null;
    }

    @Override
    public FileStatusBean getFileStatus(String fileId, String bizObjName, String objId, String fieldName) {
        NopFileRecord record = getRecord(fileId, bizObjName, objId, fieldName);
        if (record == null)
            return null;
        FileStatusBean ret = new FileStatusBean();
        ret.setFileId(record.getFileId());
        ret.setName(record.getFileName());
        ret.setLastModified(record.getFileLastModified() == null ? -1L : record.getFileLastModified().getTime());
        ret.setSize(record.getFileLength() == null ? -1L : record.getFileLength());
        ret.setExternalPath(getExternalPath(record));
        ret.setPreviewPath(getPreviewPath(record));
        return ret;
    }

    protected String getPreviewPath(NopFileRecord record) {
        return null;
    }

    protected String getExternalPath(NopFileRecord record) {
        return null;
    }

    private NopFileRecord getRecord(String fileId, String bizObjName, String objId, String fieldName) {
        NopFileRecord record = daoProvider.daoFor(NopFileRecord.class).getEntityById(fileId);
        if (record == null)
            return null;
        if (!record.getBizObjName().equals(bizObjName) || !Objects.equals(objId, record.getBizObjId())
                || !Objects.equals(record.getFieldName(), fieldName))
            throw new NopException(ERR_FILE_NOT_ALLOW_ACCESS_FILE)
                    .param(ARG_FILE_ID, fileId)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_BIZ_OBJ_ID, objId)
                    .param(ARG_FIELD_NAME, fieldName);
        return record;
    }

    @Override
    public IResource getFileResource(String fileId, String bizObjName, String objId, String fieldName) {
        IFileRecord record = getFile(fileId);
        if (record == null)
            throw new NopException(ERR_FILE_NOT_EXISTS)
                    .param(ARG_FILE_ID, fileId)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_BIZ_OBJ_ID, objId)
                    .param(ARG_FIELD_NAME, fieldName);

        if (!record.getBizObjName().equals(bizObjName) || !Objects.equals(objId, record.getBizObjId())
                || !Objects.equals(record.getFieldName(), fieldName))
            throw new NopException(ERR_FILE_NOT_ALLOW_ACCESS_FILE)
                    .param(ARG_FILE_ID, fileId)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_BIZ_OBJ_ID, objId)
                    .param(ARG_FIELD_NAME, fieldName);
        return record.getResource();
    }

    @Override
    public void detachFile(String fileId, String bizObjName, String objId, String fieldName) {
        // P1-3.8-002（plan 2386）：与 attachFile 对齐——单 session 内完成校验与删除。
        // runInSession：复用外层 session，无外层时退化为 newSession；
        // 事务归属由 TransactionRegistry 决定，与 session 选择正交。
        ormTemplate.runInSession(session -> {
            detachFileInSession(fileId, bizObjName, objId, fieldName);
            return null;
        });
    }

    private void detachFileInSession(String fileId, String bizObjName, String objId, String fieldName) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.getEntityById(fileId);
        if (record != null) {
            checkAttachPermission(record);
            if (Objects.equals(record.getBizObjName(), bizObjName)
                    && Objects.equals(record.getBizObjId(), objId)
                    && Objects.equals(record.getFieldName(), fieldName)) {
                dao.deleteEntity(record);
                // W-01（plan 2526 裁定①）：isUniqueRef 判定与物理删除之间并发 copy 可交错（引用计数
                // 过期判删→悬挂引用/数据丢失）——物理删除改两段式延迟清理：登记后宽限间隔复验引用
                // 计数仍为零才删。竞态窗口从微秒级窄化至宽限间隔（残余窗口为已声明语义）。
                // removeResource 本体 best-effort 语义零触碰（CL-017 P3 站点）。
                if (isUniqueRef(dao, record)) {
                    registerDedupCleanup(record);
                }
            } else {
                LOG.warn("nop.file.record-not-attached-to-field:fileId={},bizObjName={},objId={},fieldName={},attachedObjName={},attachedObjId={},attachedFieldName={}",
                        fileId, bizObjName, objId, fieldName, record.getBizObjName(), record.getBizObjId(), record.getFieldName());
            }
        }
    }

    protected boolean isUniqueRef(IEntityDao<NopFileRecord> dao, NopFileRecord record) {
        // originFileId与当前记录相同，但是又不是当前记录
        QueryBean query = new QueryBean();
        TreeBean filter = FilterBeans.and(
                eq(NopFileRecord.PROP_NAME_originFileId, record.getOriginFileId()),
                ne(NopFileRecord.PROP_NAME_fileId, record.getFileId()));
        query.addFilter(filter);

        return dao.findFirstByQuery(query) == null;
    }

    /**
     * P1-3.8-002（plan 2386）：attach/detach 归属校验——文件记录只能由创建者或 admin
     * 挂接/卸载（防止劫持他人文件记录并触发物理删除）。IUserContext 为 null 的
     * 引擎内部路径放行（与 GraphQL 层 auth 语义一致：无用户上下文=系统调用）。
     */
    protected void checkAttachPermission(NopFileRecord record) {
        IUserContext userContext = IUserContext.get();
        if (userContext == null)
            return;
        if (userContext.isUserInRole("admin"))
            return;
        // createdBy 由 ORM 审计写入 userRefNo：缺省 userRefNo 回退为 userName（登录名），
        // 开启 nop.auth.use-user-id-for-audit-fields 时才是 userId。归属校验必须与审计口径
        // 一致，否则同一用户上传后 attach 会因 createdBy(登录名) != userId(主键) 被误拒。
        String createdBy = record.getCreatedBy();
        boolean isCreator = java.util.Objects.equals(createdBy, userContext.getUserName())
                || java.util.Objects.equals(createdBy, userContext.getUserId());
        if (!isCreator)
            throw new NopException(io.nop.file.core.FileErrors.ERR_FILE_NO_ATTACH_PERMISSION)
                    .param(ARG_FILE_ID, record.getFileId())
                    .param("createdBy", createdBy)
                    .param("userId", userContext.getUserId());
    }

    @Override
    public void attachFile(String fileId, String bizObjName, String objId, String fieldName) {
        // P1-3.8-002（plan 2386）：load 与 save 放入同一 session 作用域，避免逐 DAO 调用
        // 各开一次性 session 导致 saveOrUpdate 误走 save 分支（save-entity-not-transient）。
        // runInSession：复用外层 session，无外层时退化为 newSession；
        // 事务归属由 TransactionRegistry 决定，与 session 选择正交。
        ormTemplate.runInSession(session -> {
            IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
            NopFileRecord record = dao.requireEntityById(fileId);
            if (!Objects.equals(record.getBizObjName(), bizObjName))
                throw new NopException(ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ)
                        .param(ARG_FILE_ID, fileId).param(ARG_BIZ_OBJ_NAME, bizObjName).param(ARG_FILE_OBJ_NAME, record.getBizObjName());
            checkAttachPermission(record);
            record.setBizObjId(objId);
            record.setFieldName(fieldName);
            dao.saveOrUpdateEntity(record);
            return null;
        });
    }

    @Override
    public String copyFile(String fileId, String newBizObjName, String newObjId, String newFieldName) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.requireEntityById(fileId);
        NopFileRecord newRecord = dao.newEntity();
        newRecord.setBizObjName(newBizObjName);
        newRecord.setBizObjId(newObjId);
        newRecord.setFieldName(newFieldName);
        newRecord.setOriginFileId(record.getOriginFileId());
        newRecord.setFileExt(record.getFileExt());
        newRecord.setFileLength(record.getFileLength());
        newRecord.setFileName(record.getFileName());
        newRecord.setFilePath(record.getFilePath());
        newRecord.setFileHash(record.getFileHash());
        newRecord.setFileLastModified(record.getFileLastModified());
        newRecord.setMimeType(record.getMimeType());
        newRecord.setFileId(newFileId());
        newRecord.setIsPublic(false);
        dao.saveEntity(newRecord);
        return newRecord.getFileId();
    }

    @Override
    public void changePublic(String fileId, boolean isPublic) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.requireEntityById(fileId);
        record.setIsPublic(isPublic);
    }
}
