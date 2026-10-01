package io.nop.graphql.grpc.service;

import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.directive.Auth;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * G5-13-01同类面：DevDoc__grpc导出全量schema对应的proto描述，debug部署下必须要求admin角色
 * （auth==null时平台按公开访问处理），对齐io.nop.biz.dev包的DevDocBizModel整改基线。
 */
public class TestDevDocGrpcBizModelAuth {

    @Test
    public void testAllOperationsRequireAdminRole() throws Exception {
        for (Method method : DevDocGrpcBizModel.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())
                    || !method.isAnnotationPresent(BizQuery.class))
                continue;
            Auth auth = method.getAnnotation(Auth.class);
            assertNotNull(auth, "DevDocGrpcBizModel." + method.getName() + " must declare @Auth");
            assertEquals("admin", auth.roles(),
                    "DevDocGrpcBizModel." + method.getName() + " must require admin role");
        }
    }
}
