/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.util;

import com.google.common.base.Preconditions;
import io.nop.stream.core.common.typeinfo.TypeInformation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;

/**
 * An {@link OutputTag} is a typed and named tag to use for tagging side outputs of an operator.
 *
 * <p>An {@code OutputTag} must always be an anonymous inner class so that Flink can derive a {@link
 * TypeInformation} for the generic type parameter.
 *
 * <p>Example:
 *
 * <pre>{@code
 * OutputTag<Tuple2<String, Long>> info = new OutputTag<Tuple2<String, Long>>("late-data"){};
 * }</pre>
 *
 * @param <T> the type of elements in the side-output stream.
 */
public class OutputTag<T> implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String id;

    private final TypeInformation<T> typeInfo;

    /**
     * Creates a new named {@code OutputTag} with the given id and output {@link TypeInformation}.
     *
     * @param id       The id of the created {@code OutputTag}.
     * @param typeInfo The {@code TypeInformation} for the side output.
     */
    public OutputTag(String id, TypeInformation<T> typeInfo) {
        Preconditions.checkNotNull(id, "OutputTag id cannot be null.");
        Preconditions.checkArgument(!id.isEmpty(), "OutputTag id must not be empty.");
        this.id = id;
        this.typeInfo = Preconditions.checkNotNull(typeInfo, "TypeInformation cannot be null.");
    }

    public static boolean isResponsibleFor(
            @Nullable OutputTag<?> owner, @Nonnull OutputTag<?> other) {
        return other.equals(owner);
    }

    // ------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public TypeInformation<T> getTypeInfo() {
        return typeInfo;
    }

    // ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !(obj instanceof OutputTag)) {
            return false;
        }
        OutputTag other = (OutputTag) obj;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "OutputTag(" + getTypeInfo() + ", " + id + ")";
    }
}
