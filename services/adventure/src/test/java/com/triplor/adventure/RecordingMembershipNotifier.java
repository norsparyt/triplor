package com.triplor.adventure;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.triplor.adventure.joining.MembershipNotifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The chat service's stand-in for tests: remembers what it was told, and can be made to fail
 * for one adventure to play "chat is down".
 */
public class RecordingMembershipNotifier implements MembershipNotifier {

    public record Call(UUID adventureId, List<UUID> memberIds) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Set<UUID> unreachableFor = ConcurrentHashMap.newKeySet();

    @Override
    public void membersAdded(UUID adventureId, List<UUID> memberIds) {
        if (unreachableFor.contains(adventureId)) {
            throw new MembershipNotificationException("chat is down (test)", null);
        }
        calls.add(new Call(adventureId, List.copyOf(memberIds)));
    }

    public List<Call> callsFor(UUID adventureId) {
        return calls.stream().filter(call -> call.adventureId().equals(adventureId)).toList();
    }

    public void failFor(UUID adventureId) {
        unreachableFor.add(adventureId);
    }

    public void recoverFor(UUID adventureId) {
        unreachableFor.remove(adventureId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        RecordingMembershipNotifier recordingMembershipNotifier() {
            return new RecordingMembershipNotifier();
        }
    }
}
