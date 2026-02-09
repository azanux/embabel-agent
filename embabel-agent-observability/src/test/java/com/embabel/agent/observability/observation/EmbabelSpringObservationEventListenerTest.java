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

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.api.event.*;
import com.embabel.agent.core.*;
import com.embabel.agent.event.AgentProcessRagEvent;
import com.embabel.agent.event.RagRequestReceivedEvent;
import com.embabel.agent.event.RagResponseEvent;
import com.embabel.agent.core.support.LlmInteraction;
import com.embabel.agent.observability.ObservabilityProperties;
import com.embabel.agent.rag.service.RagRequest;
import com.embabel.agent.rag.service.RagResponse;
import com.embabel.agent.spi.Ranking;
import com.embabel.agent.spi.Rankings;
import com.embabel.common.ai.model.LlmMetadata;
import com.embabel.common.ai.model.LlmOptions;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for EmbabelSpringObservationEventListener initialization and RAG events.
 */
@ExtendWith(MockitoExtension.class)
class EmbabelSpringObservationEventListenerTest {

    @Mock
    private Tracer tracer;

    private ObservabilityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ObservabilityProperties();
    }

    @Test
    void constructor_shouldCreateListener() {
        EmbabelSpringObservationEventListener listener =
                new EmbabelSpringObservationEventListener(tracer, properties);

        assertThat(listener).isNotNull();
    }

    @Test
    void constructor_shouldAcceptCustomProperties() {
        properties.setMaxAttributeLength(2000);
        properties.setTraceToolCalls(false);
        properties.setTracePlanning(false);

        EmbabelSpringObservationEventListener listener =
                new EmbabelSpringObservationEventListener(tracer, properties);

        assertThat(listener).isNotNull();
    }

    // ================================================================================
    // RAG EVENT TESTS
    // ================================================================================

    @Nested
    @DisplayName("RAG Event Tests")
    class RagEventTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            // Set up chaining: tracer.nextSpan().name() returns mockSpan
            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);
        }

        @Test
        @DisplayName("RAG request event should create span with correct name")
        void ragRequestEvent_shouldCreateSpan_withCorrectName() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");
            RagRequest ragRequest = RagRequest.Companion.query("What is the meaning of life?");
            RagRequestReceivedEvent ragEvent = new RagRequestReceivedEvent(ragRequest, java.time.Instant.now());
            AgentProcessRagEvent event = new AgentProcessRagEvent(process, ragEvent);

            // Create agent span first
            listener.onProcessEvent(new AgentProcessCreationEvent(process));

            // Fire RAG event
            listener.onProcessEvent(event);

            // Verify span was created with name "rag:request"
            verify(mockSpan, atLeastOnce()).name("rag:request");
            verify(mockSpan, atLeastOnce()).tag("embabel.rag.query", "What is the meaning of life?");
            verify(mockSpan, atLeastOnce()).tag("embabel.event.type", "rag");
        }

        @Test
        @DisplayName("RAG response event should create span with result count")
        void ragResponseEvent_shouldCreateSpan_withResultCount() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");
            RagRequest ragRequest = RagRequest.Companion.query("test query");
            RagResponse ragResponse = new RagResponse(ragRequest, "test-service", Collections.emptyList(), null, null, java.time.Instant.now());
            RagResponseEvent ragEvent = new RagResponseEvent(ragResponse, java.time.Instant.now());
            AgentProcessRagEvent event = new AgentProcessRagEvent(process, ragEvent);

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(event);

            verify(mockSpan, atLeastOnce()).name("rag:response");
            verify(mockSpan, atLeastOnce()).tag("embabel.rag.result_count", "0");
        }

        @Test
        @DisplayName("RAG event should NOT create span when traceRag is false")
        void ragEvent_shouldNotCreateSpan_whenDisabled() {
            properties.setTraceRag(false);

            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");
            RagRequest ragRequest = RagRequest.Companion.query("test query");
            RagRequestReceivedEvent ragEvent = new RagRequestReceivedEvent(ragRequest, java.time.Instant.now());
            AgentProcessRagEvent event = new AgentProcessRagEvent(process, ragEvent);

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(event);

            // Should never create a span named "rag:request"
            verify(mockSpan, never()).name("rag:request");
        }
    }

    // ================================================================================
    // REPLAN REQUESTED TESTS
    // ================================================================================

    @Nested
    @DisplayName("Replan Requested Tests")
    class ReplanRequestedTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);

            properties.setTracePlanning(true);
        }

        @Test
        @DisplayName("ReplanRequestedEvent should create span with reason")
        void replanRequested_shouldCreateSpan_withReason() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(new ReplanRequestedEvent(process, "Tool loop detected issue"));

            verify(mockSpan, atLeastOnce()).name("planning:replan_requested");
            verify(mockSpan, atLeastOnce()).tag("embabel.replan.reason", "Tool loop detected issue");
        }

        @Test
        @DisplayName("ReplanRequestedEvent should NOT create span when planning tracing disabled")
        void replanRequested_shouldNotCreateSpan_whenDisabled() {
            properties.setTracePlanning(false);

            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(new ReplanRequestedEvent(process, "reason"));

            verify(mockSpan, never()).name("planning:replan_requested");
        }
    }

    // ================================================================================
    // LIFECYCLE STATE ERROR TESTS
    // ================================================================================

    @Nested
    @DisplayName("Lifecycle State Error Tests")
    class LifecycleStateErrorTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);

            properties.setTraceLifecycleStates(true);
        }

        @Test
        @DisplayName("AgentProcessStuckEvent should call span.error()")
        void stuckEvent_shouldCallSpanError() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(new AgentProcessStuckEvent(process));

            verify(mockSpan, atLeastOnce()).name("lifecycle:stuck");
            verify(mockSpan).error(any(RuntimeException.class));
        }

        @Test
        @DisplayName("AgentProcessWaitingEvent should NOT call span.error()")
        void waitingEvent_shouldNotCallSpanError() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(new AgentProcessWaitingEvent(process));

            verify(mockSpan, atLeastOnce()).name("lifecycle:waiting");
            verify(mockSpan, never()).error(any(Throwable.class));
        }
    }

    // ================================================================================
    // ACTION RESULT TESTS
    // ================================================================================

    @Nested
    @DisplayName("Action Result Tests")
    class ActionResultTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);
        }

        @Test
        @DisplayName("Failed action should call span.error()")
        void failedAction_shouldCallSpanError() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));

            ActionExecutionStartEvent actionStart = createMockActionStartEvent(process, "com.example.MyAction", "MyAction");
            listener.onProcessEvent(actionStart);

            ActionExecutionResultEvent actionResult = createMockActionResultEvent(process, "com.example.MyAction", "FAILED");
            listener.onProcessEvent(actionResult);

            verify(mockSpan, atLeastOnce()).tag("embabel.action.status", "FAILED");
            verify(mockSpan).error(any(RuntimeException.class));
        }

        @Test
        @DisplayName("Succeeded action should not call span.error()")
        void succeededAction_shouldNotCallSpanError() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));

            ActionExecutionStartEvent actionStart = createMockActionStartEvent(process, "com.example.MyAction", "MyAction");
            listener.onProcessEvent(actionStart);

            ActionExecutionResultEvent actionResult = createMockActionResultEvent(process, "com.example.MyAction", "SUCCEEDED");
            listener.onProcessEvent(actionResult);

            verify(mockSpan, atLeastOnce()).tag("embabel.action.status", "SUCCEEDED");
            verify(mockSpan, never()).error(any(Throwable.class));
        }
    }

    // ================================================================================
    // LLM CALL TESTS
    // ================================================================================

    @Nested
    @DisplayName("LLM Call Tests")
    class LlmCallTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);

            properties.setTraceLlmCalls(true);
        }

        @Test
        @DisplayName("LLM span should have GenAI hyperparameter attributes")
        void llmSpan_shouldHaveGenAiHyperparameterAttributes() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            // Create agent span first
            listener.onProcessEvent(new AgentProcessCreationEvent(process));

            // Fire LLM request
            LlmRequestEvent<?> llmRequest = createMockLlmRequestEvent(process, "com.example.MyAction", "gpt-4", String.class);
            listener.onProcessEvent(llmRequest);

            // Verify hyperparameter tags
            verify(mockSpan, atLeastOnce()).tag("gen_ai.request.temperature", "0.7");
            verify(mockSpan, atLeastOnce()).tag("gen_ai.request.max_tokens", "1000");
            verify(mockSpan, atLeastOnce()).tag("gen_ai.request.top_p", "0.9");
            verify(mockSpan, atLeastOnce()).tag("gen_ai.provider.name", "openai");
        }
    }

    @Nested
    @DisplayName("LLM Response Error Tests")
    class LlmResponseErrorTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);

            properties.setTraceLlmCalls(true);
        }

        @Test
        @DisplayName("LLM response with Throwable should call span.error()")
        void llmResponse_shouldCallSpanError_whenThrowable() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));

            LlmRequestEvent<?> llmRequest = createMockLlmRequestEvent(process, "com.example.MyAction", "gpt-4", Object.class);
            listener.onProcessEvent(llmRequest);

            @SuppressWarnings("unchecked")
            LlmRequestEvent<Object> typedRequest = (LlmRequestEvent<Object>) llmRequest;
            LlmResponseEvent<Object> llmResponse = typedRequest.responseEvent(
                    new RuntimeException("LLM call failed"), java.time.Duration.ofMillis(150));
            listener.onProcessEvent(llmResponse);

            verify(mockSpan).error(any(Throwable.class));
        }
    }

    // ================================================================================
    // PARALLEL LLM CALL TESTS
    // ================================================================================

    @Nested
    @DisplayName("Parallel LLM Call Tests")
    class ParallelLlmCallTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);

            properties.setTraceLlmCalls(true);
        }

        @Test
        @DisplayName("Parallel LLM calls should each produce span end() calls")
        void parallelLlmCalls_shouldProduceDistinctSpanEndCalls() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));

            // Fire 3 parallel LLM requests (same runId + actionName)
            LlmRequestEvent<?> llmRequest1 = createMockLlmRequestEvent(process, "com.example.MyAction", "gpt-4", String.class);
            LlmRequestEvent<?> llmRequest2 = createMockLlmRequestEvent(process, "com.example.MyAction", "gpt-4", String.class);
            LlmRequestEvent<?> llmRequest3 = createMockLlmRequestEvent(process, "com.example.MyAction", "gpt-4", String.class);

            listener.onProcessEvent(llmRequest1);
            listener.onProcessEvent(llmRequest2);
            listener.onProcessEvent(llmRequest3);

            // Complete them
            @SuppressWarnings("unchecked")
            LlmRequestEvent<String> typed1 = (LlmRequestEvent<String>) llmRequest1;
            @SuppressWarnings("unchecked")
            LlmRequestEvent<String> typed2 = (LlmRequestEvent<String>) llmRequest2;
            @SuppressWarnings("unchecked")
            LlmRequestEvent<String> typed3 = (LlmRequestEvent<String>) llmRequest3;

            listener.onProcessEvent(typed1.responseEvent("result1", java.time.Duration.ofMillis(150)));
            listener.onProcessEvent(typed2.responseEvent("result2", java.time.Duration.ofMillis(150)));
            listener.onProcessEvent(typed3.responseEvent("result3", java.time.Duration.ofMillis(150)));

            // 3 LLM request starts (span.start()) + agent start = 4 starts
            // But we care about 3 LLM span end() calls (not agent end)
            // Agent start calls start() once, each LLM request calls start() once = 4 total
            // Each LLM response should call scope.close() and span.end()
            // Agent close + 3 LLM closes = at least 3 scope.close() calls for LLM
            // The key assertion: all 3 LLM responses should find their spans (not overwritten)
            // With the bug, only the last request's span is in the map, so only 1 end() for LLM
            verify(mockScope, atLeast(3)).close(); // 3 LLM scope closes (agent scope may not close yet)
            verify(mockSpan, atLeast(3)).end(); // 3 LLM span ends
        }
    }

    // ================================================================================
    // RANKING EVENT TESTS
    // ================================================================================

    @Nested
    @DisplayName("Ranking Event Tests")
    class RankingEventTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);
        }

        @Test
        @DisplayName("RankingChoiceMadeEvent should create span with correct name")
        @SuppressWarnings("unchecked")
        void rankingChoiceMade_shouldCreateSpan() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            RankingChoiceMadeEvent<?> event = createRankingChoiceMadeEvent("TestAgent", 0.95);
            listener.onPlatformEvent(event);

            verify(mockSpan, atLeastOnce()).name("ranking:choice_made");
            verify(mockSpan, atLeastOnce()).tag("embabel.event.type", "ranking");
            verify(mockSpan, atLeastOnce()).tag("embabel.ranking.chosen", "TestAgent");
        }

        @Test
        @DisplayName("Ranking event should NOT create span when disabled")
        @SuppressWarnings("unchecked")
        void rankingEvent_shouldNotCreateSpan_whenDisabled() {
            properties.setTraceRanking(false);

            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            RankingChoiceMadeEvent<?> event = createRankingChoiceMadeEvent("TestAgent", 0.95);
            listener.onPlatformEvent(event);

            verify(mockSpan, never()).name("ranking:choice_made");
        }
    }

    // ================================================================================
    // DYNAMIC AGENT CREATION TESTS
    // ================================================================================

    @Nested
    @DisplayName("Dynamic Agent Creation Tests")
    class DynamicAgentCreationTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);
        }

        @Test
        @DisplayName("DynamicAgentCreationEvent should create span")
        void dynamicAgentCreation_shouldCreateSpan() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            DynamicAgentCreationEvent event = createDynamicAgentCreationEvent("DynamicBot");
            listener.onPlatformEvent(event);

            verify(mockSpan, atLeastOnce()).name("dynamic_agent:DynamicBot");
            verify(mockSpan, atLeastOnce()).tag("gen_ai.operation.name", "create_agent");
            verify(mockSpan, atLeastOnce()).tag("embabel.event.type", "dynamic_agent_creation");
        }

        @Test
        @DisplayName("DynamicAgentCreationEvent should NOT create span when disabled")
        void dynamicAgentCreation_shouldNotCreateSpan_whenDisabled() {
            properties.setTraceDynamicAgentCreation(false);

            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            DynamicAgentCreationEvent event = createDynamicAgentCreationEvent("DynamicBot");
            listener.onPlatformEvent(event);

            verify(mockSpan, never()).name("dynamic_agent:DynamicBot");
        }
    }

    // ================================================================================
    // PROCESS KILLED TESTS
    // ================================================================================

    @Nested
    @DisplayName("Process Killed Tests")
    class ProcessKilledTests {

        private Span mockSpan;
        private Tracer.SpanInScope mockScope;

        @BeforeEach
        void setUp() {
            mockSpan = mock(Span.class);
            mockScope = mock(Tracer.SpanInScope.class);

            lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
            lenient().when(tracer.nextSpan(any(Span.class))).thenReturn(mockSpan);
            lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
            lenient().when(mockSpan.start()).thenReturn(mockSpan);
            lenient().when(tracer.withSpan(any())).thenReturn(mockScope);
            lenient().when(tracer.withSpan(null)).thenReturn(mockScope);
        }

        @Test
        @DisplayName("ProcessKilledEvent should close agent span with killed status")
        void processKilled_shouldCloseAgentSpan() {
            EmbabelSpringObservationEventListener listener =
                    new EmbabelSpringObservationEventListener(tracer, properties);

            AgentProcess process = createMockAgentProcess("run-1", "TestAgent");

            listener.onProcessEvent(new AgentProcessCreationEvent(process));
            listener.onProcessEvent(new ProcessKilledEvent(process));

            // Agent span should be tagged with killed status
            verify(mockSpan, atLeastOnce()).tag("embabel.agent.status", "killed");
        }
    }

    // ================================================================================
    // HELPER METHODS
    // ================================================================================

    @SuppressWarnings("unchecked")
    private static RankingChoiceMadeEvent<?> createRankingChoiceMadeEvent(String chosenName, double score) {
        AgentPlatform platform = mock(AgentPlatform.class);
        Agent chosenAgent = mock(Agent.class);
        lenient().when(chosenAgent.getName()).thenReturn(chosenName);
        lenient().when(chosenAgent.getDescription()).thenReturn("Test agent");

        Ranking ranking = mock(Ranking.class);
        lenient().when(ranking.getMatch()).thenReturn(chosenAgent);
        lenient().when(ranking.getScore()).thenReturn(score);

        Rankings rankings = mock(Rankings.class);
        lenient().when(rankings.rankings()).thenReturn(java.util.List.of(ranking));

        RankingChoiceMadeEvent event = mock(RankingChoiceMadeEvent.class);
        lenient().when(event.getAgentPlatform()).thenReturn(platform);
        lenient().when(event.getChoice()).thenReturn(ranking);
        lenient().when(event.getRankings()).thenReturn(rankings);
        lenient().when(event.getBasis()).thenReturn("test basis");
        lenient().when(event.getChoices()).thenReturn(java.util.List.of(chosenAgent));
        lenient().when(event.getType()).thenReturn((Class) Agent.class);
        return event;
    }

    private static DynamicAgentCreationEvent createDynamicAgentCreationEvent(String agentName) {
        AgentPlatform platform = mock(AgentPlatform.class);
        Agent agent = mock(Agent.class);
        lenient().when(agent.getName()).thenReturn(agentName);
        return new DynamicAgentCreationEvent(platform, agent, "test basis", java.time.Instant.now());
    }

    /**
     * Creates a real LlmRequestEvent (Kotlin final class — cannot be reliably mocked).
     */
    @SuppressWarnings("unchecked")
    private static <O> LlmRequestEvent<O> createMockLlmRequestEvent(
            AgentProcess process, String actionName, String modelName, Class<O> outputClass) {
        com.embabel.agent.core.Action action = null;
        if (actionName != null) {
            action = mock(com.embabel.agent.core.Action.class);
            lenient().when(action.getName()).thenReturn(actionName);
        }

        LlmMetadata llmMetadata = mock(LlmMetadata.class);
        lenient().when(llmMetadata.getName()).thenReturn(modelName);
        lenient().when(llmMetadata.getProvider()).thenReturn("openai");

        LlmOptions llmOptions = new LlmOptions();
        llmOptions.setTemperature(0.7);
        llmOptions.setMaxTokens(1000);
        llmOptions.setTopP(0.9);

        LlmInteraction interaction = LlmInteraction.using(llmOptions);

        return new LlmRequestEvent<>(process, action, outputClass, interaction, llmMetadata, java.util.Collections.emptyList());
    }

    private static ActionExecutionStartEvent createMockActionStartEvent(AgentProcess process, String fullName, String shortName) {
        ActionExecutionStartEvent event = mock(ActionExecutionStartEvent.class);
        when(event.getAgentProcess()).thenReturn(process);

        Action action = mock(Action.class);
        when(action.getName()).thenReturn(fullName);
        when(action.shortName()).thenReturn(shortName);
        when(action.getDescription()).thenReturn("Test action");
        lenient().doReturn(action).when(event).getAction();

        return event;
    }

    private static ActionExecutionResultEvent createMockActionResultEvent(AgentProcess process, String actionName, String status) {
        ActionExecutionResultEvent event = mock(ActionExecutionResultEvent.class);
        when(event.getAgentProcess()).thenReturn(process);
        when(event.getRunningTime()).thenReturn(java.time.Duration.ofMillis(100));

        Action action = mock(Action.class);
        when(action.getName()).thenReturn(actionName);
        lenient().doReturn(action).when(event).getAction();

        ActionStatus actionStatus = mock(ActionStatus.class);
        ActionStatusCode statusCode = mock(ActionStatusCode.class);
        when(statusCode.name()).thenReturn(status);
        when(actionStatus.getStatus()).thenReturn(statusCode);
        lenient().doReturn(actionStatus).when(event).getActionStatus();

        return event;
    }

    private static AgentProcess createMockAgentProcess(String runId, String agentName) {
        AgentProcess process = mock(AgentProcess.class);
        Agent agent = mock(Agent.class);
        Blackboard blackboard = mock(Blackboard.class);
        ProcessOptions processOptions = mock(ProcessOptions.class);
        Goal goal = mock(Goal.class);

        lenient().when(process.getId()).thenReturn(runId);
        lenient().when(process.getAgent()).thenReturn(agent);
        lenient().when(process.getBlackboard()).thenReturn(blackboard);
        lenient().when(process.getProcessOptions()).thenReturn(processOptions);
        lenient().when(process.getParentId()).thenReturn(null);
        lenient().when(process.getGoal()).thenReturn(goal);
        lenient().when(process.getFailureInfo()).thenReturn(null);

        lenient().when(agent.getName()).thenReturn(agentName);
        lenient().when(agent.getGoals()).thenReturn(Set.of(goal));
        lenient().when(goal.getName()).thenReturn("TestGoal");

        lenient().when(blackboard.getObjects()).thenReturn(Collections.emptyList());
        lenient().when(blackboard.lastResult()).thenReturn(null);
        lenient().when(processOptions.getPlannerType()).thenReturn(PlannerType.GOAP);

        return process;
    }
}
