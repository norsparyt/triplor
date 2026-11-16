package com.triplor.adventure.joining;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Phase 1's way of telling chat: a direct HTTP call, made after the accept has committed.
 *
 * <p>Short timeouts, because the organizer's request is waiting on this call: better to
 * answer 502 after two seconds than to hang. There is no retry and no circuit breaker here on
 * purpose. This coupling is the thing Phase 2 measures and then removes, by replacing this
 * class with an event written in the accept transaction.
 */
public class HttpMembershipNotifier implements MembershipNotifier {

    static final String TOKEN_HEADER = "X-Internal-Token";
    static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient chat;
    private final String internalToken;

    public HttpMembershipNotifier(RestClient chat, String internalToken) {
        this.chat = chat;
        this.internalToken = internalToken;
    }

    @Override
    public void membersAdded(UUID adventureId, List<UUID> memberIds) {
        try {
            chat.put()
                    .uri("/internal/conversations/{adventureId}/members", adventureId)
                    .header(TOKEN_HEADER, internalToken)
                    .body(Map.of("memberIds", memberIds))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Covers a refused connection, a timeout and any 4xx or 5xx answer.
            throw new MembershipNotificationException("Chat did not accept the members of adventure " + adventureId, e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        MembershipNotifier httpMembershipNotifier(RestClient.Builder builder,
                @Value("${triplor.chat.base-url}") String baseUrl,
                @Value("${triplor.internal.token:}") String internalToken) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(TIMEOUT);
            requestFactory.setReadTimeout(TIMEOUT);
            return new HttpMembershipNotifier(
                    builder.baseUrl(baseUrl).requestFactory(requestFactory).build(), internalToken);
        }
    }
}
