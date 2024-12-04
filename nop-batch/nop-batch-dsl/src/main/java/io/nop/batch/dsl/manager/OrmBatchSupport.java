package io.nop.batch.dsl.manager;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.dsl.model.BatchOrmReaderModel;
import io.nop.batch.dsl.model.BatchOrmWriterModel;
import io.nop.batch.orm.consumer.OrmBatchConsumerProvider;
import io.nop.batch.orm.loader.OrmQueryBatchLoaderProvider;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;

import java.util.List;

public class OrmBatchSupport {
    public static IBatchLoaderProvider<Object> newOrmReader(BatchOrmReaderModel loaderModel, IDaoProvider daoProvider) {
        IXNodeGenerator query = loaderModel.getQuery();
        List<String> batchLoadProps = loaderModel.getBatchLoadProps();

        OrmQueryBatchLoaderProvider<IOrmEntity> loader = new OrmQueryBatchLoaderProvider<>();
        loader.setBatchLoadProps(batchLoadProps);
        loader.setDaoProvider(daoProvider);
        loader.setPartitionIndexField(loaderModel.getPartitionIndexField());
        loader.setEntityName(loaderModel.getEntityName());

        if (query != null)
            loader.setQueryBuilder(newQueryBuilder(query));
        //loader.setSqlGenerator(loaderModel.getEql());

        return (IBatchLoaderProvider) loader;
    }

    public static IQueryBuilder newQueryBuilder(IXNodeGenerator generator) {
        return context -> {
            XNode node = generator.generateNode(context);
            return BeanTool.buildBeanFromTreeBean(node, QueryBean.class);
        };
    }

    public static IBatchConsumerProvider<Object> newOrmWriter(BatchOrmWriterModel consumerModel, IDaoProvider daoProvider,
                                                              IOrmTemplate ormTemplate) {
        OrmBatchConsumerProvider<Object> provider = new OrmBatchConsumerProvider<>();
        provider.setOrmTemplate(ormTemplate);
        provider.setDaoProvider(daoProvider);
        provider.setEntityName(consumerModel.getEntityName());
        provider.setKeyFields(consumerModel.getKeyFields());
        provider.setAllowInsert(consumerModel.isAllowInsert());
        provider.setAllowUpdate(consumerModel.isAllowUpdate());
        return provider;
    }
}
