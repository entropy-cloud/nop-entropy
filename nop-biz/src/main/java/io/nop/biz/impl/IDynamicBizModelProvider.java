package io.nop.biz.impl;

import io.nop.graphql.core.reflection.GraphQLBizModel;

import java.util.Set;

public interface IDynamicBizModelProvider {
    Set<String> getBizObjNames();

    GraphQLBizModel getBizModel(String bizObjName);

    Runnable addOnChangeListener(ChangeListener listener);

    interface ChangeListener {
        void onBizObjRemoved(String bizObjName);

        void onBizObjChanged(String bizObjName);
    }
}
