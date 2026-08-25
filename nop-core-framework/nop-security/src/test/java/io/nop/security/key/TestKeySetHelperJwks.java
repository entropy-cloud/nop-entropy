/**
 * KeySetHelper JWKS 编码回归测试：
 * 修复前指数硬编码 "AQAB"（65537）且模数用 BigInteger.toByteArray() 原始输出（2048 位模数
 * 带 1 个前导零字节，257 字节），违反 RFC 7518 §6.3.1.1 minimum octets 要求，且非 65537
 * 指数证书发布的 JWKS 指数错误；修复后指数取公钥真实值、模数/指数均为最小八位组编码。
 *
 * <p>用固定 BigInteger 桩（而非随机生成密钥）保证前导零场景确定性出现：
 * 全 0xFF 的 256 字节模数 bitLength=2048，toByteArray() 输出 257 字节。</p>
 */
package io.nop.security.key;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestKeySetHelperJwks {

    static class StubRsaPublicKey implements RSAPublicKey {
        private final BigInteger modulus;
        private final BigInteger exponent;

        StubRsaPublicKey(BigInteger modulus, BigInteger exponent) {
            this.modulus = modulus;
            this.exponent = exponent;
        }

        @Override
        public BigInteger getModulus() {
            return modulus;
        }

        @Override
        public BigInteger getPublicExponent() {
            return exponent;
        }

        @Override
        public String getAlgorithm() {
            return "RSA";
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }

    static class StubCertificate extends Certificate {
        private final PublicKey publicKey;

        protected StubCertificate(PublicKey publicKey) {
            super("RSA");
            this.publicKey = publicKey;
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) throws CertificateException {
            throw new CertificateException("stub");
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException {
            throw new CertificateException("stub");
        }

        @Override
        public String toString() {
            return "stub-cert";
        }

        @Override
        public PublicKey getPublicKey() {
            return publicKey;
        }
    }

    static class StubKeyManager implements IKeyManager {
        private final Certificate cert;

        StubKeyManager(Certificate cert) {
            this.cert = cert;
        }

        @Override
        public Certificate getCertificate(String certId) {
            return cert;
        }

        @Override
        public PrivateKey getPrivateKey(String keyId) {
            return null;
        }

        @Override
        public List<String> getCertificateIds() {
            return Arrays.asList("test-cert");
        }
    }

    private static KeyBean keyBeanOf(BigInteger modulus, BigInteger exponent) {
        StubRsaPublicKey key = new StubRsaPublicKey(modulus, exponent);
        KeySetBean keySet = KeySetHelper.getPublicKeySet(new StubKeyManager(new StubCertificate(key)));
        return keySet.getKeyById("test-cert");
    }

    @Test
    public void testModulusUsesMinimumOctets() {
        // 全 0xFF 的 256 字节 → bitLength 2048 → toByteArray() 257 字节（带前导零）
        byte[] raw = new byte[256];
        Arrays.fill(raw, (byte) 0xFF);
        BigInteger modulus = new BigInteger(1, raw);

        KeyBean bean = keyBeanOf(modulus, BigInteger.valueOf(65537));
        byte[] n = Base64.getUrlDecoder().decode(bean.getRSAModulus());

        assertEquals((modulus.bitLength() + 7) / 8, n.length,
                "模数必须为 minimum octets 编码（剥离前导零字节）");
        assertEquals(modulus, new BigInteger(1, n), "模数值不得改变");
        // 修复前：n 长度为 257（BigInteger.toByteArray 原始输出）
    }

    @Test
    public void testExponentTakesRealPublicKeyValue() {
        byte[] raw = new byte[256];
        Arrays.fill(raw, (byte) 0xFF);
        BigInteger modulus = new BigInteger(1, raw);
        BigInteger exponent = BigInteger.valueOf(3); // 非 65537 的合法指数

        KeyBean bean = keyBeanOf(modulus, exponent);
        byte[] e = Base64.getUrlDecoder().decode(bean.getRSAExponent());

        assertEquals(exponent, new BigInteger(1, e), "指数必须取公钥真实值");
        // 修复前：恒为 "AQAB"（65537）
    }

    @Test
    public void testStandard65537KeyStillEncodesAsAqab() {
        byte[] raw = new byte[256];
        Arrays.fill(raw, (byte) 0xFF);
        BigInteger modulus = new BigInteger(1, raw);

        KeyBean bean = keyBeanOf(modulus, BigInteger.valueOf(65537));

        // 65537 = 0x010001，最小八位组 base64url 即 "AQAB"，标准密钥行为不变
        assertEquals("AQAB", bean.getRSAExponent());
    }

    @Test
    public void testJwksValuesReconstructOriginalKey() throws Exception {
        byte[] raw = new byte[256];
        Arrays.fill(raw, (byte) 0xFF);
        raw[0] = (byte) 0xC0; // 保持 2048 位
        BigInteger modulus = new BigInteger(1, raw);
        BigInteger exponent = BigInteger.valueOf(3);

        KeyBean bean = keyBeanOf(modulus, exponent);
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(bean.getRSAModulus()));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(bean.getRSAExponent()));

        // JWKS 值可重建原始公钥（标准 RSAPublicKeySpec 构造，不依赖宽容前导零的解析）
        RSAPublicKey reconstructed = (RSAPublicKey) java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(n, e));
        assertEquals(modulus, reconstructed.getModulus());
        assertEquals(exponent, reconstructed.getPublicExponent());
    }
}
