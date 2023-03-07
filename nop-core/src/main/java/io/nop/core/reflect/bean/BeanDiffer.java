/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.diff.DiffType;
import io.nop.commons.diff.DiffValue;
import io.nop.commons.diff.IDiffValue;
import io.nop.commons.util.CollectionHelper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("PMD.UnusedFormalParameter")
public class BeanDiffer implements IBeanDiffer {
    static final BeanDiffOptions EMPTY_OPTIONS = new BeanDiffOptions();

    @Override
    public IDiffValue beanDiff(Object src, Object target, BeanDiffOptions options) {
        if (options == null)
            options = EMPTY_OPTIONS;
        return diffObject(src, target, options.getSelection(), options);
    }

    private IDiffValue diffObject(Object src, Object target, FieldSelectionBean selection, BeanDiffOptions options) {
        if (Objects.equals(src, target)) {
            if (!options.isIncludeSame())
                return null;
            return DiffValue.same(src, target);
        }
        if (src == null) {
            return DiffValue.replace(null, target);
        }
        if (target == null) {
            return DiffValue.replace(src, null);
        }

        Class<?> c1 = src.getClass();
        Class<?> c2 = target.getClass();
        if (c1.isArray() && c2.isArray()) {
            return diffArray(src, target, selection, options);
        }
        if (c1.isArray() || c2.isArray()) {
            return DiffValue.replace(src, target);
        }

        IBeanModelManager beanModelManager = options.getBeanModelManager();
        IBeanModel bm1 = beanModelManager.getBeanModelForClass(src.getClass());
        IBeanModel bm2 = beanModelManager.getBeanModelForClass(target.getClass());

        if (bm1.isCollectionLike() && bm2.isCollectionLike()) {
            return diffCollection(src, target, selection, options);
        }

        if (bm1.isCollectionLike() || bm2.isCollectionLike()) {
            return DiffValue.replace(src, target);
        }

        if (bm1.isEnum() || bm2.isEnum())
            return DiffValue.replace(src, target);

        if (bm1.isSimpleType() || bm2.isSimpleType())
            return DiffValue.replace(src, target);

        DiffValue ret = new DiffValue(DiffType.update, src, target);
        Map<String, IDiffValue> propDiffs;
        if (selection != null) {
            if (!selection.hasField()) {
                return DiffValue.replace(src, target);
            }

            propDiffs = CollectionHelper.newLinkedHashMap(selection.getFields().size());
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String propName = entry.getKey();
                FieldSelectionBean subSelection = entry.getValue();
                diffValue(propDiffs, bm1, bm2, src, target, propName, subSelection, options);
            }
        } else if (isSameClass(bm1, bm2, src, target)) {
            propDiffs = new LinkedHashMap<>(bm1.getPropertyModels().size());
            forEachReadableProperty(bm1, src, true, propName -> {
                diffValue(propDiffs, bm1, bm2, src, target, propName, null, options);
            });
        } else {
            propDiffs = new LinkedHashMap<>();
            Set<String> propNames = new HashSet<>();
            forEachReadableProperty(bm2, src, options.isOnlySerializable(), propName -> {
                propNames.add(propName);

                diffValue(propDiffs, bm1, bm2, src, target, propName, null, options);
            });

            forEachReadableProperty(bm2, src, options.isOnlySerializable(), propName -> {
                if (!propNames.contains(propName)) {
                    Object targetProp = bm2.getProperty(target, propName, options.getScope());
                    IDiffValue diff = DiffValue.replace(null, targetProp);
                    propDiffs.put(propName, diff);
                }
            });
        }
        if (propDiffs.isEmpty()) {
            if (!options.isIncludeSame())
                return null;
        }
        ret.setPropDiffs(propDiffs);
        return ret;
    }

    private void forEachReadableProperty(IBeanModel beanModel, Object bean, boolean onlySerializable,
                                         Consumer<String> consumer) {
        beanModel.forEachReadableProp(propModel -> {
            if (onlySerializable && !propModel.isSerializable())
                return;

            consumer.accept(propModel.getName());
        });

        Set<String> extNames = beanModel.getExtPropertyNames(bean);
        if (extNames != null) {
            for (String extName : extNames) {
                consumer.accept(extName);
            }
        }
    }

    private void diffValue(Map<String, IDiffValue> propDiffs, IBeanModel bm1, IBeanModel bm2, Object src, Object target,
                           String propName, FieldSelectionBean subSelection, BeanDiffOptions options) {
        Object srcProp = bm1.getProperty(src, propName, options.getScope());
        Object targetProp = bm2.getProperty(target, propName, options.getScope());

        IDiffValue diff = diffObject(srcProp, targetProp, subSelection, options);
        if (diff != null)
            propDiffs.put(propName, diff);
    }

    private boolean isSameClass(IBeanModel bm1, IBeanModel bm2, Object src, Object target) {
        return bm1 == bm2 && Objects.equals(bm1.getExtPropertyNames(src), bm2.getExtPropertyNames(target));
    }

    private IDiffValue diffArray(Object src, Object target, FieldSelectionBean selection, BeanDiffOptions options) {
        return null;
    }

    private IDiffValue diffCollection(Object src, Object target, FieldSelectionBean selection,
                                      BeanDiffOptions options) {
        return null;
    }

    @Override
    public void applyDiff(Object bean, IDiffValue diffValue) {

    }
}