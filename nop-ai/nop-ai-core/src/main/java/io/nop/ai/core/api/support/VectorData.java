/*
 *  Copyright (c) 2023-2025, Agents-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.nop.ai.core.api.support;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Arrays;


@DataBean
/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 仅
 * reserved 族内类——document/embedding/vectorstore/classifier——与测试引用），vector SPI 契约族
 * 的基础 value 类型（与 {@code IVectorStore}/{@code IEmbeddingModel} 的 P1-MA5-003 SPI 裁定
 * 一致）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
public class VectorData extends Metadata {

    private double[] vector;

    public double[] getVector() {
        return vector;
    }

    public void setVector(double[] vector) {
        this.vector = vector;
    }

    @Override
    public String toString() {
        return "VectorData{" +
                "metadataMap=" + metadata +
                ", vector=" + Arrays.toString(vector) +
                '}';
    }
}