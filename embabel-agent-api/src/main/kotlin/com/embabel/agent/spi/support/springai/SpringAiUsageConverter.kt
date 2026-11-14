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
package com.embabel.agent.spi.support.springai

import com.embabel.agent.core.Usage

/**
 * SPI for turning a provider's Spring AI usage into embabel's [Usage].
 *
 * The completion holds every token the model generated, its thinking included, as OpenAI and Anthropic report it.
 * A provider that reports its thinking apart, as Gemini does, wires a converter that adds it to the completion and
 * keeps it in [Usage.thinkingTokens]; the others use [DEFAULT].
 *
 * Follows the same injected-collaborator pattern as [SpringAiNativeThinkingExtractor].
 */
fun interface SpringAiUsageConverter {

    fun convert(usage: org.springframework.ai.chat.metadata.Usage): Usage

    companion object {
        @JvmField
        val DEFAULT = SpringAiUsageConverter { it.toEmbabelUsage() }
    }
}
