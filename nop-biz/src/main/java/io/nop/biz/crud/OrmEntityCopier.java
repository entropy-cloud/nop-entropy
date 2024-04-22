/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmEntityState;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.utils.OrmModelHelper;
import io.nop.orm.support.OrmCompositePk;
import io.nop.orm.support.OrmEntityHelper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.biz.crud.BizSchemaHelper.getPropSchema;
import static io.nop.orm.OrmErrors.ARG_PROP_CLASS;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_COPY_ENTITY_PROP_NOT_COLLECTION;

/**
 * 实现json对象或者DTO对象与数据库实体对象之间的同步。例如前台修改实体对象之后提交到后台，利用此函数更新到对象图上。
 */
public class OrmEntityCopier {
    private final IBizObjectManager bizObjectManager;
    private final IDaoProvider daoProvider;

    private Map<String, String> relationChangeTypes;

    /**
     * bizObjName是接口层面看到的业务对象名，IBizObjManager根据bizObjName装载对应业务对象。
     * 指定的关联对象名会对应找到相关的meta文件，而meta中可以提供一些扩展信息，例如setter等
     *
     * @param daoProvider 用于创建或者装载关联实体对象
     */
    public OrmEntityCopier(IDaoProvider daoProvider, IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
        this.daoProvider = daoProvider;
    }

    public OrmEntityCopier(IDaoProvider daoProvider) {
        this(daoProvider, null);
    }

    public OrmEntityCopier addRelationCopyOptions(String relationName, String chgType) {
        if (relationChangeTypes == null) {
            relationChangeTypes = new HashMap<>();
        }
        relationChangeTypes.put(relationName, chgType);
        return this;
    }

    public String getRelationChangeTypes(String relationName) {
        if (relationChangeTypes == null)
            return null;
        return relationChangeTypes.get(relationName);
    }

    public void copyToEntity(Object src, IOrmEntity target, FieldSelectionBean selection, String action) {
        this.copyToEntity(src, target, selection, null, null, action, XLang.newEvalScope());
    }

    /**
     * 将java bean或者json对象上的指定属性设置到实体对象上。根据实体属性的类型，有可能创建新的实体对象，或者装载关联实体对象。
     *
     * @param src       来源数据对象
     * @param target    需要被更新的目标实体对象
     * @param selection 指定从来源对象的哪些属性读取并更新到目标实体对象的哪些属性上
     */
    public void copyToEntity(Object src, IOrmEntity target, FieldSelectionBean selection, IObjSchema objMeta,
                             String baseBizObjName, String action, IEvalScope scope) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(src.getClass());

        Set<String> ignoreAutoExprProps = new HashSet<>();

        if (selection == null) {
            if (src instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) src;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String name = entry.getKey();
                    // 忽略_chgType属性
                    if (name.startsWith(DaoConstants.PROP_CHANGE_TYPE))
                        continue;

                    IObjPropMeta propMeta = getProp(objMeta, name);
                    if (propMeta != null) {
                        // 如果明确从前台提交参数，那么以提交的值为准。如果禁止前台提交，应该设置字段的insertable=false,updatable=false
                        if (propMeta.getAutoExpr() != null) {
                            ignoreAutoExprProps.add(propMeta.getName());
                        }
                    }
                    copyField(beanModel, map, src, target, name, name, null, propMeta, baseBizObjName, scope);
                }
            } else {
                beanModel.forEachSerializableProp(propModel -> {
                    String name = propModel.getName();
                    IObjPropMeta propMeta = getProp(objMeta, name);
                    if (propMeta != null && propMeta.getAutoExpr() != null) {
                        ignoreAutoExprProps.add(propMeta.getName());
                    }
                    copyField(beanModel, null, src, target, name, name, null, propMeta, baseBizObjName, scope);
                });
            }
        } else {
            Map<String, Object> map = src instanceof Map ? (Map<String, Object>) src : null;
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String name = entry.getKey();
                FieldSelectionBean field = entry.getValue();
                String from = field.getName();
                if (from == null)
                    from = name;

                if (map != null && !map.containsKey(from)) {
                    continue;
                }

                IObjPropMeta propMeta = getProp(objMeta, from);
                if (propMeta != null) {
                    if (propMeta.getAutoExpr() != null)
                        ignoreAutoExprProps.add(propMeta.getName());
                }
                copyField(beanModel, map, src, target, from, name, field, propMeta, baseBizObjName, scope);
            }
        }

        if (objMeta != null)
            AutoExprRunner.runAutoExpr(action, target, src, objMeta, scope, ignoreAutoExprProps);
    }

    IObjPropMeta getProp(IObjSchema objMeta, String name) {
        if (objMeta == null)
            return null;
        return objMeta.getProp(name);
    }

    private void copyField(IBeanModel beanModel, Map<String, Object> map,
                           Object src, IOrmEntity target, String from, String name,
                           FieldSelectionBean field, IObjPropMeta propMeta, String baseBizObjName, IEvalScope scope) {
        Object fromValue = beanModel.getProperty(src, from);
        if (propMeta != null) {
            IEvalAction setter = propMeta.getSetter();
            if (setter != null) {
                scope = scope.newChildScope();
                scope.setLocalValue(null, OrmConstants.VAR_ENTITY, target);
                scope.setLocalValue(null, OrmConstants.VAR_VALUE, fromValue);
                scope.setLocalValue(null, OrmConstants.VAR_PROP_META, propMeta);
                setter.invoke(scope);
                return;
            }

            // 虚拟字段不会设置到实体上
            if (propMeta.isVirtual())
                return;

            if (propMeta.getMapToProp() != null) {
                BeanTool.setComplexProperty(target, propMeta.getMapToProp(), fromValue);
                return;
            }
        }
        IEntityModel entityModel = target.orm_entityModel();

        IEntityPropModel propModel = entityModel.getProp(name, true);
        if (propModel == null) {
            // 不是实体属性，则直接认为是java bean属性设置
            target.orm_propValueByName(name, fromValue);
        } else {
            if (propModel.isToOneRelation()) {
                IObjSchema subSchema = getPropSchema(propMeta, false, bizObjectManager, baseBizObjName);
                copyRefEntity(fromValue, map, target, (IEntityRelationModel) propModel, field, subSchema, baseBizObjName, scope);
            } else if (propModel.isToManyRelation()) {
                IObjSchema subSchema = getPropSchema(propMeta, true, bizObjectManager, baseBizObjName);
                copyRefEntitySet(fromValue, map, target, (IEntityRelationModel) propModel, field, subSchema, baseBizObjName, scope);
            } else {
                target.orm_propValueByName(name, fromValue);
            }
        }
    }

    private void copyRefEntity(Object fromValue, Map<String, Object> map,
                               IOrmEntity target, IEntityRelationModel propModel,
                               FieldSelectionBean field, IObjSchema objMeta, String baseBizObjName, IEvalScope scope) {
        if (StringHelper.isEmptyObject(fromValue)) {
            target.orm_propValueByName(propModel.getName(), null);
        } else {
            String propName = propModel.getName();
            if (StdDataType.fromJavaClass(fromValue.getClass()).isSimpleType()) {
                Object refEntity = daoProvider.dao(propModel.getRefEntityName()).loadEntityById(fromValue);
                target.orm_propValueByName(propName, refEntity);
            } else {
                String chgType = getRelationChangeTypes(OrmModelHelper.buildRelationName(propModel));
                if (chgType == null && map != null) {
                    chgType = (String) map.get(DaoConstants.PROP_CHANGE_TYPE + '_' + propName);
                }

                // 更新关联实体
                Object id = getId(fromValue, propModel.getRefEntityModel());
                if (StringHelper.isEmptyObject(id)) {
                    // 没有主键，需要新增对象
                    if (field != null && !field.hasField()) {
                        target.orm_propValueByName(propName, null);
                    } else {
                        if (chgType == null || chgType.contains(DaoConstants.CHANGE_TYPE_ADD)) {
                            Object refEntity = daoProvider.dao(propModel.getRefEntityName()).newEntity();
                            copyToEntity(fromValue, (IOrmEntity) refEntity, field, objMeta, baseBizObjName,
                                    BizConstants.METHOD_SAVE, scope);
                            target.orm_propValueByName(propName, refEntity);
                        }
                    }
                } else {
                    if (chgType == null || chgType.contains(DaoConstants.CHANGE_TYPE_UPDATE)) {
                        IOrmEntity refEntity = (IOrmEntity) daoProvider.dao(propModel.getRefEntityName()).loadEntityById(id);
                        copyToEntity(fromValue, refEntity, field, objMeta, baseBizObjName,
                                BizConstants.METHOD_UPDATE, scope);

                        // 关联实体不存在，是否需要新建？
                        if (refEntity.orm_state().isMissing()) {
                            refEntity.orm_state(OrmEntityState.TRANSIENT);
                        }
                        target.orm_propValueByName(propName, refEntity);
                    }
                }
            }
        }
    }

    private void copyRefEntitySet(Object fromValue, Map<String, Object> map, IOrmEntity target, IEntityRelationModel propModel,
                                  FieldSelectionBean field, IObjSchema objMeta, String baseBizObjName,
                                  IEvalScope scope) {
        String propName = propModel.getName();
        IOrmEntitySet<IOrmEntity> refSet = target.orm_refEntitySet(propName);
        String chgType = getRelationChangeTypes(refSet.orm_collectionName());

        if (chgType == null && map != null) {
            chgType = (String) map.get(DaoConstants.PROP_CHANGE_TYPE + '_' + propName);
        }

        if (StringHelper.isEmptyObject(fromValue)) {
            if (chgType == null || chgType.contains(DaoConstants.CHANGE_TYPE_DELETE))
                refSet.clear();
        } else if (fromValue instanceof Collection) {
            Collection<?> c = (Collection<?>) fromValue;
            if (c.isEmpty()) {
                if (chgType == null || chgType.contains(DaoConstants.CHANGE_TYPE_DELETE))
                    refSet.clear();
            } else {
                syncEntitySet(c, refSet, propModel.getKeyProp(), field, propModel.getRefEntityName(), propModel, objMeta,
                        baseBizObjName, scope);
            }
        } else {
            throw new OrmException(ERR_ORM_COPY_ENTITY_PROP_NOT_COLLECTION).param(ARG_PROP_NAME, propModel.getName())
                    .param(ARG_PROP_CLASS, fromValue.getClass());
        }
    }

    void syncEntitySet(Collection<?> c, IOrmEntitySet<IOrmEntity> refSet, String keyProp, FieldSelectionBean field,
                       String refEntityName, IEntityRelationModel refModel, IObjSchema objMeta, String baseBizObjName, IEvalScope scope) {
        Set<IOrmEntity> ret = new LinkedHashSet<>();
        IEntityDao<IOrmEntity> dao = daoProvider.dao(refEntityName);

        String chgType = getRelationChangeTypes(refSet.orm_collectionName());

        for (Object item : c) {
            if (StringHelper.isEmptyObject(item))
                continue;

            boolean simple = StdDataType.isSimpleType(item.getClass().getName());
            if (simple) {
                IOrmEntity refEntity = dao.loadEntityById(item);
                ret.add(refEntity);
            } else {
                assignRefProps(item, refSet.orm_owner(), refModel);

                // 更新关联实体
                Object id = getId(item, refModel.getRefEntityModel());
                if (StringHelper.isEmptyObject(id)) {
                    // 没有主键，需要新增对象
                    if (field != null && !field.hasField()) {
                        continue;
                    } else {
                        IOrmEntity refEntity = null;
                        if (keyProp != null) {
                            String srcProp = getRefField(field, keyProp);
                            Object keyValue = BeanTool.instance().getProperty(item, srcProp);
                            // 按照keyProp查找集合中已经存在的实体
                            if (keyValue != null)
                                refEntity = (IOrmEntity) refSet.prop_get(keyValue.toString());
                        }

                        String action = null;
                        if (refEntity == null) {
                            // 如果明确配置了不允许向集合中添加元素
                            if (chgType != null && !chgType.contains(DaoConstants.CHANGE_TYPE_ADD))
                                continue;
                            refEntity = dao.newEntity();
                            action = BizConstants.METHOD_SAVE;
                        } else {
                            // 如果明确配置了不允许更新集合中的元素
                            if (chgType != null && !chgType.contains(DaoConstants.CHANGE_TYPE_UPDATE))
                                continue;
                            action = BizConstants.METHOD_UPDATE;
                        }
                        copyToEntity(item, refEntity, field, objMeta, baseBizObjName, action, scope);
                        ret.add(refEntity);
                    }
                } else {
                    if (chgType == null || chgType.contains(DaoConstants.CHANGE_TYPE_UPDATE)) {
                        IOrmEntity refEntity = dao.loadEntityById(id);
                        copyToEntity(item, refEntity, field, objMeta, baseBizObjName, BizConstants.METHOD_UPDATE, scope);
                        ret.add(refEntity);
                    }
                }
            }
        }

        // 删除没有在src集合中出现的条目
        if (chgType == null || chgType.contains(DaoConstants.CHANGE_TYPE_DELETE))
            refSet.clear();

        refSet.addAll(ret);
    }

    private void assignRefProps(Object item, IOrmEntity owner, IEntityRelationModel refModel) {
        for (IEntityJoinConditionModel join : refModel.getJoin()) {
            String rightProp = join.getRightProp();
            if (rightProp != null) {
                Object leftValue = OrmEntityHelper.getLeftValue(join, owner);
                BeanTool.instance().setProperty(item, rightProp, leftValue);
            }
        }
    }

    Object getId(Object bean, IEntityModel entityModel) {
        Object id = BeanTool.instance().getProperty(bean, OrmConstants.PROP_ID);
        if (!StringHelper.isEmptyObject(id))
            return id;

        if (entityModel.isCompositePk()) {
            List<? extends IColumnModel> pkCols = entityModel.getPkColumns();
            Object[] pkValues = new Object[pkCols.size()];
            for (int i = 0, n = pkCols.size(); i < n; i++) {
                pkValues[i] = BeanTool.instance().getProperty(bean, pkCols.get(i).getName());
            }
            return OrmCompositePk.buildNotNull(entityModel.getPkColumnNames(), pkValues);
        } else {
            String pkName = entityModel.getPkColumns().get(0).getName();
            return BeanTool.instance().getProperty(bean, pkName);
        }
    }

    String getRefField(FieldSelectionBean field, String keyProp) {
        if (field == null)
            return keyProp;
        FieldSelectionBean subField = field.getField(keyProp);
        if (subField == null) {
            return keyProp;
        }
        String name = subField.getName();
        if (name == null)
            name = keyProp;
        return name;
    }
}