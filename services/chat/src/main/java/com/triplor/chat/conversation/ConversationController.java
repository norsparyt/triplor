package com.triplor.chat.conversation;

import java.net.URI;
import java.util.List;

import com.triplor.chat.conversation.ChatDtos.ConversationResponse;
import com.triplor.chat.conversation.ChatDtos.MarkReadRequest;
import com.triplor.chat.conversation.ChatDtos.MessagePage;
import com.triplor.chat.conversation.ChatDtos.MessageResponse;
import com.triplor.chat.conversation.ChatDtos.SendMessageRequest;
import com.triplor.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ChatService chat;

    public ConversationController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping
    List<ConversationResponse> list(CurrentUser user, @RequestParam(name = "limit", required = false) Integer limit) {
        return chat.conversationsOf(user.id().toString(), limit);
    }

    /** History, newest first. {@code before} is a message id: only older messages are returned. */
    @GetMapping("/{id}/messages")
    MessagePage messages(CurrentUser user, @PathVariable String id,
            @RequestParam(name = "before", required = false) String before,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return chat.messages(id, user.id().toString(), before, limit);
    }

    /** 201 for a new message; 200 with the stored message when the {@code clientMessageId} was already used. */
    @PostMapping("/{id}/messages")
    ResponseEntity<MessageResponse> send(CurrentUser user, @PathVariable String id,
            @Valid @RequestBody SendMessageRequest request) {
        ChatService.Sent sent = chat.send(id, user.id().toString(), request.clientMessageId(), request.body());
        if (sent.created()) {
            return ResponseEntity.created(URI.create("/conversations/" + id + "/messages/" + sent.message().id()))
                    .body(sent.message());
        }
        return ResponseEntity.ok(sent.message());
    }

    @PutMapping("/{id}/read")
    ResponseEntity<Void> markRead(CurrentUser user, @PathVariable String id, @Valid @RequestBody MarkReadRequest request) {
        chat.markRead(id, user.id().toString(), request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }
}
