package io.nop.excel.model.constants;

/* ====================================================================
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
==================================================================== */

//package org.apache.poi.ss.usermodel;

/**
 * Specifies printed page order.
 *
 * @author Gisella Bronzetti
 */
public enum ExcelPageOrder {

    /**
     * Order pages vertically first, then move horizontally.
     */
    DOWN_THEN_OVER(1),
    /**
     * Order pages horizontally first, then move vertically
     */
    OVER_THEN_DOWN(2);

    private final int order;

    ExcelPageOrder(int order) {
        this.order = order;
    }

    public int getValue() {
        return order;
    }

    private static ExcelPageOrder[] _table = new ExcelPageOrder[3];

    static {
        for (ExcelPageOrder c : values()) {
            _table[c.getValue()] = c;
        }
    }

    public static ExcelPageOrder valueOf(int value) {
        return _table[value];
    }
}
