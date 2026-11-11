package com.xinyu.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xinyu.service.AiReplyService;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** 保留真实请求构造、响应解析与错误分类，只替换供应商HTTP传输。 */
class CommunityAiServiceTests {
    private static final String UPSTREAM = "http://127.0.0.1:1/synthetic/chat/completions";
    private static final String PRIVATE_TEXT = "synthetic-private-post";
    private static final String PRIVATE_TOKEN = "synthetic-private-token";

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"choices\":[]}", "{\"choices\":{}}",
            "{\"choices\":[null]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":123}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"   \"}}]}",
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"不完整内容\"}}]}",
            "not-json"
    })
    void invalidProviderResponseNeverBecomesSuccessfulFallback(String body) {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(http).build();
        upstream.expect(requestTo(UPSTREAM)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var result = service(http).generateReplyForPost("合成话题", "平静", PRIVATE_TEXT);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getReplyContent()).isNull();
        assertThat(result.getPrompt()).isNull();
        assertThat(result.getErrorCode()).isEqualTo("invalid_model_response");
        upstream.verify();
    }

    @Test
    void successfulReplyIsTrimmedAndDoesNotRetainPrompt() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(http).build();
        upstream.expect(requestTo(UPSTREAM)).andRespond(withSuccess(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"  合成社区回复。  \"}}]}",
                MediaType.APPLICATION_JSON));
        var result = service(http).generateReplyForPost("合成话题", "平静", PRIVATE_TEXT);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getReplyContent()).isEqualTo("合成社区回复。");
        assertThat(result.getPrompt()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void upstreamFailureLogsOnlyCategoryWithoutBodyTokenOrPost() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(http).build();
        upstream.expect(requestTo(UPSTREAM)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .body(PRIVATE_TEXT + PRIVATE_TOKEN).contentType(MediaType.TEXT_PLAIN));
        Logger logger = (Logger) LoggerFactory.getLogger(AiReplyService.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            var result = service(http).generateReplyForPost("合成话题", "平静", PRIVATE_TEXT);
            assertThat(result.getSuccess()).isFalse();
            assertThat(result.getReplyContent()).isNull();
            assertThat(result.getErrorMessage()).contains("502", PRIVATE_TEXT).doesNotContain(PRIVATE_TOKEN);
            assertThat(captured.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("provider_error")
                        .doesNotContain(PRIVATE_TEXT, PRIVATE_TOKEN);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void connectionPoolWaitTimeoutIsClassifiedAsTimeout() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(http).build();
        upstream.expect(requestTo(UPSTREAM)).andRespond(withException(
                new ConnectionRequestTimeoutException("synthetic-pool-wait")));
        var result = service(http).generateReplyForPost("合成话题", "平静", PRIVATE_TEXT);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getReplyContent()).isNull();
        assertThat(result.getErrorCode()).isEqualTo("provider_timeout");
        upstream.verify();
    }

    static AiReplyService service(RestTemplate http) {
        AiReplyService service = new AiReplyService();
        ReflectionTestUtils.setField(service, "restTemplate", http);
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:1/synthetic");
        ReflectionTestUtils.setField(service, "apiKey", PRIVATE_TOKEN);
        ReflectionTestUtils.setField(service, "model", "synthetic-model");
        return service;
    }
}
