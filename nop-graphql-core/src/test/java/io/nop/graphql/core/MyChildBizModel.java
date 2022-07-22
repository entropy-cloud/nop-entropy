package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.ContextRoot;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@BizModel("MyChild")
public class MyChildBizModel {
    @BizLoader
    public String extField(@ContextSource MyChild child, @ContextRoot MyEntity entity,
                              FieldSelectionBean selection, IDataFetchingEnvironment env) {
        assertNotNull(entity);
        assertEquals("extField", env.getSelection().getName());
        assertEquals("extField", env.getSelectionBean().getName());
        assertEquals(selection, env.getSelectionBean());

        return "ext_" + child.getValue();
    }

    /**
     * 批量加载属性
     */
    @BizLoader("name")
    public List<String> getNames(@ContextSource List<MyChild> list) {
        List<String> ret = new ArrayList<>(list.size());
        for (MyChild child : list) {
            ret.add(child.getName() + "_batch");
        }
        return ret;
    }
}
