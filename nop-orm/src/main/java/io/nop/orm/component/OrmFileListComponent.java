/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.component;

import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntityFileStore;
import io.nop.orm.OrmConstants;
import io.nop.orm.support.OrmEntityHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrmFileListComponent extends AbstractOrmComponent {
    public static final String PROP_NAME_filePath = "filePath";

    public String getFilePath() {
        return ConvertHelper.toString(internalGetPropValue(PROP_NAME_filePath));
    }

    public void setFilePath(String value) {
        internalSetPropValue(PROP_NAME_filePath, value);
    }

    public List<String> getFilePaths() {
        return OrmEntityHelper.parseFileList(getFilePath());
    }

    public void setFilePaths(List<String> filePaths) {
        setFilePath(OrmEntityHelper.joinFileList(filePaths));
    }


    public List<IResource> loadResources() {
        IOrmEntity entity = orm_owner();
        IOrmEntityFileStore fileStore = (IOrmEntityFileStore) getBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);

        List<String> paths = getFilePaths();
        if (paths == null || paths.isEmpty())
            return new ArrayList<>();

        List<IResource> resources = new ArrayList<>();
        for (String path : paths) {
            String fileId = fileStore.decodeFileId(path);
            IResource resource = fileStore.getFileResource(fileId, getBizObjName(), entity.orm_idString(), PROP_NAME_filePath);
            resources.add(resource);
        }

        return resources;
    }


    public List<String> getFileIds() {
        List<String> paths = getFilePaths();
        if (paths == null || paths.isEmpty())
            return null;

        IOrmEntityFileStore fileStore = (IOrmEntityFileStore) tryGetBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);
        if (fileStore == null) {
            return paths.stream().map(filePath -> StringHelper.lastPart(filePath, '/')).collect(Collectors.toList());
        }
        return paths.stream().map(fileStore::decodeFileId).collect(Collectors.toList());
    }

    public List<FileStatusBean> getFileStatusList() {
        IOrmEntity entity = orm_owner();
        IOrmEntityFileStore fileStore = (IOrmEntityFileStore) getBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);

        List<String> paths = getFilePaths();
        if (paths == null || paths.isEmpty())
            return new ArrayList<>();

        List<FileStatusBean> fileStatuses = new ArrayList<>();
        String propName = entity.orm_propName(getColPropId(PROP_NAME_filePath));
        String bizObjName = getBizObjName();

        for (String path : paths) {
            String fileId = fileStore.decodeFileId(path);
            FileStatusBean fileStatus = fileStore.getFileStatus(fileId, bizObjName, entity.orm_idString(), propName);
            fileStatuses.add(fileStatus);
        }

        return fileStatuses;
    }

    @Override
    public void onEntityFlush() {
        IOrmEntity entity = orm_owner();
        int propId = getColPropId(PROP_NAME_filePath);
        if (entity.orm_state().isUnsaved() || entity.orm_propDirty(propId)) {

            IOrmEntityFileStore fileStore = (IOrmEntityFileStore) tryGetBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);
            if (fileStore == null)
                return;

            String oldValue = (String) entity.orm_propOldValue(propId);

            List<String> paths = getFilePaths();
            List<String> oldPaths = OrmEntityHelper.parseFileList(oldValue);
            if (paths == null)
                paths = new ArrayList<>();
            if (oldPaths == null)
                oldPaths = new ArrayList<>();

            String propName = entity.orm_propName(propId);

            String bizObjName = getBizObjName();

            for (String path : paths) {
                String fileId = fileStore.decodeFileId(path);

                if (oldPaths.contains(path))
                    continue;

                if (!StringHelper.isEmpty(fileId)) {
                    fileStore.attachFile(fileId, bizObjName, entity.orm_idString(), propName);
                }
            }

            for (String oldPath : oldPaths) {
                String fileId = fileStore.decodeFileId(oldPath);

                if (paths.contains(oldPath))
                    continue;

                if (!StringHelper.isEmpty(fileId)) {
                    fileStore.detachFile(fileId, bizObjName, entity.orm_idString(), propName);
                }
            }
        }
    }

    @Override
    public void onEntityDelete(boolean logicalDelete) {
        if (logicalDelete)
            return;

        IOrmEntity entity = orm_owner();
        IOrmEntityFileStore fileStore = (IOrmEntityFileStore) tryGetBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);
        if (fileStore == null)
            return;

        List<String> paths = getFilePaths();
        if (paths == null || paths.isEmpty())
            return;

        int propId = getColPropId(PROP_NAME_filePath);
        String propName = entity.orm_propName(propId);
        String bizObjName = getBizObjName();

        for (String path : paths) {
            String fileId = fileStore.decodeFileId(path);
            fileStore.detachFile(fileId, bizObjName, entity.orm_idString(), propName);
        }
    }

    public String getBizObjName() {
        IOrmEntity entity = orm_owner();
        return StringHelper.lastPart(entity.orm_entityName(), '.');
    }
}
