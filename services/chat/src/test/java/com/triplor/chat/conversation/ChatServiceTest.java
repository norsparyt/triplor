package com.triplor.chat.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatServiceTest {

    @Test
    void aShortMessageIsItsOwnPreview() {
        assertThat(ChatService.preview("See you at the station")).isEqualTo("See you at the station");
        assertThat(ChatService.preview("x".repeat(80))).hasSize(80).doesNotContain("…");
    }

    @Test
    void aLongMessageIsCutToEightyCharactersWithAnEllipsis() {
        String preview = ChatService.preview("x".repeat(81));

        assertThat(preview).hasSize(80).endsWith("…");
    }

    @Test
    void lineBreaksBecomeSpacesSoThePreviewFitsOneLine() {
        assertThat(ChatService.preview("Bring:\n- boots\n- a jacket")).isEqualTo("Bring: - boots - a jacket");
    }
}
