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

import com.embabel.agent.observability.ObservabilityProperties;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EmbabelSpringObservationEventListener initialization.
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
}
