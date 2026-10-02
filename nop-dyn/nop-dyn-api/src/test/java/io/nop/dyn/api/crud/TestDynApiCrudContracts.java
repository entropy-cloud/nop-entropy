package io.nop.dyn.api.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.api.ICrudApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试：nop-dyn-api 的 *Api 接口全部由 codegen 生成，模块内零实现逻辑。
 * 固化生成的接口契约：@BizModel 名称与动态元数据实体名一致、ICrudApi 泛型参数正确
 * 绑定对应的 InputBean / OutputBean 数据类。
 */
public class TestDynApiCrudContracts {

    static final Map<String, String> API_MODELS = buildApiModels();

    static Map<String, String> buildApiModels() {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put(NopDynAppApi.class.getName(), "NopDynApp");
        ret.put(NopDynAppModuleApi.class.getName(), "NopDynAppModule");
        ret.put(NopDynDomainApi.class.getName(), "NopDynDomain");
        ret.put(NopDynEntityApi.class.getName(), "NopDynEntity");
        ret.put(NopDynEntityMetaApi.class.getName(), "NopDynEntityMeta");
        ret.put(NopDynEntityRelationApi.class.getName(), "NopDynEntityRelation");
        ret.put(NopDynEntityRelationMetaApi.class.getName(), "NopDynEntityRelationMeta");
        ret.put(NopDynFileApi.class.getName(), "NopDynFile");
        ret.put(NopDynFunctionMetaApi.class.getName(), "NopDynFunctionMeta");
        ret.put(NopDynModuleApi.class.getName(), "NopDynModule");
        ret.put(NopDynModuleDepApi.class.getName(), "NopDynModuleDep");
        ret.put(NopDynPageApi.class.getName(), "NopDynPage");
        ret.put(NopDynPatchFileApi.class.getName(), "NopDynPatchFile");
        ret.put(NopDynPropMetaApi.class.getName(), "NopDynPropMeta");
        ret.put(NopDynSqlApi.class.getName(), "NopDynSql");
        return ret;
    }

    @Test
    public void testBizModelNameMatchesEntityName() throws Exception {
        for (Map.Entry<String, String> entry : API_MODELS.entrySet()) {
            Class<?> apiClass = Class.forName(entry.getKey());
            BizModel bizModel = apiClass.getAnnotation(BizModel.class);
            assertNotNull(bizModel, apiClass.getSimpleName() + " must carry @BizModel");
            assertEquals(entry.getValue(), bizModel.value(),
                    apiClass.getSimpleName() + " biz obj name must match entity name");
        }
    }

    @Test
    public void testEveryApiExtendsCrudApiWithMatchingBeanTypeArgs() throws Exception {
        for (String apiClassName : API_MODELS.keySet()) {
            Class<?> apiClass = Class.forName(apiClassName);
            String entityName = apiClass.getSimpleName().substring(0,
                    apiClass.getSimpleName().length() - "Api".length());

            ParameterizedType crudApi = null;
            for (Type type : apiClass.getGenericInterfaces()) {
                if (type instanceof ParameterizedType
                        && ((ParameterizedType) type).getRawType() == ICrudApi.class) {
                    crudApi = (ParameterizedType) type;
                }
            }
            assertNotNull(crudApi, apiClass.getSimpleName() + " must extend ICrudApi");

            Type[] args = crudApi.getActualTypeArguments();
            assertEquals(2, args.length);
            assertEquals("io.nop.dyn.api.beans." + entityName + "InputBean",
                    ((Class<?>) args[0]).getName(),
                    apiClass.getSimpleName() + " input bean binding");
            assertEquals("io.nop.dyn.api.beans." + entityName + "OutputBean",
                    ((Class<?>) args[1]).getName(),
                    apiClass.getSimpleName() + " output bean binding");
        }
    }

    @Test
    public void testInputOutputBeansArePublicConcreteDataClasses() throws Exception {
        for (String apiClassName : API_MODELS.keySet()) {
            String entityName = apiClassName.substring(apiClassName.lastIndexOf('.') + 1,
                    apiClassName.length() - "Api".length());
            for (String suffix : new String[]{"InputBean", "OutputBean"}) {
                Class<?> beanClass = Class.forName("io.nop.dyn.api.beans." + entityName + suffix);
                assertTrue(java.lang.reflect.Modifier.isPublic(beanClass.getModifiers()),
                        beanClass.getName() + " must be public");
                assertTrue(!beanClass.isInterface()
                                && !java.lang.reflect.Modifier.isAbstract(beanClass.getModifiers()),
                        beanClass.getName() + " must be a concrete data class");
            }
        }
    }

    @Test
    public void testEntityMetaApiModelName() {
        BizModel bizModel = NopDynEntityMetaApi.class.getAnnotation(BizModel.class);
        assertEquals("NopDynEntityMeta", bizModel.value(),
                "动态实体元数据的 biz 对象名必须是 NopDynEntityMeta");
    }
}
