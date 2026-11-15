package com.triplor.common.web;

import java.time.Clock;
import java.util.List;

import com.triplor.common.error.ApiExceptionHandler;
import com.triplor.common.security.CurrentUserArgumentResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web-layer defaults shared by every service: the error format, the clock and {@code CurrentUser}. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TriplorWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }

    /**
     * The one source of "now". Code asks this bean for the time instead of calling
     * {@code Instant.now()}, so tests can replace it with a fixed clock.
     */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WebMvcConfigurer currentUserArgumentResolverConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
