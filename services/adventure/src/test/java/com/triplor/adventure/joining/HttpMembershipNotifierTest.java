package com.triplor.adventure.joining;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;

import com.triplor.adventure.joining.MembershipNotifier.MembershipNotificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The notifier against a fake chat server: what it sends, and how it reports each kind of failure. */
class HttpMembershipNotifierTest {

    final UUID adventureId = UUID.randomUUID();
    final UUID organizer = UUID.randomUUID();
    final UUID member = UUID.randomUUID();

    MockRestServiceServer chat;
    HttpMembershipNotifier notifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://chat.test");
        chat = MockRestServiceServer.bindTo(builder).build();
        notifier = new HttpMembershipNotifier(builder.build(), "the-shared-secret");
    }

    @Test
    void sendsTheMembersWithTheSharedSecret() {
        chat.expect(requestTo("http://chat.test/internal/conversations/" + adventureId + "/members"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Token", "the-shared-secret"))
                .andExpect(content().json("{\"memberIds\": [\"%s\", \"%s\"]}".formatted(organizer, member)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> notifier.membersAdded(adventureId, List.of(organizer, member))).doesNotThrowAnyException();
        chat.verify();
    }

    @Test
    void aServerErrorFromChatIsReportedAsANotificationFailure() {
        chat.expect(method(HttpMethod.PUT)).andRespond(withServerError());

        assertThatThrownBy(() -> notifier.membersAdded(adventureId, List.of(organizer, member)))
                .isInstanceOf(MembershipNotificationException.class);
    }

    @Test
    void aRefusalFromChatIsReportedAsANotificationFailure() {
        // What chat answers when the secret is wrong or not configured.
        chat.expect(method(HttpMethod.PUT)).andRespond(withForbiddenRequest());

        assertThatThrownBy(() -> notifier.membersAdded(adventureId, List.of(organizer, member)))
                .isInstanceOf(MembershipNotificationException.class);
    }

    @Test
    void chatNotRunningIsReportedAsANotificationFailure() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpMembershipNotifier unreachable = new HttpMembershipNotifier(
                RestClient.builder().baseUrl("http://localhost:" + closedPort).build(), "the-shared-secret");

        assertThatThrownBy(() -> unreachable.membersAdded(adventureId, List.of(organizer, member)))
                .isInstanceOf(MembershipNotificationException.class);
    }
}
