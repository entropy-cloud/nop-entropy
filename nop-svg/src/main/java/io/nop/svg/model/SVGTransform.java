package io.nop.svg.model;
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

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.svg.parse.SVGTransformParser;
import io.nop.svg.parse.TransformListHandler;

import java.awt.geom.AffineTransform;

import static io.nop.svg.SVGErrors.ERR_SVG_MATRIX_INVERT_FAIL;

/**
 * This class provides an implementation of the PathHandler that initializes an AffineTransform from the value of a
 * 'transform' attribute.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public class SVGTransform implements TransformListHandler {
    /**
     * The transform used to implement flipX.
     */
    protected static final AffineTransform FLIP_X_TRANSFORM = new AffineTransform(-1, 0, 0, 1, 0, 0);

    /**
     * The transform used to implement flipX.
     */
    protected static final AffineTransform FLIP_Y_TRANSFORM = new AffineTransform(1, 0, 0, -1, 0, 0);

    /**
     * The value of the current affine transform.
     */
    protected AffineTransform affineTransform;

    /**
     * Utility method for creating an AffineTransform.
     *
     * @param s The transform specification.
     */
    public static SVGTransform parse(String s) {
        if (StringHelper.isBlank(s))
            return null;

        SVGTransform th = new SVGTransform(null);
        SVGTransformParser p = new SVGTransformParser(th);
        p.parse(s);

        return th;
    }

    public SVGTransform clone() {
        return new SVGTransform((AffineTransform) affineTransform.clone());
    }

    /**
     * Returns the AffineTransform object initialized during the last parsing.
     *
     * @return the transform or null if this handler has not been used by a parser.
     */
    public AffineTransform getAffineTransform() {
        return affineTransform;
    }

    /**
     * Implements {@link TransformListHandler#startTransformList()}.
     */
    public void startTransformList() {
        affineTransform = new AffineTransform();
    }

    public SVGTransform(AffineTransform trans) {
        this.affineTransform = trans;
    }

    public SVGTransform() {
        this.affineTransform = new AffineTransform();
    }

    /**
     * Implements {@link TransformListHandler#matrix(float, float, float, float, float, float)}.
     */
    public void matrix(float a, float b, float c, float d, float e, float f) {
        affineTransform.concatenate(new AffineTransform(a, b, c, d, e, f));
    }

    /**
     * Implements {@link TransformListHandler#rotate(float)}.
     */
    public void rotate(float theta) {
        affineTransform.concatenate(AffineTransform.getRotateInstance(Math.toRadians(theta)));
    }

    /**
     * Implements {@link TransformListHandler#rotate(float, float, float)}.
     */
    public void rotate(float theta, float cx, float cy) {
        AffineTransform at = AffineTransform.getRotateInstance(Math.toRadians(theta), cx, cy);
        affineTransform.concatenate(at);
    }

    /**
     * Implements {@link TransformListHandler#translate(float)}.
     */
    public void translate(float tx) {
        affineTransform.translate(tx, 0);
    }

    /**
     * Implements {@link TransformListHandler#translate(float, float)}.
     */
    public void translate(float tx, float ty) {
        affineTransform.translate(tx, ty);
    }

    /**
     * Implements {@link TransformListHandler#scale(float)}.
     */
    public void scale(float sx) {
        affineTransform.scale(sx, sx);
    }

    /**
     * Implements {@link TransformListHandler#scale(float, float)}.
     */
    public void scale(float sx, float sy) {
        affineTransform.scale(sx, sy);
    }

    /**
     * Implements {@link TransformListHandler#skewX(float)}.
     */
    public void skewX(float skx) {
        affineTransform.concatenate(AffineTransform.getShearInstance(Math.tan(Math.toRadians(skx)), 0));
    }

    /**
     * Implements {@link TransformListHandler#skewY(float)}.
     */
    public void skewY(float sky) {
        affineTransform.concatenate(AffineTransform.getShearInstance(0, Math.tan(Math.toRadians(sky))));
    }

    /**
     * Implements {@link TransformListHandler#endTransformList()}.
     */
    public void endTransformList() {
    }

    public void flipX() {
        affineTransform.concatenate(FLIP_X_TRANSFORM);
    }

    public void flipY() {
        affineTransform.concatenate(FLIP_Y_TRANSFORM);
    }

    public void rotateFromVector(float x, float y) {
        Guard.checkArgument(x != 0 || y != 0);
        affineTransform.rotate(Math.atan2(y, x));
    }

    public void invert() {
        try {
            affineTransform.invert();
        } catch (Exception e) {
            throw new NopException(ERR_SVG_MATRIX_INVERT_FAIL, e);
        }
    }

    public void multiply(SVGTransform matrix) {
        affineTransform.concatenate(matrix.getAffineTransform());
    }

    public float getA() {
        return (float) getAffineTransform().getScaleX();
    }

    public void setA(float a) {
        AffineTransform at = getAffineTransform();
        at.setTransform(a, at.getShearY(), at.getShearX(), at.getScaleY(), at.getTranslateX(), at.getTranslateY());
    }

    public float getB() {
        return (float) getAffineTransform().getShearY();
    }

    public void setB(float b) {
        AffineTransform at = getAffineTransform();
        at.setTransform(at.getScaleX(), b, at.getShearX(), at.getScaleY(), at.getTranslateX(), at.getTranslateY());
    }

    public float getC() {
        return (float) getAffineTransform().getShearX();
    }

    public void setC(float c) {
        AffineTransform at = getAffineTransform();
        at.setTransform(at.getScaleX(), at.getShearY(), c, at.getScaleY(), at.getTranslateX(), at.getTranslateY());
    }

    public float getD() {
        return (float) getAffineTransform().getScaleY();
    }

    public void setD(float d) {
        AffineTransform at = getAffineTransform();
        at.setTransform(at.getScaleX(), at.getShearY(), at.getShearX(), d, at.getTranslateX(), at.getTranslateY());
    }

    public float getE() {
        return (float) getAffineTransform().getTranslateX();
    }

    public void setE(float e) {
        AffineTransform at = getAffineTransform();
        at.setTransform(at.getScaleX(), at.getShearY(), at.getShearX(), at.getScaleY(), e, at.getTranslateY());
    }

    public float getF() {
        return (float) getAffineTransform().getTranslateY();
    }

    public void setF(float f) {
        AffineTransform at = getAffineTransform();
        at.setTransform(at.getScaleX(), at.getShearY(), at.getShearX(), at.getScaleY(), at.getTranslateX(), f);
    }

    public double getTranslateX() {
        return affineTransform.getTranslateX();
    }

    public double getTranslateY() {
        return affineTransform.getTranslateY();
    }

    public double getScaleX() {
        return affineTransform.getScaleX();
    }

    public double getScaleY() {
        return affineTransform.getScaleY();
    }

    public String toString() {
        return toSVGString();
    }

    /**
     * Returns the string representation of this transform.
     */
    public String toSVGString() {
        StringBuilder buf = new StringBuilder();
        int type = affineTransform.getType();
        switch (type) {
            case AffineTransform.TYPE_TRANSLATION:
                buf.append("translate(");
                buf.append((float) affineTransform.getTranslateX());
                if (affineTransform.getTranslateY() != 0) {
                    buf.append(' ');
                    buf.append((float) affineTransform.getTranslateY());
                }
                buf.append(')');
                break;
            case AffineTransform.TYPE_UNIFORM_SCALE:
            case AffineTransform.TYPE_GENERAL_SCALE:
                buf.append("scale(");
                buf.append((float) affineTransform.getScaleX());
                buf.append(' ');
                buf.append((float) affineTransform.getScaleY());
                buf.append(')');
                break;
            default:
                buf.append("matrix(");
                double[] matrix = new double[6];
                affineTransform.getMatrix(matrix);
                for (int i = 0; i < 6; i++) {
                    if (i != 0) {
                        buf.append(' ');
                    }
                    buf.append((float) matrix[i]);
                }
                buf.append(')');
                break;
        }
        return buf.toString();
    }
}
