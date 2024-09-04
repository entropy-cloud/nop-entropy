package io.nop.security.key;

import io.nop.security.SecurityConstants;

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
            String n = Base64.getUrlEncoder().encodeToString(rsaKey.getModulus().toByteArray());
            key.setOtherClaim(SecurityConstants.RSA_PROP_MODULUS, n);
            key.setOtherClaim(SecurityConstants.RSA_PROP_EXPONENT, "AQAB"); // 对于RSA公钥，"e"字段通常是固定的

            keys.add(key);
        }
        return keySet;
    }
}
