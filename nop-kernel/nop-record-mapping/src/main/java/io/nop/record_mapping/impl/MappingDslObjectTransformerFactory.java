package io.nop.record_mapping.impl;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.xlang.xdsl.IDslObjectTransformerFactory;

public class MappingDslObjectTransformerFactory implements IDslObjectTransformerFactory {

    @Override
    public IDslObjectTransformer newTransformer(String mappingName) {
        return new IDslObjectTransformer() {
            @Override
            public Object transform(Object obj) {
                IRecordMappingManager manager = BeanContainer.getBeanByType(IRecordMappingManager.class);
                RecordMappingContext ctx = new RecordMappingContext();
                return manager.getRecordMapping(mappingName).map(obj, ctx);
            }
        };
    }
}
