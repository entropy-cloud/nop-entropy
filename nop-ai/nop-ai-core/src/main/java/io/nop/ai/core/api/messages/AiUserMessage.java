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
package io.nop.ai.core.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.core.AiCoreConstants;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.IResource;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated This internal AI core class is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@DataBean
@Deprecated
public class AiUserMessage extends AbstractTextMessage {
    private List<AiMessageAttachment> attachments;

    public AiUserMessage() {
    }

    public AiUserMessage(String content) {
        setContent(content);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<AiMessageAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AiMessageAttachment> attachments) {
        this.attachments = attachments;
    }

    public AiUserMessage addImage(IResource resource) {
        if (attachments == null)
            attachments = new ArrayList<>();
        attachments.add(AiMessageAttachment.forImage(resource));
        return this;
    }

    @Override
    public String getRole() {
        return AiCoreConstants.ROLE_USER;
    }
}
