package io.nop.batch.dsl.manager;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.dsl.model.BatchOrmReaderModel;
import io.nop.batch.orm.loader.OrmQueryBatchLoaderProvider;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.IOrmEntity;

import java.util.List;

public class OrmBatchSupport {
    public static IBatchLoaderProvider<Object> buildOrmReader(BatchOrmReaderModel loaderModel, IDaoProvider daoProvider) {
        IXNodeGenerator query = loaderModel.getQuery();
        List<String> batchLoadProps = loaderModel.getBatchLoadProps();

        OrmQueryBatchLoaderProvider<IOrmEntity> loader = new OrmQueryBatchLoaderProvider<>();
        loader.setBatchLoadProps(batchLoadProps);
        loader.setDaoProvider(daoProvider);
        if (query != null)
            loader.setQueryBuilder(newQueryBuilder(query));
        //loader.setSqlGenerator(loaderModel.getEql());

        return (IBatchLoaderProvider) loader;
    }

    private static IQueryBuilder newQueryBuilder(IXNodeGenerator generator) {
        return context -> {
            XNode node = generator.generateNode(context);
            return BeanTool.buildBeanFromTreeBean(node, QueryBean.class);
        };
    }
}
