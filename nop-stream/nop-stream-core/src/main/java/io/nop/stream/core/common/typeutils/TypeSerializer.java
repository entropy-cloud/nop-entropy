/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.common.typeutils;


import java.io.Serializable;

/**
 * This interface describes the methods that are required for a data type to be handled by the Flink
 * runtime. Specifically, this interface contains the serialization and copying methods.
 *
 * <p>The methods in this class are not necessarily thread safe. To avoid unpredictable side
 * effects, it is recommended to call {@code duplicate()} method and use one serializer instance per
 * thread.
 *
 * <p><b>Upgrading TypeSerializers to the new TypeSerializerSnapshot model</b>
 *
 * <p>This section is relevant if you implemented a TypeSerializer in Flink versions up to 1.6 and
 * want to adapt that implementation to the new interfaces that support proper state schema
 * evolution, while maintaining backwards compatibility. Please follow these steps:
 *
 * <ul>
 *   <li>Change the type serializer's config snapshot to implement {@link TypeSerializerSnapshot},
 *       rather than extending {@code TypeSerializerConfigSnapshot} (as previously).
 *   <li>If the above step was completed, then the upgrade is done. Otherwise, if changing to
 *       implement {@link TypeSerializerSnapshot} directly in-place as the same class isn't possible
 *       (perhaps because the new snapshot is intended to have completely different written contents
 *       or intended to have a different class name), retain the old serializer snapshot class
 *       (extending {@code TypeSerializerConfigSnapshot}) under the same name and give the updated
 *       serializer snapshot class (the one extending {@code TypeSerializerSnapshot}) a new name.
 *   <li>Override the {@code
 *       TypeSerializerConfigSnapshot#resolveSchemaCompatibility(TypeSerializer)} method to perform
 *       the compatibility check based on configuration written by the old serializer snapshot
 *       class.
 * </ul>
 *
 * @param <T> The data type that the serializer serializes.
 */
public interface TypeSerializer<T> extends Serializable {

    // --------------------------------------------------------------------------------------------
    // General information about the type and the serializer
    // --------------------------------------------------------------------------------------------

    /**
     * Gets whether the type is an immutable type.
     *
     * @return True, if the type is immutable.
     */
    boolean isImmutableType();

    /**
     * Creates a deep copy of this serializer if it is necessary, i.e. if it is stateful. This can
     * return itself if the serializer is not stateful.
     *
     * <p>We need this because Serializers might be used in several threads. Stateless serializers
     * are inherently thread-safe while stateful serializers might not be thread-safe.
     */
    TypeSerializer<T> duplicate();

    // --------------------------------------------------------------------------------------------
    // Instantiation & Cloning
    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new instance of the data type.
     *
     * @return A new instance of the data type.
     */
    T createInstance();

    /**
     * Creates a deep copy of the given element in a new element.
     *
     * @param from The element reuse be copied.
     * @return A deep copy of the element.
     */
    T copy(T from);

    /**
     * Creates a copy from the given element. The method makes an attempt to store the copy in the
     * given reuse element, if the type is mutable. This is, however, not guaranteed.
     *
     * @param from  The element to be copied.
     * @param reuse The element to be reused. May or may not be used.
     * @return A deep copy of the element.
     */
    T copy(T from, T reuse);

    // --------------------------------------------------------------------------------------------

    /**
     * Gets the length of the data type, if it is a fix length data type.
     *
     * @return The length of the data type, or <code>-1</code> for variable length data types.
     */
    int getLength();


}
