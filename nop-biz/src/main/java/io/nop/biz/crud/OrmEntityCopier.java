/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmConstants;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.utils.OrmModelHelper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private Map<String, RelationCopyOptions> relationOptions;

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

    public OrmEntityCopier addRelationCopyOptions(String relationName, RelationCopyOptions options) {
        if (relationOptions == null) {
            relationOptions = new HashMap<>();
        }
        relationOptions.put(relationName, options);
        return this;
    }

    public RelationCopyOptions getRelationOptions(String relationName) {
        if (relationOptions == null)
            return null;
        return relationOptions.get(relationName);
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

        Set<String> ignoreProps = new HashSet<>();

        if (selection == null) {
            if (src instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) src;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String name = entry.getKey();
                    IObjPropMeta propMeta = getProp(objMeta, name);
                    if (propMeta != null && propMeta.getAutoExpr() != null) {
                        ignoreProps.add(propMeta.getName());
                    }
                    copyField(beanModel, src, target, name, name, null, propMeta, baseBizObjName, scope);
                }
            } else {
                beanModel.forEachSerializableProp(propModel -> {
                    String name = propModel.getName();
                    IObjPropMeta propMeta = getProp(objMeta, name);
                    if (propMeta != null && propMeta.getAutoExpr() != null) {
                        ignoreProps.add(propMeta.getName());
                    }
                    copyField(beanModel, src, target, name, name, null, propMeta, baseBizObjName, scope);
                });
            }
        } else {
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String name = entry.getKey();
                FieldSelectionBean field = entry.getValue();
                String from = field.getName();
                if (from == null)
                    from = name;

                IObjPropMeta propMeta = getProp(objMeta, from);
                if (propMeta != null && propMeta.getAutoExpr() != null) {
                    ignoreProps.add(propMeta.getName());
                }
                copyField(beanModel, src, target, from, name, field, propMeta, baseBizObjName, scope);
            }
        }

        if (objMeta != null)
            AutoExprRunner.runAutoExpr(action, target, src, objMeta, scope, ignoreProps);
    }

    IObjPropMeta getProp(IObjSchema objMeta, String name) {
        if (objMeta == null)
            return null;
        return objMeta.getProp(name);
    }

    private void copyField(IBeanModel beanModel, Object src, IOrmEntity target, String from, String name,
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
        }
        IEntityModel entityModel = target.orm_entityModel();

        IEntityPropModel propModel = entityModel.getProp(name, true);
        if (propModel == null) {
            // 不是实体属性，则直接认为是java bean属性设置
            target.orm_propValueByName(name, fromValue);
        } else {
            if (propModel.isToOneRelation()) {
                IObjSchema subSchema = getPropSchema(propMeta, false, bizObjectManager, baseBizObjName);
                copyRefEntity(fromValue, target, (IEntityRelationModel) propModel, field, subSchema, baseBizObjName, scope);
            } else if (propModel.isToManyRelation()) {
                IObjSchema subSchema = getPropSchema(propMeta, true, bizObjectManager, baseBizObjName);
                copyRefEntitySet(fromValue, target, (IEntityRelationModel) propModel, field, subSchema, baseBizObjName, scope);
            } else {
                target.orm_propValueByName(name, fromValue);
            }
        }
    }

    private void copyRefEntity(Object fromValue, IOrmEntity target, IEntityRelationModel propModel,
                               FieldSelectionBean field, IObjSchema objMeta, String baseBizObjName, IEvalScope scope) {
        if (StringHelper.isEmptyObject(fromValue)) {
            target.orm_propValueByName(propModel.getName(), null);
        } else {
            String propName = propModel.getName();
            if (StdDataType.fromJavaClass(fromValue.getClass()).isSimpleType()) {
                Object refEntity = daoProvider.dao(propModel.getRefEntityName()).loadEntityById(fromValue);
                target.orm_propValueByName(propName, refEntity);
            } else {
                RelationCopyOptions options = getRelationOptions(OrmModelHelper.buildRelationName(propModel));

                // 更新关联实体
                Object id = BeanTool.instance().getProperty(fromValue, OrmConstants.PROP_ID);
                if (StringHelper.isEmptyObject(id)) {
                    // 没有主键，需要新增对象
                    if (field != null && !field.hasField()) {
                        target.orm_propValueByName(propName, null);
                    } else {
                        if (options == null || options.isAllowAdd()) {
                            Object refEntity = daoProvider.dao(propModel.getRefEntityName()).newEntity();
                            copyToEntity(fromValue, (IOrmEntity) refEntity, field, objMeta, baseBizObjName,
                                    BizConstants.METHOD_SAVE, scope);
                            target.orm_propValueByName(propName, refEntity);
                        }
                    }
                } else {
                    if (options == null || options.isAllowUpdate()) {
                        Object refEntity = daoProvider.dao(propModel.getRefEntityName()).loadEntityById(id);
                        copyToEntity(fromValue, (IOrmEntity) refEntity, field, objMeta, baseBizObjName,
                                BizConstants.METHOD_UPDATE, scope);
                        target.orm_propValueByName(propName, refEntity);
                    }
                }
            }
        }
    }

    private void copyRefEntitySet(Object fromValue, IOrmEntity target, IEntityRelationModel propModel,
                                  FieldSelectionBean field, IObjSchema objMeta, String baseBizObjName,
                                  IEvalScope scope) {
        String propName = propModel.getName();
        IOrmEntitySet<IOrmEntity> refSet = target.orm_refEntitySet(propName);
        RelationCopyOptions options = getRelationOptions(refSet.orm_collectionName());

        if (StringHelper.isEmptyObject(fromValue)) {
            if (options == null || options.isAllowDelete())
                refSet.clear();
        } else if (fromValue instanceof Collection) {
            Collection<?> c = (Collection<?>) fromValue;
            if (c.isEmpty()) {
                if (options == null || options.isAllowDelete())
                    refSet.clear();
            } else {
                syncEntitySet(c, refSet, propModel.getKeyProp(), field, propModel.getRefEntityName(), objMeta,
                        baseBizObjName, scope);
            }
        } else {
            throw new OrmException(ERR_ORM_COPY_ENTITY_PROP_NOT_COLLECTION).param(ARG_PROP_NAME, propModel.getName())
                    .param(ARG_PROP_CLASS, fromValue.getClass());
        }
    }

    void syncEntitySet(Collection<?> c, IOrmEntitySet<IOrmEntity> refSet, String keyProp, FieldSelectionBean field,
                       String refEntityName, IObjSchema objMeta, String baseBizObjName, IEvalScope scope) {
        Set<IOrmEntity> ret = new LinkedHashSet<>();
        IEntityDao<IOrmEntity> dao = daoProvider.dao(refEntityName);

        RelationCopyOptions options = getRelationOptions(refSet.orm_collectionName());

        for (Object item : c) {
            if (StringHelper.isEmptyObject(item))
                continue;

            boolean simple = StdDataType.isSimpleType(item.getClass().getName());
            if (simple) {
                IOrmEntity refEntity = dao.loadEntityById(item);
                ret.add(refEntity);
            } else {
                // 更新关联实体
                Object id = BeanTool.instance().getProperty(item, OrmConstants.PROP_ID);
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
                            if (options != null && !options.isAllowAdd())
                                continue;
                            refEntity = dao.newEntity();
                            action = BizConstants.METHOD_SAVE;
                        } else {
                            // 如果明确配置了不允许更新集合中的元素
                            if (options != null && !options.isAllowUpdate())
                                continue;
                            action = BizConstants.METHOD_UPDATE;
                        }
                        copyToEntity(item, refEntity, field, objMeta, baseBizObjName, action, scope);
                        ret.add(refEntity);
                    }
                } else {
                    if (options == null || options.isAllowUpdate()) {
                        IOrmEntity refEntity = dao.loadEntityById(id);
                        copyToEntity(item, refEntity, field, objMeta, baseBizObjName, BizConstants.METHOD_UPDATE, scope);
                        ret.add(refEntity);
                    }
                }
            }
        }

        // 删除没有在src集合中出现的条目
        if (options == null || options.isAllowDelete())
            refSet.clear();

        refSet.addAll(ret);
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