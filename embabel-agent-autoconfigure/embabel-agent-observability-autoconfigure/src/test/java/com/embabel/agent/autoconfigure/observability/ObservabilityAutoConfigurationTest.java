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
package com.embabel.agent.autoconfigure.observability;

import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.observability.ObservabilityProperties;
import com.embabel.agent.observability.observation.ChatModelObservationFilter;
import com.embabel.agent.observability.observation.EmbabelObservationEventListener;
import com.embabel.agent.observability.observation.EmbabelSpringObservationEventListener;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for ObservabilityAutoConfiguration.
 *
 * @since 0.3.4
 */
class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void eventListener_shouldBeCreated_byDefault() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(EmbabelObservationEventListener.class);
                });
    }

    @Test
    void eventListener_shouldNotBeCreated_whenDisabled() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues("embabel.observability.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EmbabelObservationEventListener.class);
                });
    }

    @Test
    void eventListener_shouldNotBeCreated_whenTraceAgentEventsDisabled() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues("embabel.observability.trace-agent-events=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EmbabelObservationEventListener.class);
                });
    }

    @Test
    void chatModelFilter_shouldBeCreated_byDefault() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModelObservationFilter.class);
                });
    }

    @Test
    void chatModelFilter_shouldNotBeCreated_whenDisabled() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues("embabel.observability.trace-llm-calls=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatModelObservationFilter.class);
                });
    }

    @Test
    void propertiesBean_shouldBeCreated() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObservabilityProperties.class);
                });
    }

    @Test
    void properties_shouldApplyCustomValues() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues(
                        "embabel.observability.service-name=my-app",
                        "embabel.observability.max-attribute-length=2000"
                )
                .run(context -> {
                    ObservabilityProperties props = context.getBean(ObservabilityProperties.class);
                    assertThat(props.getServiceName()).isEqualTo("my-app");
                    assertThat(props.getMaxAttributeLength()).isEqualTo(2000);
                });
    }

    @Test
    void eventListener_shouldUseMicrometerTracing_whenPropertySetAndTracerAvailable() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class, TracerConfig.class)
                .withPropertyValues("embabel.observability.implementation=MICROMETER_TRACING")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgenticEventListener.class);
                    assertThat(context.getBean(AgenticEventListener.class))
                            .isInstanceOf(EmbabelSpringObservationEventListener.class);
                });
    }

    @Test
    void eventListener_shouldUseOpenTelemetryDirect_whenPropertySet() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class, TracerConfig.class)
                .withPropertyValues("embabel.observability.implementation=OPENTELEMETRY_DIRECT")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgenticEventListener.class);
                    assertThat(context.getBean(AgenticEventListener.class))
                            .isInstanceOf(EmbabelObservationEventListener.class);
                });
    }

    @Test
    void eventListener_shouldFallbackToOpenTelemetry_whenNoTracer() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues("embabel.observability.implementation=MICROMETER_TRACING")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgenticEventListener.class);
                    assertThat(context.getBean(AgenticEventListener.class))
                            .isInstanceOf(EmbabelObservationEventListener.class);
                });
    }

    @Test
    void httpBodyCachingFilter_shouldNotBeCreated_byDefault() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HttpBodyCachingFilter.class);
                });
    }

    @Test
    void httpBodyCachingFilter_shouldBeCreated_whenTraceHttpDetailsEnabled() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues("embabel.observability.trace-http-details=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpBodyCachingFilter.class);
                });
    }

    @Test
    void httpRequestObservationFilter_shouldNotBeCreated_byDefault() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HttpRequestObservationFilter.class);
                });
    }

    @Test
    void httpRequestObservationFilter_shouldBeCreated_whenTraceHttpDetailsEnabled() {
        contextRunner
                .withUserConfiguration(OpenTelemetryConfig.class)
                .withPropertyValues("embabel.observability.trace-http-details=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpRequestObservationFilter.class);
                });
    }

    @Configuration
    static class OpenTelemetryConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return mock(OpenTelemetry.class);
        }
    }

    @Configuration
    static class TracerConfig {
        @Bean
        Tracer tracer() {
            return mock(Tracer.class);
        }
    }
}
