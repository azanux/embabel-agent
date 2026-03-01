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
package com.embabel.agent.observability.observation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationKeysTest {

    @Nested
    @DisplayName("toolLoopSpanName")
    class ToolLoopSpanName {

        @Test
        @DisplayName("should return constant name to avoid metric cardinality explosion")
        void shouldReturnConstantName() {
            // The span name must be the same regardless of interactionId,
            // because Micrometer creates a new meter for each unique observation name.
            // Using interactionId in the name causes unbounded metric cardinality (see #1449).
            String name1 = ObservationKeys.toolLoopSpanName("Agent.respond-com.example.Response-1");
            String name2 = ObservationKeys.toolLoopSpanName("Agent.respond-com.example.Response-2");
            String name3 = ObservationKeys.toolLoopSpanName("Agent.respond-com.example.Response-1006");

            assertThat(name1).isEqualTo(name2);
            assertThat(name2).isEqualTo(name3);
        }

        @Test
        @DisplayName("should not contain the interactionId in the name")
        void shouldNotContainInteractionId() {
            String interactionId = "Agent.respond-com.example.Response-42";
            String name = ObservationKeys.toolLoopSpanName(interactionId);

            assertThat(name).doesNotContain("42");
            assertThat(name).doesNotContain("Agent.respond");
            assertThat(name).doesNotContain("com.example");
        }
    }

    @Nested
    @DisplayName("map keys should still use interactionId for uniqueness")
    class MapKeys {

        @Test
        @DisplayName("toolLoopKey should contain interactionId for map lookups")
        void toolLoopKeyShouldContainInteractionId() {
            String key = ObservationKeys.toolLoopKey("run-1", "interaction-42");
            assertThat(key).contains("interaction-42");
        }

        @Test
        @DisplayName("llmKey should contain interactionId for map lookups")
        void llmKeyShouldContainInteractionId() {
            String key = ObservationKeys.llmKey("run-1", "interaction-42");
            assertThat(key).contains("interaction-42");
        }
    }
}