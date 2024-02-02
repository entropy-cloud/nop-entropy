package io.nop.graphql.core.fetcher;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

public class SpecificPropertyFetcher implements IDataFetcher {
    private final String name;

    public SpecificPropertyFetcher(String name) {
        this.name = name;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        return BeanTool.instance().getProperty(env.getSource(), name);
    }
}
