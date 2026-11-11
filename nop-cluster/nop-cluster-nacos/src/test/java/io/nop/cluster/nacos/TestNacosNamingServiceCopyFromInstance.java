package io.nop.cluster.nacos;

import com.alibaba.nacos.api.naming.pojo.Instance;
import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1-4.4-C4（plan 2395）：copyFromInstance 对 serviceName 的 "@@" 拆组必须带 pos 守卫
 * （对齐 ServiceInstance.getNormalizedServiceName）——无组名的 serviceName 此前直接
 * substring 抛 StringIndexOutOfBoundsException。
 */
public class TestNacosNamingServiceCopyFromInstance {

    private Instance nacosInstance(String serviceName) {
        Instance inst = new Instance();
        inst.setServiceName(serviceName);
        inst.setIp("10.0.0.1");
        inst.setPort(8080);
        return inst;
    }

    /** 无组名（不含 "@@"）：不得抛异常，serviceName 原样保留。 */
    @Test
    public void testCopyFromInstanceWithoutGroupKeepsServiceName() {
        ServiceInstance ret = new ServiceInstance();
        NacosNamingService.copyFromInstance(nacosInstance("my-service"), ret);

        assertEquals("my-service", ret.getServiceName(),
                "serviceName without group must be kept as-is (P1-4.4-C4)");
        assertNull(ret.getGroupName(), "no group part → groupName stays null");
    }

    /** 带 "@@"：正常拆出 groupName 与 serviceName。 */
    @Test
    public void testCopyFromInstanceWithGroupSplitsParts() {
        ServiceInstance ret = new ServiceInstance();
        NacosNamingService.copyFromInstance(nacosInstance("DEFAULT_GROUP@@my-service"), ret);

        assertEquals("DEFAULT_GROUP", ret.getGroupName());
        assertEquals("my-service", ret.getServiceName());
    }

    /**
     * 4.2-MA-08（plan 2559 裁定 C）：copyFromInstance :319 截断面——超出 int 范围的 weight
     * 不得经窄化静默截断/环绕，必须显式饱和到 int 边界。
     */
    @Test
    public void testCopyFromInstanceSaturatesOutOfRangeWeight() {
        Instance huge = nacosInstance("svc");
        huge.setWeight(6.0e9); // beyond Integer.MAX_VALUE
        ServiceInstance ret = new ServiceInstance();
        NacosNamingService.copyFromInstance(huge, ret);
        assertEquals(Integer.MAX_VALUE, ret.getWeight(),
                "MA-08/C: out-of-range weight must saturate, not silently truncate");

        Instance tiny = nacosInstance("svc");
        tiny.setWeight(-6.0e9); // beyond Integer.MIN_VALUE
        ServiceInstance ret2 = new ServiceInstance();
        NacosNamingService.copyFromInstance(tiny, ret2);
        assertEquals(Integer.MIN_VALUE, ret2.getWeight(),
                "MA-08/C: out-of-range negative weight must saturate, not silently wrap");
    }

    /** 常规范围内的 weight 原样拷贝，不受饱和逻辑影响。 */
    @Test
    public void testCopyFromInstanceKeepsInrangeWeight() {
        Instance inst = nacosInstance("svc");
        inst.setWeight(3.0);
        ServiceInstance ret = new ServiceInstance();
        NacosNamingService.copyFromInstance(inst, ret);
        assertEquals(3, ret.getWeight());
    }
}
