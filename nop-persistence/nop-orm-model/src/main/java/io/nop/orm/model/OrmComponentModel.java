/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.orm.model._gen._OrmComponentModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ARG_COMPONENT_CLASS;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_UNKNOWN_COMPONENT_PROP;

public class OrmComponentModel extends _OrmComponentModel implements IEntityComponentModel {
    private OrmEntityModel ownerEntityModel;

    // plan 2517 R2-14 裁定③（C1-015）：解析期预计算（OrmEntityModelInitializer 构造尾调用 precompute），
    // 运行期只读；volatile 保证一次性发布安全（消除先发布引用后填充窗口）。
    // 手工构建（未经 initializer）的模型走 safe-publish 惰性兜底：局部填充完整后最后赋引用
    private volatile Map<String, Integer> colPropIds;
    private volatile int[] propIds;

    public OrmComponentModel() {

    }

    @Override
    public OrmEntityModel getOwnerEntityModel() {
        return ownerEntityModel;
    }

    public void setOwnerEntityModel(OrmEntityModel ownerEntityModel) {
        this.ownerEntityModel = ownerEntityModel;
    }

    @Override
    public Map<String, Integer> getColumnPropIdMap() {
        Map<String, Integer> result = colPropIds;
        if (result == null) {
            // safe-publish 惰性兜底：局部构建完整后最后赋引用（先发布引用后填充窗口消除）
            Map<String, Integer> map = new HashMap<>();
            for (OrmComponentPropModel propModel : this.getProps()) {
                map.put(propModel.getName(), propModel.getColumnPropId());
            }
            colPropIds = map;
            result = map;
        }
        return result;
    }

    /**
     * plan 2517 R2-14 裁定③（C1-015）：解析期预计算。由 OrmEntityModelInitializer 在组件列解析
     * （checkPropNames 设置 columnModel）完成后调用一次，此后运行期 getter 纯读。
     */
    public void precompute() {
        Map<String, Integer> map = new HashMap<>();
        for (OrmComponentPropModel propModel : this.getProps()) {
            map.put(propModel.getName(), propModel.getColumnPropId());
        }
        List<OrmComponentPropModel> props = getProps();
        int[] ids = new int[props == null ? 0 : props.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = props.get(i).getColumnPropId();
        }
        this.colPropIds = map;
        this.propIds = ids;
    }

//    @Override
//    public Object getPropValue(IOrmEntity entity) {
//        return entity.orm_propValueByName(getName());
//    }
//
//    @Override
//    public void setPropValue(IOrmEntity entity, Object value) {
//        entity.orm_propValueByName(getName(), value);
//    }

    @Override
    public IEntityComponentPropModel requireProp(String name) {
        IEntityComponentPropModel prop = getProp(name);
        if (prop == null)
            throw new NopException(ERR_ORM_MODEL_UNKNOWN_COMPONENT_PROP).source(this)
                    .param(ARG_COMPONENT_CLASS, getClassName()).param(ARG_PROP_NAME, name);
        return prop;
    }

    @Override
    public List<? extends IColumnModel> getColumns() {
        List<OrmColumnModel> cols = new ArrayList<>(getProps().size());
        for (OrmComponentPropModel prop : getProps()) {
            cols.add(prop.getColumnModel());
        }
        return cols;
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.ANY;
    }

    @Override
    public boolean isSingleColumn() {
        return false;
    }

    @Override
    public boolean hasLazyLoadColumn() {
        for (OrmComponentPropModel prop : getProps()) {
            if (prop.getColumnModel().isLazy())
                return true;
        }
        return false;
    }

    @Override
    public int getColumnPropId() {
        return getColumnPropIds()[0];
    }

    @Override
    public int[] getColumnPropIds() {
        int[] result = propIds;
        if (result == null) {
            // 按 getProps() 的声明顺序构造。getColumnPropIdMap 是 HashMap，
            // 其 values() 的哈希序与声明顺序无关，会导致 getColumnPropId() 取值不确定。
            // safe-publish 惰性兜底：局部数组完整填充后最后赋引用
            List<OrmComponentPropModel> props = getProps();
            int[] ids = new int[props == null ? 0 : props.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = props.get(i).getColumnPropId();
            }
            propIds = ids;
            result = ids;
        }
        return result;
    }

    @Override
    public String getAliasPropPath() {
        return null;
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.COMPONENT;
    }

    @Override
    public String getComment() {
        return null;
    }

}
