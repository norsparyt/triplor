package com.triplor.common.web;

import com.triplor.common.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * What every service's OpenAPI description has in common: its title, and the fact that calls
 * carry a bearer token. Applies only to services that have springdoc on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({OpenAPI.class, SpringDocUtils.class})
public class TriplorOpenApiAutoConfiguration {

    static {
        // CurrentUser is filled in from the token. It is not a parameter a client sends.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    @ConditionalOnMissingBean
    OpenAPI triplorOpenApi(@Value("${spring.application.name:service}") String name) {
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("Triplor " + name + " API").version("v1"))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
