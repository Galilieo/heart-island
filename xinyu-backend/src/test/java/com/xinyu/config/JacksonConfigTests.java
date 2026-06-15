package com.xinyu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JacksonConfigTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesLongIdsAsStringsForJavaScriptClients() throws Exception {
        UserVO user = new UserVO(2054153819121537036L, "test", "测试用户");

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).contains("\"id\":\"2054153819121537036\"");
    }
}
