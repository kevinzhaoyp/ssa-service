package com.ssa.apis.wechat;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory ticket store for QR login.
 * Tickets expire after a configurable TTL (default 5 minutes).
 */
@Service
public class WechatQrService {
    public static class TicketInfo {
        public final long createdAt;
        public volatile boolean logged;
        public TicketInfo(long createdAt) { this.createdAt = createdAt; this.logged = false; }
    }

    private final Map<String, TicketInfo> store = new ConcurrentHashMap<>();
    private final Map<String, String> testSessionUser = new ConcurrentHashMap<>();
    private final long ttlMillis = 5 * 60 * 1000L; // 5 minutes

    public String createTicket() {
        String ticket = UUID.randomUUID().toString();
        store.put(ticket, new TicketInfo(Instant.now().toEpochMilli()));
        return ticket;
    }

    public boolean markLogged(String ticket) {
        if (ticket == null) return false;
        TicketInfo t = store.get(ticket);
        if (t == null) return false;
        if (isExpired(t)) {
            store.remove(ticket);
            return false;
        }
        t.logged = true;
        return true;
    }

    public boolean isLogged(String ticket) {
        if (ticket == null) return false;
        TicketInfo t = store.get(ticket);
        if (t == null) return false;
        if (isExpired(t)) { store.remove(ticket); return false; }
        return t.logged;
    }

    private boolean isExpired(TicketInfo t) {
        return Instant.now().toEpochMilli() - t.createdAt > ttlMillis;
    }

    // Test session helpers
    public void createTestSession(String ticket, String username) {
        if (ticket != null && username != null) {
            testSessionUser.put(ticket, username);
            markLogged(ticket);
        }
    }

    public String getTestUserByTicket(String ticket) {
        return testSessionUser.get(ticket);
    }
}
