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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestObservationFilterTest {

    @Nested
    class NonHttpContext {

        @Test
        void shouldIgnoreNonServerRequestContext() {
            var filter = new HttpRequestObservationFilter(4000);
            var context = new Observation.Context();

            var result = filter.map(context);

            assertThat(result).isSameAs(context);
        }
    }

    @Nested
    class RequestHeaders {

        @Test
        void shouldExtractContentTypeHeader() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            request.addHeader("Content-Type", "application/json");
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.request.headers"))
                    .contains("Content-Type: application/json");
        }

        @Test
        void shouldMaskAuthorizationHeader() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer secret-token-12345");
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            var headers = findHighCardinalityValue(context, "http.request.headers");
            assertThat(headers).contains("Authorization: ***");
            assertThat(headers).doesNotContain("secret-token-12345");
        }

        @Test
        void shouldExtractAcceptHeader() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            request.addHeader("Accept", "text/html");
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.request.headers"))
                    .contains("Accept: text/html");
        }
    }

    @Nested
    class RequestParams {

        @Test
        void shouldExtractQueryParameters() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            request.setParameter("q", "search term");
            request.setParameter("page", "1");
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            var params = findHighCardinalityValue(context, "http.request.params");
            assertThat(params).contains("q=search term");
            assertThat(params).contains("page=1");
        }

        @Test
        void shouldHandleNoParameters() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.request.params")).isNull();
        }
    }

    @Nested
    class RequestBody {

        @Test
        void shouldExtractRequestBodyFromCachingWrapper() {
            var filter = new HttpRequestObservationFilter(4000);
            var inner = new MockHttpServletRequest();
            inner.setContent("{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8));
            var wrapper = new ContentCachingRequestWrapper(inner);
            // Read the input stream to populate the cache
            try { wrapper.getInputStream().readAllBytes(); } catch (Exception ignored) {}
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(wrapper, response);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.request.body"))
                    .isEqualTo("{\"name\":\"test\"}");
        }

        @Test
        void shouldNotAddRequestBodyWhenNotCachingWrapper() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            request.setContent("{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.request.body")).isNull();
        }
    }

    @Nested
    class ResponseBody {

        @Test
        void shouldExtractResponseBodyFromCachingWrapper() throws Exception {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            var inner = new MockHttpServletResponse();
            var wrapper = new ContentCachingResponseWrapper(inner);
            wrapper.getWriter().write("{\"result\":\"ok\"}");
            wrapper.getWriter().flush();
            var context = new ServerRequestObservationContext(request, wrapper);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.response.body"))
                    .isEqualTo("{\"result\":\"ok\"}");
        }

        @Test
        void shouldNotAddResponseBodyWhenNotCachingWrapper() {
            var filter = new HttpRequestObservationFilter(4000);
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);

            filter.map(context);

            assertThat(findHighCardinalityValue(context, "http.response.body")).isNull();
        }
    }

    @Nested
    class Truncation {

        @Test
        void shouldTruncateBodyWhenExceedsMaxLength() {
            var filter = new HttpRequestObservationFilter(10);
            var inner = new MockHttpServletRequest();
            inner.setContent("a]bcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));
            var wrapper = new ContentCachingRequestWrapper(inner);
            try { wrapper.getInputStream().readAllBytes(); } catch (Exception ignored) {}
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(wrapper, response);

            filter.map(context);

            var body = findHighCardinalityValue(context, "http.request.body");
            assertThat(body).hasSize(13); // 10 + "..."
            assertThat(body).endsWith("...");
        }
    }

    private String findHighCardinalityValue(Observation.Context context, String key) {
        return context.getHighCardinalityKeyValues().stream()
                .filter(kv -> kv.getKey().equals(key))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null);
    }
}
