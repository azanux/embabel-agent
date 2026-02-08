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
import com.embabel.agent.observability.mdc.MdcPropagationEventListener;
import com.embabel.agent.observability.observation.ChatModelObservationFilter;
import com.embabel.agent.observability.observation.EmbabelFullObservationEventListener;
import com.embabel.agent.observability.metrics.EmbabelMetricsEventListener;
import com.embabel.agent.observability.observation.EmbabelObservationEventListener;
import com.embabel.agent.observability.observation.EmbabelSpringObservationEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Embabel Agent observability.
 *
 * <p>Configures tracing infrastructure with fallback:
 * SPRING_OBSERVATION -> MICROMETER_TRACING -> OPENTELEMETRY_DIRECT.
 *
 * @see ObservabilityProperties
 * @since 0.3.4
 */
@AutoConfiguration(
        after = MicrometerTracingAutoConfiguration.class,
        afterName = {
                "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration"
        }
)
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "embabel.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    /**
     * Creates the event listener based on configured implementation with automatic fallback.
     *
     * @param openTelemetryProvider the OpenTelemetry provider
     * @param tracerProvider the Micrometer Tracer provider
     * @param observationRegistryProvider the ObservationRegistry provider
     * @param properties the observability properties
     * @return the configured event listener
     */
    @Bean
    @ConditionalOnProperty(prefix = "embabel.observability", name = "trace-agent-events", havingValue = "true", matchIfMissing = true)
    public AgenticEventListener embabelObservationEventListener(
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ObjectProvider<Tracer> tracerProvider,
            ObjectProvider<ObservationRegistry> observationRegistryProvider,
            ObservabilityProperties properties) {

        if (properties.getImplementation() == ObservabilityProperties.Implementation.SPRING_OBSERVATION) {
            ObservationRegistry observationRegistry = observationRegistryProvider.getIfAvailable();
            if (observationRegistry != null) {
                log.info("Configuring Embabel Agent observability with Spring Observation API (traces + metrics)");
                return new EmbabelFullObservationEventListener(observationRegistry, properties);
            } else {
                log.warn("ObservationRegistry not found, falling back to Micrometer Tracing implementation");
            }
        }

        if (properties.getImplementation() == ObservabilityProperties.Implementation.MICROMETER_TRACING
                || properties.getImplementation() == ObservabilityProperties.Implementation.SPRING_OBSERVATION) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                log.info("Configuring Embabel Agent observability with Micrometer Tracing (integrated with Spring AI)");
                return new EmbabelSpringObservationEventListener(tracer, properties);
            } else {
                log.warn("Micrometer Tracer not found, falling back to OpenTelemetry direct implementation");
            }
        }

        log.info("Configuring Embabel Agent observability with OpenTelemetry direct");
        return new EmbabelObservationEventListener(openTelemetryProvider, properties);
    }

    /**
     * Creates a servlet filter that wraps request/response for body caching.
     *
     * @return the body caching filter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.util.ContentCachingRequestWrapper")
    @ConditionalOnProperty(prefix = "embabel.observability", name = "trace-http-details", havingValue = "true")
    public HttpBodyCachingFilter httpBodyCachingFilter() {
        log.debug("Configuring HTTP body caching filter for request/response tracing");
        return new HttpBodyCachingFilter();
    }

    /**
     * Creates observation filter to enrich HTTP server observations with request/response details.
     *
     * @param properties the observability properties
     * @return the HTTP request observation filter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.http.server.observation.ServerRequestObservationContext")
    @ConditionalOnProperty(prefix = "embabel.observability", name = "trace-http-details", havingValue = "true")
    public HttpRequestObservationFilter httpRequestObservationFilter(ObservabilityProperties properties) {
        log.debug("Configuring HTTP request observation filter for request/response tracing");
        return new HttpRequestObservationFilter(properties.getMaxAttributeLength());
    }

    /**
     * Creates filter to enrich Spring AI LLM observations with prompt/completion.
     *
     * @param properties the observability properties
     * @return the configured observation filter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.ai.chat.observation.ChatModelObservationContext")
    @ConditionalOnProperty(prefix = "embabel.observability", name = "trace-llm-calls", havingValue = "true", matchIfMissing = true)
    public ChatModelObservationFilter chatModelObservationFilter(ObservabilityProperties properties) {
        log.debug("Configuring ChatModel observation filter for LLM call tracing");
        return new ChatModelObservationFilter(properties.getMaxAttributeLength());
    }

    /**
     * Creates the MDC propagation listener for log correlation.
     *
     * @param properties the observability properties
     * @return the MDC propagation event listener
     */
    @Bean
    @ConditionalOnProperty(prefix = "embabel.observability", name = "mdc-propagation", havingValue = "true", matchIfMissing = true)
    public MdcPropagationEventListener mdcPropagationEventListener(ObservabilityProperties properties) {
        log.info("Configuring Embabel Agent MDC propagation for log correlation");
        return new MdcPropagationEventListener(properties);
    }

    /**
     * Creates the Micrometer business metrics listener.
     *
     * @param meterRegistry the meter registry
     * @param properties the observability properties
     * @return the metrics event listener
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "embabel.observability", name = "metrics-enabled",
            havingValue = "true", matchIfMissing = true)
    public EmbabelMetricsEventListener embabelMetricsEventListener(
            MeterRegistry meterRegistry, ObservabilityProperties properties) {
        log.info("Configuring Embabel Agent Micrometer metrics listener");
        return new EmbabelMetricsEventListener(meterRegistry, properties);
    }

}
