package com.analytics.security;

import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.service.ConversationSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSessionSecurityTest {

    private ConversationSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new ConversationSessionService();
    }

    @Test
    void userOwnedSession_AllowedForOwner() {
        String sessionId = "user-a-session";
        String userA = "user-101";

        ConversationSessionService.ConversationSession session =
                sessionService.getOrCreateSession(sessionId, "ecommerce", userA);

        assertNotNull(session);
        assertEquals(userA, session.getOwnerUserId());
        assertEquals("ecommerce", session.getDatasourceId());

        assertDoesNotThrow(() -> sessionService.validateDatasourceForSession(sessionId, "ecommerce", userA));

        ConversationSessionService.ConversationSession retrieved =
                sessionService.getSession(sessionId, userA);
        assertNotNull(retrieved);
        assertEquals(sessionId, retrieved.getSessionId());

        QueryIntent intent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
        assertDoesNotThrow(() -> sessionService.recordTurn(
                sessionId, "Show revenue", intent, "SELECT 1;", "Revenue summary", "ecommerce", userA
        ));

        String prompt = sessionService.formatHistoryForPrompt(sessionId, "ecommerce", userA);
        assertTrue(prompt.contains("Show revenue"));
    }

    @Test
    void userOwnedSession_RejectsDifferentUserWithAccessDeniedException() {
        String sessionId = "user-a-session";
        String userA = "user-101";
        String userB = "user-202";

        sessionService.getOrCreateSession(sessionId, "ecommerce", userA);

        AccessDeniedException ex1 = assertThrows(AccessDeniedException.class,
                () -> sessionService.getSession(sessionId, userB));
        assertTrue(ex1.getMessage().contains("belongs to another user"));

        AccessDeniedException ex2 = assertThrows(AccessDeniedException.class,
                () -> sessionService.validateDatasourceForSession(sessionId, "ecommerce", userB));
        assertTrue(ex2.getMessage().contains("belongs to another user"));

        AccessDeniedException ex3 = assertThrows(AccessDeniedException.class,
                () -> sessionService.getOrCreateSession(sessionId, "ecommerce", userB));
        assertTrue(ex3.getMessage().contains("belongs to another user"));

        QueryIntent intent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
        AccessDeniedException ex4 = assertThrows(AccessDeniedException.class,
                () -> sessionService.recordTurn(sessionId, "Tampered query", intent, "SELECT 1;", "Bad summary", "ecommerce", userB));
        assertTrue(ex4.getMessage().contains("belongs to another user"));

        AccessDeniedException ex5 = assertThrows(AccessDeniedException.class,
                () -> sessionService.formatHistoryForPrompt(sessionId, "ecommerce", userB));
        assertTrue(ex5.getMessage().contains("belongs to another user"));
    }

    @Test
    void userOwnedSession_RejectsUnauthenticatedAccess() {
        String sessionId = "user-a-session";
        String userA = "user-101";

        sessionService.getOrCreateSession(sessionId, "ecommerce", userA);

        QueryIntent intent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> sessionService.recordTurn(sessionId, "Tampered query", intent, "SELECT 1;", "Bad summary", "ecommerce", (String) null));
        assertTrue(ex.getMessage().contains("belongs to another user"));
    }
}
