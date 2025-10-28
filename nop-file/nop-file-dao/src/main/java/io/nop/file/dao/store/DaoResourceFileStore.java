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
import io.nop.file.core.FileConstants;
import io.nop.file.core.IFileRecord;
import io.nop.file.core.IFileStore;
import io.nop.file.core.UploadRequestBean;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.orm.IOrmEntityFileStore;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

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
import static io.nop.file.core.FileErrors.ERR_FILE_LENGTH_EXCEED_LIMIT;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_ALLOW_ACCESS_FILE;
import static io.nop.file.core.FileErrors.ERR_FILE_NOT_EXISTS;

/**
 * 将上传文件存放在本地目录下
 */
public class DaoResourceFileStore implements IFileStore, IOrmEntityFileStore {
    static final Logger LOG = LoggerFactory.getLogger(DaoResourceFileStore.class);
    private IDaoProvider daoProvider;

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

        NopFileRecord example = new NopFileRecord();
        example.setCreatedBy(ownerId);
        example.setBizObjId(FileConstants.TEMP_BIZ_OBJ_ID);
        dao.deleteByExample(example);
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


    private boolean removeResource(String path) {
        try {
            IResource resource = resourceStore.getResource(path);
            return resource.delete();
        } catch (Exception e) {
            LOG.error("nop.file.remove-file-fail:path={}", path, e);
            return false;
        }
    }

    protected String newFileId() {
        return StringHelper.generateUUID();
    }

    protected String newPath(String bizObjName, String fileId, String fileExt) {
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
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.getEntityById(fileId);
        if (record != null) {
            if (Objects.equals(record.getBizObjName(), bizObjName)
                    && Objects.equals(record.getBizObjId(), objId)
                    && Objects.equals(record.getFieldName(), fieldName)) {
                dao.deleteEntity(record);
                if (isUniqueRef(dao, record)) {
                    removeResource(record.getFilePath());
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

    @Override
    public void attachFile(String fileId, String bizObjName, String objId, String fieldName) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.requireEntityById(fileId);
        if (!Objects.equals(record.getBizObjName(), bizObjName))
            throw new NopException(ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ)
                    .param(ARG_FILE_ID, fileId).param(ARG_BIZ_OBJ_NAME, bizObjName).param(ARG_FILE_OBJ_NAME, record.getBizObjName());
        record.setBizObjId(objId);
        record.setFieldName(fieldName);
        dao.saveOrUpdateEntity(record);
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
