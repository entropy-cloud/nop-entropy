package io.nop.dyn.service.codegen;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.type.StdSqlType;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynEntity;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestDynCodeGen extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    DynCodeGen codeGen;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testGen() {
        saveModule();

        ormTemplate.runInSession(() -> {
            codeGen.generateForAllModules();
            codeGen.reloadModel();

            IEntityDao<NopDynEntity> dao = daoProvider.dao("MyDynEntity");
            dao.findAll();

            IGraphQLExecutionContext gqlContext = graphQLEngine.newRpcContext(null, "MyDynEntity__findPage", ApiRequest.build(null));
            @SuppressWarnings("unchecked")
            ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(gqlContext));
            assertEquals(true, response.isOk());
            PageBean<?> pageBean = BeanTool.castBeanToType(response.getData(), PageBean.class);
            assertEquals(0, pageBean.getTotal());
        });
    }

    private void saveModule() {
        NopDynModule module = new NopDynModule();
        module.setModuleName("app-demo");
        module.setDisplayName("Demo Module");
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);

        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("test.MyDynEntity");
        entityMeta.setDisplayName("My Dynamic Entity");
        entityMeta.setModule(module);
        entityMeta.setStatus(1);

        entityMeta.setStoreType(NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL);

        NopDynPropMeta prop = addProp(entityMeta, "name", StdSqlType.VARCHAR, 100);
        prop.setDynPropMapping(NopDynEntity.PROP_NAME_name);

        addProp(entityMeta, "value", StdSqlType.INTEGER, 0);

        module.getEntityMetas().add(entityMeta);

        daoProvider.daoFor(NopDynModule.class).saveEntity(module);
    }

    private NopDynPropMeta addProp(NopDynEntityMeta entityMeta, String propName, StdSqlType sqlType, int precision) {
        NopDynPropMeta propMeta = new NopDynPropMeta();
        propMeta.setPropName(propName);
        propMeta.setDisplayName(propName + " Display");
        propMeta.setPrecision(precision);
        propMeta.setIsMandatory(true);
        propMeta.setStatus(1);
        propMeta.setStdSqlType(sqlType.getName());
        entityMeta.getPropMetas().add(propMeta);
        return propMeta;
    }
}
