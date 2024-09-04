package io.nop.security.key;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.security.SecurityConstants;

import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class KeyBean {
    private String kid;
    private String kty;
    private String alg;
    private String use;
    private Map<String, Object> otherClaims;

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getKty() {
        return kty;
    }

    public void setKty(String kty) {
        this.kty = kty;
    }

    public String getUse() {
        return use;
    }

    public void setUse(String use) {
        this.use = use;
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherClaims() {
        return otherClaims;
    }

    @JsonAnyGetter
    public void setOtherClaim(String name, Object value) {
        if (otherClaims == null)
            otherClaims = new LinkedHashMap<>();
        otherClaims.put(name, value);
    }

    public String getOtherClaim(String name) {
        if (otherClaims == null)
            return null;
        return (String) otherClaims.get(name);
    }

    @JsonIgnore
    public String getRSAModulus() {
        return getOtherClaim(SecurityConstants.RSA_PROP_MODULUS);
    }

    @JsonIgnore
    public String getRSAExponent() {
        return getOtherClaim(SecurityConstants.RSA_PROP_EXPONENT);
    }
}
