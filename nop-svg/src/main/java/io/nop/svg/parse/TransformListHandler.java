package io.nop.svg.parse;

/*

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

/**
 * This interface must be implemented and then registred as the handler of a <code>TransformParser</code> instance in
 * order to be notified of parsing events.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public interface TransformListHandler {
    /**
     * Invoked when the tranform starts.
     */
    default void startTransformList() {
    }

    /**
     * Invoked when 'matrix(a, b, c, d, e, f)' has been parsed.
     */
    default void matrix(float a, float b, float c, float d, float e, float f) {
    }

    /**
     * Invoked when 'rotate(theta)' has been parsed.
     */
    default void rotate(float theta) {
    }

    /**
     * Invoked when 'rotate(theta, cx, cy)' has been parsed.
     */
    default void rotate(float theta, float cx, float cy) {
    }

    /**
     * Invoked when 'translate(tx)' has been parsed.
     */
    default void translate(float tx) {
    }

    /**
     * Invoked when 'translate(tx, ty)' has been parsed.
     */
    default void translate(float tx, float ty) {
    }

    /**
     * Invoked when 'scale(sx)' has been parsed.
     */
    default void scale(float sx) {
    }

    /**
     * Invoked when 'scale(sx, sy)' has been parsed.
     */
    default void scale(float sx, float sy) {
    }

    /**
     * Invoked when 'skewX(skx)' has been parsed.
     */
    default void skewX(float skx) {
    }

    /**
     * Invoked when 'skewY(sky)' has been parsed.
     */
    default void skewY(float sky) {
    }

    /**
     * Invoked when the transform ends.
     */
    default void endTransformList() {
    }
}
