/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.type.CollectionKind;
import io.nop.core.type.IGenericType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_MAP_TYPE;

public class ContainerTypeData {
    private final CollectionKind collectionKind;
    private final IGenericType collectionType;
    private final IGenericType mapType;

    public ContainerTypeData(CollectionKind collectionKind, IGenericType collectionType, IGenericType mapType) {
        this.collectionKind = collectionKind;
        this.collectionType = collectionType;
        this.mapType = mapType;
    }

    public static ContainerTypeData buildFrom(IGenericType type) {
        CollectionKind kind = CollectionKind.NONE;
        IGenericType collectionType = type.getGenericType(List.class);
        if (collectionType == null) {
            collectionType = type.getGenericType(Set.class);
            if (collectionType == null) {
                collectionType = type.getGenericType(Collection.class);
                if (collectionType != null) {
                    kind = CollectionKind.Collection;
                }
            } else {
                kind = CollectionKind.Set;
            }
        } else {
            kind = CollectionKind.List;
        }

        IGenericType mapType = type.getGenericType(Map.class);
        return new ContainerTypeData(kind, collectionType, mapType);
    }

    public boolean isCollectionLike() {
        return collectionKind.isCollectionLike();
    }

    public boolean isListLike() {
        return collectionKind.isListLike();
    }

    public boolean isSetLike() {
        return collectionKind.isSetLike();
    }

    public boolean isMapLike() {
        return mapType != null;
    }

    public IGenericType getCollectionType() {
        return collectionType;
    }

    public IGenericType getMapType() {
        return mapType;
    }

    public IGenericType getComponentType(String typeName) {
        if (collectionType == null)
            throw new NopException(ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE).param(ARG_TYPE_NAME, typeName);
        // 这里不能调用collectionType.getComponentType(), 否则可能出现死循环
        return collectionType.getTypeParameters().get(0);
    }

    public IGenericType getMapKeyType(String typeName) {
        if (mapType == null)
            throw new NopException(ERR_TYPE_NOT_MAP_TYPE).param(ARG_TYPE_NAME, typeName);
        return mapType.getTypeParameters().get(0);
    }

    public IGenericType getMapValueType(String typeName) {
        if (mapType == null)
            throw new NopException(ERR_TYPE_NOT_MAP_TYPE).param(ARG_TYPE_NAME, typeName);
        return mapType.getTypeParameters().get(1);
    }
}
