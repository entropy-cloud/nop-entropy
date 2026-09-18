package io.nop.security.key;

import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * F-C1-1 回归测试：CompositeKeyManager 的 first-match-wins 顺序解析语义，
 * 以及重复 ID 聚合不抛错（重复时输出 WARN）。
 */
public class TestCompositeKeyManager {

    static Certificate dummyCert() {
        return new Certificate("DUMMY") {
            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }

            @Override
            public void verify(PublicKey key) {
            }

            @Override
            public void verify(PublicKey key, String sigProvider) {
            }

            @Override
            public String toString() {
                return "dummy-cert";
            }

            @Override
            public PublicKey getPublicKey() {
                return null;
            }
        };
    }

    static class StubKeyManager implements IKeyManager {
        private final String id;
        private final Certificate cert;

        StubKeyManager(String id, Certificate cert) {
            this.id = id;
            this.cert = cert;
        }

        @Override
        public Certificate getCertificate(String certId) {
            return id.equals(certId) ? cert : null;
        }

        @Override
        public PrivateKey getPrivateKey(String keyId) {
            return null;
        }

        @Override
        public List<String> getCertificateIds() {
            return List.of(id);
        }
    }

    @Test
    public void testFirstMatchWinsForSharedId() {
        Certificate first = dummyCert();
        Certificate second = dummyCert();
        CompositeKeyManager manager = new CompositeKeyManager(
                List.of(new StubKeyManager("shared", first), new StubKeyManager("shared", second)));

        assertSame(first, manager.getCertificate("shared"));
        assertNull(manager.getCertificate("missing"));
    }

    @Test
    public void testDuplicateIdsAggregateWithoutThrow() {
        CompositeKeyManager manager = new CompositeKeyManager(
                List.of(new StubKeyManager("a", dummyCert()), new StubKeyManager("a", dummyCert()),
                        new StubKeyManager("b", dummyCert())));

        assertEquals(List.of("a", "a", "b"), manager.getCertificateIds());
    }
}
