/**
 * SecurityHelper.toRSAPublicKey 错误处理回归测试：
 * 修复前公钥构造失败抛 bare RuntimeException（无错误码、无定位参数），
 * 违背平台框架核心 NopException 错误处理约定。
 */
package io.nop.security.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.security.SecurityConstants;
import io.nop.security.key.KeyBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestSecurityHelperRsaKey {

    @Test
    public void testInvalidRsaKeySpecThrowsNopException() {
        KeyBean keyBean = new KeyBean();
        // 非法 RSA 参数（1 字节模数 + 1 字节指数）：generatePublic 必然失败
        keyBean.setOtherClaim(SecurityConstants.RSA_PROP_MODULUS, "AA==");
        keyBean.setOtherClaim(SecurityConstants.RSA_PROP_EXPONENT, "AA==");

        NopException e = assertThrows(NopException.class, () -> SecurityHelper.toRSAPublicKey(keyBean),
                "公钥构造失败必须抛 NopException（平台错误处理两档策略），不得抛 bare RuntimeException");
        assertNotNull(e.getCause(), "底层异常保留为 cause");
    }
}
