package io.nop.security.key;

import io.nop.security.SecurityConstants;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class KeySetHelper {
    public static KeySetBean getPublicKeySet(IKeyManager keyManager) {
        KeySetBean keySet = new KeySetBean();
        List<String> certIds = keyManager.getCertificateIds();
        List<KeyBean> keys = new ArrayList<>(certIds.size());
        keySet.setKeys(keys);

        for (String certId : certIds) {
            Certificate cert = keyManager.getCertificate(certId);
            PublicKey publicKey = cert.getPublicKey();
            RSAPublicKey rsaKey = (RSAPublicKey) publicKey;
            KeyBean key = new KeyBean();
            key.setAlg(SecurityConstants.ALG_RS256);
            key.setKid(certId);
            key.setUse(SecurityConstants.KEY_USE_SIG);
            key.setKty(SecurityConstants.KEY_TYPE_RSA);
            // RFC 7518 §6.3.1.1/§6.3.1.2：modulus/exponent 必须使用 minimum octets 的
            // base64url 编码——模数/toByteArray() 对最高位为 1 的正数会带一个前导零字节
            //（2048 位模数常见 257 字节），严格 JWKS 客户端会拒绝或构造出错误公钥
            String n = Base64.getUrlEncoder().encodeToString(toMinimalUnsignedBytes(rsaKey.getModulus()));
            key.setOtherClaim(SecurityConstants.RSA_PROP_MODULUS, n);
            // 指数必须取证书公钥的真实值：非 65537 指数（合法存在）硬编码 "AQAB" 会让
            // 验签方构造出错误公钥
            String e = Base64.getUrlEncoder().encodeToString(toMinimalUnsignedBytes(rsaKey.getPublicExponent()));
            key.setOtherClaim(SecurityConstants.RSA_PROP_EXPONENT, e);

            keys.add(key);
        }
        return keySet;
    }

    /**
     * 剥离 BigInteger.toByteArray() 可能带的前导零字节（正数的符号位补位），得到
     * RFC 7518 要求的最小八位组表示。
     */
    static byte[] toMinimalUnsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
