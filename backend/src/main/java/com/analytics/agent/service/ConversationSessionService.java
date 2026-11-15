package com.analytics.agent.service;

import com.analytics.agent.dto.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public void validateDatasourceForSession(String sessionId, String requestedDatasourceId) {
        if (sessionId == null || requestedDatasourceId == null || requestedDatasourceId.isBlank()) {
            return;
        }
        ConversationSession session = sessions.get(sessionId.trim());
        if (session != null && session.getDatasourceId() != null) {
            if (!session.getDatasourceId().equalsIgnoreCase(requestedDatasourceId.trim())) {
                throw new IllegalArgumentException(
                        "Session '" + sessionId + "' is bound to datasource '" + session.getDatasourceId()
                                + "'. Switching to datasource '" + requestedDatasourceId
                                + "' within the same session is not allowed. Please start a new session."
                );
            }
        }
    }

    public ConversationSession getOrCreateSession(String sessionId, String datasourceId) {
        cleanupExpiredSessions();
        return sessions.compute(sessionId.trim(), (id, existing) -> {
            if (existing == null) {
                log.debug("Creating new conversation session '{}' with datasource '{}'", id, datasourceId);
                return new ConversationSession(id, datasourceId != null ? datasourceId.trim() : null);
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
            return existing;
        });
    }

    public void recordTurn(String sessionId, String question, QueryIntent intent, String sql, String summary, String datasourceId) {
        ConversationSession session = sessions.get(sessionId.trim());
        if (session != null) {
            String ds = datasourceId != null ? datasourceId.trim() : session.getDatasourceId();
            session.addTurn(new SessionTurn(question, intent, sql, summary, ds, Instant.now()));
            session.touch();
        }
    }

    public void recordTurn(String sessionId, String question, QueryIntent intent, String sql, String summary) {
        ConversationSession session = sessions.get(sessionId != null ? sessionId.trim() : "");
        String ds = session != null ? session.getDatasourceId() : null;
        recordTurn(sessionId, question, intent, sql, summary, ds);
    }

    public List<SessionTurn> getHistory(String sessionId) {
        ConversationSession session = sessions.get(sessionId != null ? sessionId.trim() : "");
        if (session == null) {
            return Collections.emptyList();
        }
        return session.getTurns();
    }

    public String formatHistoryForPrompt(String sessionId, String datasourceId) {
        List<SessionTurn> turns = getHistory(sessionId);
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
        return formatHistoryForPrompt(sessionId, ds);
    }

    private void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.entrySet().removeIf(entry -> entry.getValue().getLastActivity().isBefore(cutoff));
    }

    public static class ConversationSession {
        private final String sessionId;
        private String datasourceId;
        private final List<SessionTurn> turns = Collections.synchronizedList(new ArrayList<>());
        private volatile Instant lastActivity;

        public ConversationSession(String sessionId, String datasourceId) {
            this.sessionId = sessionId;
            this.datasourceId = datasourceId;
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
