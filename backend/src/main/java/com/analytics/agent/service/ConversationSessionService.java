package com.analytics.agent.service;

import com.analytics.agent.dto.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationSessionService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSessionService.class);
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    public String resolveSessionId(String providedSessionId) {
        if (providedSessionId != null && !providedSessionId.isBlank()) {
            return providedSessionId.trim();
        }
        return UUID.randomUUID().toString();
    }

    public ConversationSession getSession(String sessionId) {
        if (sessionId == null) return null;
        cleanupExpiredSessions();
        return sessions.get(sessionId.trim());
    }

    public ConversationSession getSession(String sessionId, String currentUserId) {
        ConversationSession session = getSession(sessionId);
        if (session != null && session.getOwnerUserId() != null) {
            if (currentUserId == null || !session.getOwnerUserId().equals(currentUserId)) {
                throw new AccessDeniedException("Access denied: Session '" + sessionId + "' belongs to another user.");
            }
        }
        return session;
    }

    public void validateDatasourceForSession(String sessionId, String requestedDatasourceId) {
        validateDatasourceForSession(sessionId, requestedDatasourceId, null);
    }

    public void validateDatasourceForSession(String sessionId, String requestedDatasourceId, String currentUserId) {
        if (sessionId == null || requestedDatasourceId == null || requestedDatasourceId.isBlank()) {
            return;
        }
        ConversationSession session = sessions.get(sessionId.trim());
        if (session != null) {
            if (session.getOwnerUserId() != null && (currentUserId == null || !session.getOwnerUserId().equals(currentUserId))) {
                throw new AccessDeniedException("Access denied: Session '" + sessionId + "' belongs to another user.");
            }
            if (session.getDatasourceId() != null) {
                if (!session.getDatasourceId().equalsIgnoreCase(requestedDatasourceId.trim())) {
                    throw new IllegalArgumentException(
                            "Session '" + sessionId + "' is bound to datasource '" + session.getDatasourceId()
                                    + "'. Switching to datasource '" + requestedDatasourceId
                                    + "' within the same session is not allowed. Please start a new session."
                    );
                }
            }
        }
    }

    public ConversationSession getOrCreateSession(String sessionId, String datasourceId) {
        return getOrCreateSession(sessionId, datasourceId, null);
    }

    public ConversationSession getOrCreateSession(String sessionId, String datasourceId, String currentUserId) {
        cleanupExpiredSessions();
        return sessions.compute(sessionId.trim(), (id, existing) -> {
            if (existing == null) {
                log.debug("Creating new conversation session '{}' with datasource '{}' for user '{}'", id, datasourceId, currentUserId);
                return new ConversationSession(id, datasourceId != null ? datasourceId.trim() : null, currentUserId);
            }
            if (existing.getOwnerUserId() != null && (currentUserId == null || !existing.getOwnerUserId().equals(currentUserId))) {
                throw new AccessDeniedException("Access denied: Session '" + id + "' belongs to another user.");
            }
            existing.touch();
            if (existing.getDatasourceId() != null && datasourceId != null && !datasourceId.isBlank()) {
                if (!existing.getDatasourceId().equalsIgnoreCase(datasourceId.trim())) {
                    throw new IllegalArgumentException(
                            "Session '" + id + "' is bound to datasource '" + existing.getDatasourceId()
                                    + "'. Switching to datasource '" + datasourceId
                                    + "' within the same session is not allowed. Please start a new session."
                    );
                }
            } else if (existing.getDatasourceId() == null && datasourceId != null && !datasourceId.isBlank()) {
                existing.setDatasourceId(datasourceId.trim());
            }
            if (existing.getOwnerUserId() == null && currentUserId != null) {
                existing.setOwnerUserId(currentUserId);
            }
            return existing;
        });
    }

    public void recordTurn(String sessionId, String question, QueryIntent intent, String sql, String summary, String datasourceId, String currentUserId) {
        ConversationSession session = sessions.get(sessionId.trim());
        if (session != null) {
            if (session.getOwnerUserId() != null && (currentUserId == null || !session.getOwnerUserId().equals(currentUserId))) {
                throw new AccessDeniedException("Access denied: Session '" + sessionId + "' belongs to another user.");
            }
            String ds = datasourceId != null ? datasourceId.trim() : session.getDatasourceId();
            session.addTurn(new SessionTurn(question, intent, sql, summary, ds, Instant.now()));
            session.touch();
        }
    }

    public void recordTurn(String sessionId, String question, QueryIntent intent, String sql, String summary, String datasourceId) {
        recordTurn(sessionId, question, intent, sql, summary, datasourceId, null);
    }

    public void recordTurn(String sessionId, String question, QueryIntent intent, String sql, String summary) {
        ConversationSession session = sessions.get(sessionId != null ? sessionId.trim() : "");
        String ds = session != null ? session.getDatasourceId() : null;
        recordTurn(sessionId, question, intent, sql, summary, ds, null);
    }

    public List<SessionTurn> getHistory(String sessionId) {
        return getHistory(sessionId, null);
    }

    public List<SessionTurn> getHistory(String sessionId, String currentUserId) {
        ConversationSession session = sessions.get(sessionId != null ? sessionId.trim() : "");
        if (session == null) {
            return Collections.emptyList();
        }
        if (session.getOwnerUserId() != null && (currentUserId == null || !session.getOwnerUserId().equals(currentUserId))) {
            throw new AccessDeniedException("Access denied: Session '" + sessionId + "' belongs to another user.");
        }
        return session.getTurns();
    }

    public String formatHistoryForPrompt(String sessionId, String datasourceId) {
        return formatHistoryForPrompt(sessionId, datasourceId, null);
    }

    public String formatHistoryForPrompt(String sessionId, String datasourceId, String currentUserId) {
        List<SessionTurn> turns = getHistory(sessionId, currentUserId);
        if (turns.isEmpty()) {
            return "";
        }
        List<SessionTurn> matchingTurns = turns.stream()
                .filter(t -> datasourceId == null || t.datasourceId() == null || t.datasourceId().equalsIgnoreCase(datasourceId))
                .toList();
        if (matchingTurns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Previous Conversation Context:\n");
        int start = Math.max(0, matchingTurns.size() - 3);
        for (int i = start; i < matchingTurns.size(); i++) {
            SessionTurn t = matchingTurns.get(i);
            sb.append("User: ").append(t.question()).append("\n");
            if (t.sql() != null) {
                sb.append("Assistant SQL: ").append(t.sql()).append("\n");
            }
            if (t.summary() != null) {
                sb.append("Assistant Summary: ").append(t.summary()).append("\n");
            }
        }
        return sb.toString();
    }

    public String formatHistoryForPrompt(String sessionId) {
        ConversationSession session = sessions.get(sessionId != null ? sessionId.trim() : "");
        String ds = session != null ? session.getDatasourceId() : null;
        return formatHistoryForPrompt(sessionId, ds, null);
    }

    private void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.entrySet().removeIf(entry -> entry.getValue().getLastActivity().isBefore(cutoff));
    }

    public static class ConversationSession {
        private final String sessionId;
        private String datasourceId;
        private String ownerUserId;
        private final List<SessionTurn> turns = Collections.synchronizedList(new ArrayList<>());
        private volatile Instant lastActivity;

        public ConversationSession(String sessionId, String datasourceId) {
            this(sessionId, datasourceId, null);
        }

        public ConversationSession(String sessionId, String datasourceId, String ownerUserId) {
            this.sessionId = sessionId;
            this.datasourceId = datasourceId;
            this.ownerUserId = ownerUserId;
            this.lastActivity = Instant.now();
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getDatasourceId() {
            return datasourceId;
        }

        public void setDatasourceId(String datasourceId) {
            this.datasourceId = datasourceId;
        }

        public String getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(String ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public List<SessionTurn> getTurns() {
            synchronized (turns) {
                return new ArrayList<>(turns);
            }
        }

        public void addTurn(SessionTurn turn) {
            turns.add(turn);
        }

        public Instant getLastActivity() {
            return lastActivity;
        }

        public void touch() {
            this.lastActivity = Instant.now();
        }
    }

    public record SessionTurn(
            String question,
            QueryIntent intent,
            String sql,
            String summary,
            String datasourceId,
            Instant timestamp
    ) {}
}
