package com.analytics.agent.service;

import com.analytics.exception.GroqConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmClientService {

    private static final Logger log = LoggerFactory.getLogger(LlmClientService.class);

    private final ChatModel chatModel;
    private final String apiKey;
    private final String modelName;

    public LlmClientService(
            ChatModel chatModel,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.model:llama-3.3-70b-versatile}") String modelName) {
        this.chatModel = chatModel;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = modelName != null && !modelName.isBlank() ? modelName.trim() : "llama-3.3-70b-versatile";
    }

    public void validateConfiguration() {
        if (apiKey.isEmpty() || apiKey.equals("${GROQ_API_KEY}")) {
            throw new GroqConfigurationException("Groq API key is not configured. Please set GROQ_API_KEY in the environment or .env file.");
        }
    }

    public String generate(String systemPrompt, String userPrompt) {
        validateConfiguration();

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            ChatResponse response = chatModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new GroqConfigurationException("Groq API returned an empty response for model '" + modelName + "'.");
            }

            String output = response.getResult().getOutput().getText();
            return output != null ? output.trim() : "";
        } catch (GroqConfigurationException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = extractProviderError(e);
            log.error("Groq API request failed for model '{}': {}", modelName, errorMsg, e);
            throw new GroqConfigurationException("Groq API error (" + modelName + "): " + errorMsg, e);
        }
    }

    public String getModelName() {
        return modelName;
    }

    private String extractProviderError(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return message;
    }
}
