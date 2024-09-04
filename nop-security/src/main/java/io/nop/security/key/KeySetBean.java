package io.nop.security.key;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class KeySetBean {
    private List<KeyBean> keys;

    public List<KeyBean> getKeys() {
        return keys;
    }

    public void setKeys(List<KeyBean> keys) {
        this.keys = keys;
    }

    public KeyBean getKeyById(String keyId) {
        if (keys == null)
            return null;
        for (KeyBean key : keys) {
            if (keyId.equals(key.getKid()))
                return key;
        }
        return null;
    }
}
