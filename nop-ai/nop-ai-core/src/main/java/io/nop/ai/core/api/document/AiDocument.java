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
package io.nop.ai.core.api.document;

import io.nop.ai.core.api.support.VectorData;

/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），document/embedding/vectorstore SPI 契约族的 value 类型（与 {@code IVectorStore}/
 * {@code IEmbeddingModel} 的 P1-MA5-003 SPI 裁定一致）。保留为公共 API 预留；删除需单独 plan +
 * 迁移评估。
 */
public class AiDocument extends VectorData {

    /**
     * Document ID
     */
    private Object id;

    /**
     * Document Content
     */
    private String content;


    public AiDocument() {
    }

    public AiDocument(String content) {
        this.content = content;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public static AiDocument fromText(String content) {
        AiDocument document = new AiDocument();
        document.setContent(content);
        return document;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", metadataMap=" + metadata +
                '}';
    }
}
