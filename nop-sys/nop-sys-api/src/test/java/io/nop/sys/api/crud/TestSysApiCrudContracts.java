package io.nop.sys.api.crud;

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
 * 结构性契约测试：nop-sys-api 的 *Api 接口全部由 codegen 生成，模块内零实现逻辑。
 * 这里固化生成的接口契约：@BizModel 名称与实体名一致、ICrudApi 泛型参数正确绑定
 * 对应的 InputBean / OutputBean 数据类。
 */
public class TestSysApiCrudContracts {

    /** crud 接口名 -> 期望的 @BizModel 名称（去掉 Api 后缀的实体名） */
    static final Map<String, String> API_MODELS = buildApiModels();

    static Map<String, String> buildApiModels() {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put(NopSysDictApi.class.getName(), "NopSysDict");
        ret.put(NopSysDictOptionApi.class.getName(), "NopSysDictOption");
        ret.put(NopSysSequenceApi.class.getName(), "NopSysSequence");
        ret.put(NopSysLockApi.class.getName(), "NopSysLock");
        ret.put(NopSysCodeRuleApi.class.getName(), "NopSysCodeRule");
        ret.put(NopSysVariableApi.class.getName(), "NopSysVariable");
        ret.put(NopSysUserVariableApi.class.getName(), "NopSysUserVariable");
        ret.put(NopSysI18nApi.class.getName(), "NopSysI18n");
        ret.put(NopSysTagApi.class.getName(), "NopSysTag");
        ret.put(NopSysObjTagApi.class.getName(), "NopSysObjTag");
        ret.put(NopSysExtFieldApi.class.getName(), "NopSysExtField");
        ret.put(NopSysCompactExtFieldApi.class.getName(), "NopSysCompactExtField");
        ret.put(NopSysChangeLogApi.class.getName(), "NopSysChangeLog");
        ret.put(NopSysEventApi.class.getName(), "NopSysEvent");
        ret.put(NopSysBroadcastEventApi.class.getName(), "NopSysBroadcastEvent");
        ret.put(NopSysClusterLeaderApi.class.getName(), "NopSysClusterLeader");
        ret.put(NopSysCheckerRecordApi.class.getName(), "NopSysCheckerRecord");
        ret.put(NopSysServiceInstanceApi.class.getName(), "NopSysServiceInstance");
        ret.put(NopSysNoticeTemplateApi.class.getName(), "NopSysNoticeTemplate");
        return ret;
    }

    static Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    @Test
    public void testBizModelNameMatchesEntityName() throws Exception {
        for (Map.Entry<String, String> entry : API_MODELS.entrySet()) {
            Class<?> apiClass = loadClass(entry.getKey());
            BizModel bizModel = apiClass.getAnnotation(BizModel.class);
            assertNotNull(bizModel, apiClass.getSimpleName() + " must carry @BizModel");
            assertEquals(entry.getValue(), bizModel.value(),
                    apiClass.getSimpleName() + " biz obj name must match entity name");
        }
    }

    @Test
    public void testEveryApiExtendsCrudApiWithMatchingBeanTypeArgs() throws Exception {
        for (String apiClassName : API_MODELS.keySet()) {
            Class<?> apiClass = loadClass(apiClassName);
            String entityName = apiClass.getSimpleName().substring(0,
                    apiClass.getSimpleName().length() - "Api".length());

            Type[] genericIfaces = apiClass.getGenericInterfaces();
            ParameterizedType crudApi = null;
            for (Type type : genericIfaces) {
                if (type instanceof ParameterizedType
                        && ((ParameterizedType) type).getRawType() == ICrudApi.class) {
                    crudApi = (ParameterizedType) type;
                }
            }
            assertNotNull(crudApi, apiClass.getSimpleName() + " must extend ICrudApi");

            Type[] args = crudApi.getActualTypeArguments();
            assertEquals(2, args.length, "ICrudApi takes exactly two type args");
            assertEquals("io.nop.sys.api.beans." + entityName + "InputBean",
                    ((Class<?>) args[0]).getName(),
                    apiClass.getSimpleName() + " input bean binding");
            assertEquals("io.nop.sys.api.beans." + entityName + "OutputBean",
                    ((Class<?>) args[1]).getName(),
                    apiClass.getSimpleName() + " output bean binding");
        }
    }

    @Test
    public void testInputOutputBeansArePublicConcreteDataBeans() throws Exception {
        for (String apiClassName : API_MODELS.keySet()) {
            String entityName = apiClassName.substring(apiClassName.lastIndexOf('.') + 1,
                    apiClassName.length() - "Api".length());
            for (String suffix : new String[]{"InputBean", "OutputBean"}) {
                Class<?> beanClass = loadClass("io.nop.sys.api.beans." + entityName + suffix);
                assertTrue(java.lang.reflect.Modifier.isPublic(beanClass.getModifiers()),
                        beanClass.getName() + " must be public");
                assertTrue(!beanClass.isInterface() && !java.lang.reflect.Modifier.isAbstract(beanClass.getModifiers()),
                        beanClass.getName() + " must be a concrete data class");
                assertNotNull(beanClass.getAnnotation(io.nop.api.core.annotations.data.DataBean.class),
                        beanClass.getName() + " must carry @DataBean");
            }
        }
    }

    @Test
    public void testDictApiModelName() {
        BizModel bizModel = NopSysDictApi.class.getAnnotation(BizModel.class);
        assertEquals("NopSysDict", bizModel.value(),
                "dict CRUD 服务暴露的 biz 对象名必须是 NopSysDict");
        assertEquals(ICrudApi.class, NopSysDictApi.class.getInterfaces()[0]);
    }
}
