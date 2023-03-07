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
package io.nop.svg.parse;

import io.nop.svg.model.SVGPath;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * This class provides an implementation of the PathHandler that initializes a Shape from the value of a path's 'd'
 * attribute.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id: AWTPathProducer.java 475477 2006-11-15 22:44:28Z cam $
 */
public class AWTPathProducer {

    /**
     * The temporary value of extendedGeneralPath.
     */
    protected SVGPath path;

    /**
     * The current x position.
     */
    protected float currentX;

    /**
     * The current y position.
     */
    protected float currentY;

    /**
     * The reference x point for smooth arcs.
     */
    protected float xCenter;

    /**
     * The reference y point for smooth arcs.
     */
    protected float yCenter;

    /**
     * The winding rule to use to construct the path.
     */
    protected int windingRule;

    public void setWindingRule(int i) {
        windingRule = i;
    }

    /**
     * Returns the current winding rule.
     */
    public int getWindingRule() {
        return windingRule;
    }

    /**
     * Returns the Shape object initialized during the last parsing.
     *
     * @return the shape or null if this handler has not been used by a parser.
     */
    public Shape getShape() {
        return (Shape) path;
    }

    public void startPath() {
        currentX = 0;
        currentY = 0;
        xCenter = 0;
        yCenter = 0;
        path = new SVGPath(windingRule);
    }

    public void endPath() {
    }

    public void movetoRel(float x, float y) {
        path.moveTo(xCenter = currentX += x, yCenter = currentY += y);
    }

    public void movetoAbs(float x, float y) {
        path.moveTo(xCenter = currentX = x, yCenter = currentY = y);
    }

    public void closePath() {
        path.closePath();
        Point2D pt = path.getCurrentPoint();
        currentX = (float) pt.getX();
        currentY = (float) pt.getY();
    }

    public void linetoRel(float x, float y) {
        path.lineTo(xCenter = currentX += x, yCenter = currentY += y);
    }

    public void linetoAbs(float x, float y) {
        path.lineTo(xCenter = currentX = x, yCenter = currentY = y);
    }

    public void linetoHorizontalRel(float x) {
        path.lineTo(xCenter = currentX += x, yCenter = currentY);
    }

    public void linetoHorizontalAbs(float x) {
        path.lineTo(xCenter = currentX = x, yCenter = currentY);
    }

    public void linetoVerticalRel(float y) {
        path.lineTo(xCenter = currentX, yCenter = currentY += y);
    }

    public void linetoVerticalAbs(float y) {
        path.lineTo(xCenter = currentX, yCenter = currentY = y);
    }

    public void curvetoCubicRel(float x1, float y1, float x2, float y2, float x, float y) {
        path.curveTo(currentX + x1, currentY + y1, xCenter = currentX + x2, yCenter = currentY + y2, currentX += x,
                currentY += y);
    }

    public void curvetoCubicAbs(float x1, float y1, float x2, float y2, float x, float y) {
        path.curveTo(x1, y1, xCenter = x2, yCenter = y2, currentX = x, currentY = y);
    }

    public void curvetoCubicSmoothRel(float x2, float y2, float x, float y) {
        path.curveTo(currentX * 2 - xCenter, currentY * 2 - yCenter, xCenter = currentX + x2, yCenter = currentY + y2,
                currentX += x, currentY += y);
    }

    public void curvetoCubicSmoothAbs(float x2, float y2, float x, float y) {
        path.curveTo(currentX * 2 - xCenter, currentY * 2 - yCenter, xCenter = x2, yCenter = y2, currentX = x,
                currentY = y);
    }

    public void curvetoQuadraticRel(float x1, float y1, float x, float y) {
        path.quadTo(xCenter = currentX + x1, yCenter = currentY + y1, currentX += x, currentY += y);
    }

    public void curvetoQuadraticAbs(float x1, float y1, float x, float y) {
        path.quadTo(xCenter = x1, yCenter = y1, currentX = x, currentY = y);
    }

    public void curvetoQuadraticSmoothRel(float x, float y) {
        path.quadTo(xCenter = currentX * 2 - xCenter, yCenter = currentY * 2 - yCenter, currentX += x, currentY += y);
    }

    public void curvetoQuadraticSmoothAbs(float x, float y) {
        path.quadTo(xCenter = currentX * 2 - xCenter, yCenter = currentY * 2 - yCenter, currentX = x, currentY = y);
    }

    public void arcRel(float rx, float ry, float xAxisRotation, boolean largeArcFlag, boolean sweepFlag, float x,
                       float y) {
        path.arcTo(rx, ry, xAxisRotation, largeArcFlag, sweepFlag, xCenter = currentX += x, yCenter = currentY += y);
    }

    public void arcAbs(float rx, float ry, float xAxisRotation, boolean largeArcFlag, boolean sweepFlag, float x,
                       float y) {
        path.arcTo(rx, ry, xAxisRotation, largeArcFlag, sweepFlag, xCenter = currentX = x, yCenter = currentY = y);
    }
}
