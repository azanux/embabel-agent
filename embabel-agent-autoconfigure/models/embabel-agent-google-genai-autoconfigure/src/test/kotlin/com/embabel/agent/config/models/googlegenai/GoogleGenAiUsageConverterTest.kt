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

import com.google.genai.types.GenerateContentResponseUsageMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage

class GoogleGenAiUsageConverterTest {

    private fun gemini(prompt: Int, candidates: Int, thoughts: Int?, toolUsePrompt: Int = 0) = GoogleGenAiUsage.from(
        GenerateContentResponseUsageMetadata.builder()
            .promptTokenCount(prompt)
            .candidatesTokenCount(candidates)
            .apply { thoughts?.let { thoughtsTokenCount(it) } }
            .toolUsePromptTokenCount(toolUsePrompt)
            .totalTokenCount(prompt + candidates + (thoughts ?: 0) + toolUsePrompt)
            .build()
    )

    @Test
    fun `thinking is counted in the completion and kept apart as a detail`() {
        val usage = GoogleGenAiUsageConverter.convert(gemini(60, 1, 95))

        assertEquals(60, usage.promptTokens)
        assertEquals(96, usage.completionTokens)
        assertEquals(95, usage.thinkingTokens)
        assertEquals(156, usage.totalTokens)
    }

    @Test
    fun `tool-use prompt tokens in the total are not taken for thinking`() {
        val usage = GoogleGenAiUsageConverter.convert(gemini(60, 10, 30, toolUsePrompt = 500))

        assertEquals(40, usage.completionTokens)
        assertEquals(30, usage.thinkingTokens)
    }

    @Test
    fun `no thinking leaves the usage as reported`() {
        val usage = GoogleGenAiUsageConverter.convert(gemini(60, 10, null))

        assertEquals(10, usage.completionTokens)
        assertNull(usage.thinkingTokens)
    }

    @Test
    fun `a usage that is not Gemini's is converted as any other`() {
        val usage = GoogleGenAiUsageConverter.convert(DefaultUsage(100, 50, 400))

        assertEquals(50, usage.completionTokens)
        assertNull(usage.thinkingTokens)
    }
}
