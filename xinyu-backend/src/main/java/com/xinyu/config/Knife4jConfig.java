package com.xinyu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    private static final String TOKEN_SECURITY_SCHEME = "token";

    @Bean
    public OpenAPI xinyuOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("心屿 API 文档")
                        .description("心屿 AI 情绪记录与匿名互助社区系统接口文档")
                        .version("1.0.0")
                        .contact(new Contact().name("heart-island")))
                .components(new Components()
                        .addSecuritySchemes(TOKEN_SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("token")
                                .description("登录后返回的 JWT token")))
                .addSecurityItem(new SecurityRequirement().addList(TOKEN_SECURITY_SCHEME));
    }
}
