package com.analytics.agent;

import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.service.ConversationSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSessionServiceTest {

    private ConversationSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new ConversationSessionService();
    }

    @Test
    void resolvesNewSessionIdWhenNull() {
        String id = sessionService.resolveSessionId(null);
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    void preservesProvidedSessionId() {
        String id = sessionService.resolveSessionId("test-session-123");
        assertEquals("test-session-123", id);
    }

    @Test
    void isolatesConversationsBetweenDistinctSessions() {
        ConversationSessionService.ConversationSession sessionA = sessionService.getOrCreateSession("session-A", "ecommerce");
        ConversationSessionService.ConversationSession sessionB = sessionService.getOrCreateSession("session-B", "crm");

        assertEquals("ecommerce", sessionA.getDatasourceId());
        assertEquals("crm", sessionB.getDatasourceId());

        sessionService.recordTurn(
                "session-A",
                "Show monthly revenue",
                new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null),
                "SELECT DATE_FORMAT(order_date, '%Y-%m') AS month, SUM(total_amount) AS revenue FROM orders GROUP BY month;",
                "Monthly revenue summary",
                "ecommerce"
        );

        assertEquals(1, sessionService.getHistory("session-A").size());
        assertEquals(0, sessionService.getHistory("session-B").size());

        String promptContextA = sessionService.formatHistoryForPrompt("session-A");
        assertTrue(promptContextA.contains("Show monthly revenue"));

        String promptContextB = sessionService.formatHistoryForPrompt("session-B");
        assertTrue(promptContextB.isEmpty());
    }

    @Test
    void ecommerceSessionAcceptsMatchingFollowUp() {
        // 1. Create an E-Commerce session
        ConversationSessionService.ConversationSession session = sessionService.getOrCreateSession("ecom-session-1", "ecommerce");
        assertNotNull(session);
        assertEquals("ecommerce", session.getDatasourceId());

        sessionService.recordTurn(
                "ecom-session-1",
                "What was our monthly revenue trend?",
                new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null),
                "SELECT DATE_FORMAT(order_date, '%Y-%m') AS month, SUM(total_amount) AS revenue FROM orders GROUP BY month;",
                "Revenue stayed near $10k",
                "ecommerce"
        );

        // 2. Send an E-Commerce follow-up
        assertDoesNotThrow(() -> sessionService.validateDatasourceForSession("ecom-session-1", "ecommerce"));
        ConversationSessionService.ConversationSession followUp = sessionService.getOrCreateSession("ecom-session-1", "ecommerce");
        assertEquals("ecommerce", followUp.getDatasourceId());

        sessionService.recordTurn(
                "ecom-session-1",
                "Show only delivered orders",
                new QueryIntent("ecommerce", "count", List.of("status"), List.of(), null, "COUNT", "BREAKDOWN", false, null),
                "SELECT COUNT(*) FROM orders WHERE order_status = 'delivered';",
                "Delivered count is 405",
                "ecommerce"
        );

        assertEquals(2, sessionService.getHistory("ecom-session-1").size());
        String history = sessionService.formatHistoryForPrompt("ecom-session-1", "ecommerce");
        assertTrue(history.contains("What was our monthly revenue trend?"));
        assertTrue(history.contains("Show only delivered orders"));
    }

    @Test
    void rejectsSwitchingDatasourceWithinSameSession() {
        // 1. Create an E-Commerce session
        sessionService.getOrCreateSession("session-locked", "ecommerce");

        // 3. Attempt a CRM query using the same session ID
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sessionService.validateDatasourceForSession("session-locked", "crm"));
        // 4. Verify the chosen policy is enforced
        assertTrue(ex.getMessage().contains("is bound to datasource 'ecommerce'"));
        assertTrue(ex.getMessage().contains("Switching to datasource 'crm' within the same session is not allowed"));

        // Also verify getOrCreateSession throws when datasource conflict occurs
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> sessionService.getOrCreateSession("session-locked", "crm"));
        assertTrue(ex2.getMessage().contains("Switching to datasource 'crm' within the same session is not allowed"));
    }

    @Test
    void separateCrmSessionWorksNormally() {
        // 5. Verify a separate CRM session works normally
        ConversationSessionService.ConversationSession crmSession = sessionService.getOrCreateSession("crm-session-1", "crm");
        assertEquals("crm", crmSession.getDatasourceId());

        sessionService.recordTurn(
                "crm-session-1",
                "Which opportunities are in pipeline?",
                new QueryIntent("crm", "amount", List.of("stage"), List.of(), null, "SUM", "PIPELINE", false, null),
                "SELECT opportunity_name, stage, amount FROM opportunities;",
                "Pipeline total is $1.48M",
                "crm"
        );

        assertEquals(1, sessionService.getHistory("crm-session-1").size());
        String crmHistory = sessionService.formatHistoryForPrompt("crm-session-1", "crm");
        assertTrue(crmHistory.contains("Which opportunities are in pipeline?"));
    }

    @Test
    void twoSeparateSessionsNeverShareHistory() {
        // 6. Verify two separate sessions never share history
        sessionService.getOrCreateSession("ecom-100", "ecommerce");
        sessionService.recordTurn(
                "ecom-100",
                "Ecom question",
                new QueryIntent("ecommerce", "revenue", List.of(), List.of(), null, "SUM", "SUMMARY", false, null),
                "SELECT 1 FROM orders;",
                "Ecom summary",
                "ecommerce"
        );

        sessionService.getOrCreateSession("crm-200", "crm");
        sessionService.recordTurn(
                "crm-200",
                "CRM question",
                new QueryIntent("crm", "leads", List.of(), List.of(), null, "COUNT", "SUMMARY", false, null),
                "SELECT 1 FROM leads;",
                "CRM summary",
                "crm"
        );

        String ecomPrompt = sessionService.formatHistoryForPrompt("ecom-100");
        String crmPrompt = sessionService.formatHistoryForPrompt("crm-200");

        assertTrue(ecomPrompt.contains("Ecom question"));
        assertFalse(ecomPrompt.contains("CRM question"));

        assertTrue(crmPrompt.contains("CRM question"));
        assertFalse(crmPrompt.contains("Ecom question"));
    }

    @Test
    void handlesUnknownSessionIdConsistently() {
        assertTrue(sessionService.getHistory("non-existent-uuid").isEmpty());
        assertEquals("", sessionService.formatHistoryForPrompt("non-existent-uuid"));

        ConversationSessionService.ConversationSession newSession = sessionService.getOrCreateSession("non-existent-uuid", "ecommerce");
        assertNotNull(newSession);
        assertEquals("ecommerce", newSession.getDatasourceId());
    }
}
