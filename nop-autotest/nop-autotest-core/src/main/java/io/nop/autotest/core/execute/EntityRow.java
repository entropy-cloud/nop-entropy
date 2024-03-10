/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.execute;

import io.nop.autotest.core.data.AutoTestVars;
import io.nop.dao.api.EntityChangeType;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.HashMap;
import java.util.Map;

import static io.nop.autotest.core.execute.AutoTestHelper.getVarNamePrefix;
import static io.nop.autotest.core.execute.AutoTestHelper.isVarCol;

public class EntityRow {
    private final String id;
    private final Map<String, Object> initData = new HashMap<>();
    private final Map<String, Object> changedData = new HashMap<>();

    private boolean load;
    private EntityChangeType changeType;

    public EntityRow(String id) {
        this.id = id;
    }

    public EntityChangeType getChangeType() {
        return changeType;
    }

    public String getId() {
        return id;
    }

    public boolean isLoadData() {
        return load && !initData.isEmpty();
    }

    public boolean hasChangedData() {
        return !changedData.isEmpty();
    }

    public Map<String, Object> getInitData() {
        return initData;
    }

    public Map<String, Object> getChangedData() {
        return changedData;
    }

    public synchronized void onLoad(IOrmEntity entity, IEntityModel entityModel) {
        // 在修改前加载才表示加载初始化数据
        if (changeType == null)
            load = true;

        entity.orm_forEachInitedProp((value, propId) -> {
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            initData.putIfAbsent(col.getCode(), value);
        });
    }

    public synchronized void onSave(IOrmEntity entity, IEntityModel entityModel) {
        this.changeType = EntityChangeType.A;
        onChange(entity, entityModel, true);
    }

    public synchronized void onUpdate(IOrmEntity entity, IEntityModel entityModel) {
        if (this.changeType != EntityChangeType.A)
            this.changeType = EntityChangeType.U;
        onChange(entity, entityModel, false);
    }

    public synchronized void onDelete(IOrmEntity entity, IEntityModel entityModel) {
        this.changeType = EntityChangeType.D;
        onChange(entity, entityModel, false);
    }

    private void onChange(IOrmEntity entity, IEntityModel entityModel, boolean save) {
        entity.orm_forEachInitedProp((value, propId) -> {
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            if (save || entity.orm_propDirty(propId)) {
                registerVar(col, value);
            }
            changedData.put(col.getCode(), value);
        });
    }

    private void registerVar(IColumnModel colModel, Object value) {
        if (isVarCol(colModel)) {
            AutoTestVars.addVar(getVarNamePrefix(colModel), value);
        }
    }
}