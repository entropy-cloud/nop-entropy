package io.nop.biz.impl;

import io.nop.graphql.core.reflection.GraphQLBizModel;

public interface IDynamicBizModelProvider {

    GraphQLBizModel getBizModel(String bizObjName);

    Runnable addOnChangeListener(ChangeListener listener);

    interface ChangeListener {
        void onBizObjRemoved(String bizObjName);
    }
}
