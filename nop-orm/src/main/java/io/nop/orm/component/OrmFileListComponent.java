/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.component;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.util.StringHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntityFileStore;
import io.nop.orm.OrmConstants;

import java.util.ArrayList;
import java.util.List;

public class OrmFileListComponent extends AbstractOrmComponent {
    public static final String PROP_NAME_filePath = "filePath";

    public String getFilePath() {
        return ConvertHelper.toString(internalGetPropValue(PROP_NAME_filePath));
    }

    public void setFilePath(String value) {
        internalSetPropValue(PROP_NAME_filePath, value);
    }

    public List<String> getFilePaths() {
        return StringHelper.split(getFilePath(), ',');
    }

    public void setFilePaths(List<String> filePaths) {
        setFilePath(StringHelper.join(filePaths, ","));
    }

    @Override
    public void onEntityFlush() {
        IOrmEntity entity = orm_owner();
        int propId = getColPropId(PROP_NAME_filePath);
        if (entity.orm_state().isUnsaved() || entity.orm_propDirty(propId)) {
            IBeanProvider beanProvider = entity.orm_enhancer().getBeanProvider();
            // 有可能没有引入file store支持
            if (!beanProvider.containsBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE))
                return;

            IOrmEntityFileStore fileStore = (IOrmEntityFileStore) beanProvider.getBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);
            String oldValue = (String) entity.orm_propOldValue(propId);

            List<String> paths = StringHelper.split(getFilePath(), ',');
            List<String> oldPaths = StringHelper.split(oldValue, ',');
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
        IBeanProvider beanProvider = entity.orm_enhancer().getBeanProvider();
        IOrmEntityFileStore fileStore = (IOrmEntityFileStore) beanProvider.getBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);

        List<String> paths = StringHelper.split(getFilePath(), ',');
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
