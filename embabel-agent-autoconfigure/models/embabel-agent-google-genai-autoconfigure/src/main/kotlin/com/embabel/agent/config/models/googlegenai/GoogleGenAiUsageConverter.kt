/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.agent.config.models.googlegenai

import com.embabel.agent.core.Usage
import com.embabel.agent.spi.support.springai.SpringAiUsageConverter
import com.embabel.agent.spi.support.springai.toEmbabelUsage
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage

/**
 * Gemini reports its thinking apart from the completion, in `thoughtsTokenCount`. It is generated and billed as
 * output, so it is added to the completion, as OpenAI and Anthropic count it, and kept in [Usage.thinkingTokens].
 */
object GoogleGenAiUsageConverter : SpringAiUsageConverter {

    override fun convert(usage: org.springframework.ai.chat.metadata.Usage): Usage {
        val embabel = usage.toEmbabelUsage()
        val thinking = (usage as? GoogleGenAiUsage)?.thoughtsTokenCount?.takeIf { it > 0 } ?: return embabel
        return embabel.copy(
            completionTokens = (embabel.completionTokens ?: 0) + thinking,
            thinkingTokens = thinking,
        )
    }
}
