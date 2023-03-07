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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class ECPublicJWK extends JWK {

    public static final String EC = "EC";

    public static final String CRV = "crv";
    public static final String X = "x";
    public static final String Y = "y";

    private String crv;

    private String x;

    private String y;

    @JsonProperty(CRV)
    public String getCrv() {
        return crv;
    }

    public void setCrv(String crv) {
        this.crv = crv;
    }

    @JsonProperty(X)
    public String getX() {
        return x;
    }

    public void setX(String x) {
        this.x = x;
    }

    @JsonProperty(Y)
    public String getY() {
        return y;
    }

    public void setY(String y) {
        this.y = y;
    }
}
