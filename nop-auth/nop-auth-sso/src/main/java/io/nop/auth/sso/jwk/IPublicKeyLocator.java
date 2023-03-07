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

import java.security.PublicKey;

// 支持公钥轮换，从keycloak项目拷贝

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public interface IPublicKeyLocator {

    /**
     * @param kid
     * @return publicKey, which should be used for verify signature on given "input"
     */
    PublicKey getPublicKey(String kid);

    /**
     * Reset the state of locator (eg. clear the cached keys)
     *
     * @param deployment
     */
    void reset();

}
