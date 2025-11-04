/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.api.core.annotations.txn;

/**
 * Enumeration that represents transaction propagation behaviors for use
 * with the {@link Transactional} annotation.
 *
 * @author Colin Sampaleanu
 * @author Juergen Hoeller
 * @since 1.2
 */
public enum TransactionPropagation {

    /**
     * Support a current transaction, create a new one if none exists.
     * Analogous to EJB transaction attribute of the same name.
     * <p>This is the default setting of a transaction annotation.
     */
    REQUIRED(0),

    /**
     * Support a current transaction, execute non-transactionally if none exists.
     * Analogous to EJB transaction attribute of the same name.
     * <p>Note: For transaction managers with transaction synchronization,
     * {@code SUPPORTS} is slightly different from no transaction at all,
     * as it defines a transaction scope that synchronization will apply for.
     * As a consequence, the same resources (JDBC Connection, Hibernate Session, etc)
     * will be shared for the entire specified scope. Note that this depends on
     * the actual synchronization configuration of the transaction manager.
     */
    SUPPORTS(1),

    /**
     * Support a current transaction, throw an exception if none exists.
     * Analogous to EJB transaction attribute of the same name.
     */
    MANDATORY(2),

    /**
     * Create a new transaction, and suspend the current transaction if one exists.
     * Analogous to the EJB transaction attribute of the same name.
     */
    REQUIRES_NEW(3),

    /**
     * Execute non-transactionally, suspend the current transaction if one exists.
     * Analogous to EJB transaction attribute of the same name.
     */
    NOT_SUPPORTED(4),

    /**
     * Execute non-transactionally, throw an exception if a transaction exists.
     * Analogous to EJB transaction attribute of the same name.
     */
    NEVER(5);


    private final int value;


    TransactionPropagation(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }

}
