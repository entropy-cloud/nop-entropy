/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.auth.sso.jwk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.commons.util.StringHelper;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.util.Base64Url;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class JWK {

    public static final String KEY_ID = "kid";

    public static final String KEY_TYPE = "kty";

    public static final String ALGORITHM = "alg";

    public static final String PUBLIC_KEY_USE = "use";

    public enum Use {
        SIG("sig"),
        ENCRYPTION("enc");

        private String str;

        Use(String str) {
            this.str = str;
        }

        public String asString() {
            return str;
        }
    }

    private String keyId;

    private String keyType;

    private String algorithm;

    private String publicKeyUse;

    protected Map<String, Object> otherClaims = new HashMap<String, Object>();

    @JsonProperty(KEY_ID)
    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    @JsonProperty(KEY_TYPE)
    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    @JsonProperty(ALGORITHM)
    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    @JsonProperty(PUBLIC_KEY_USE)
    public String getPublicKeyUse() {
        return publicKeyUse;
    }

    public void setPublicKeyUse(String publicKeyUse) {
        this.publicKeyUse = publicKeyUse;
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherClaims() {
        return otherClaims;
    }

    @JsonAnySetter
    public void setOtherClaims(String name, Object value) {
        otherClaims.put(name, value);
    }


    public PublicKey toPublicKey() {
        String keyType = getKeyType();
        if (keyType.equals(KeyType.RSA)) {
            return createRSAPublicKey();
        } else if (keyType.equals(KeyType.EC)) {
            return createECPublicKey();
        } else {
            throw new RuntimeException("Unsupported keyType " + keyType);
        }
    }

    private PublicKey createECPublicKey() {
        String crv = (String) getOtherClaims().get(ECPublicJWK.CRV);
        BigInteger x = new BigInteger(1, StringHelper.decodeBase64((String) getOtherClaims().get(ECPublicJWK.X)));
        BigInteger y = new BigInteger(1, StringHelper.decodeBase64((String) getOtherClaims().get(ECPublicJWK.Y)));

        String name;
        switch (crv) {
            case "P-256":
                name = "secp256r1";
                break;
            case "P-384":
                name = "secp384r1";
                break;
            case "P-521":
                name = "secp521r1";
                break;
            default:
                throw new RuntimeException("Unsupported curve");
        }

        try {

            ECPoint point = new ECPoint(x, y);
            ECParameterSpec params = CryptoIntegration.getProvider().createECParams(name);
            ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, params);

            KeyFactory kf = CryptoIntegration.getProvider().getKeyFactory("ECDSA");
            return kf.generatePublic(pubKeySpec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PublicKey createRSAPublicKey() {
        BigInteger modulus = new BigInteger(1, Base64Url.decode(getOtherClaims().get(RSAPublicJWK.MODULUS).toString()));
        BigInteger publicExponent = new BigInteger(1, Base64Url.decode(getOtherClaims().get(RSAPublicJWK.PUBLIC_EXPONENT).toString()));

        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isKeyTypeSupported(String keyType) {
        return (RSAPublicJWK.RSA.equals(keyType) || ECPublicJWK.EC.equals(keyType));
    }
}
