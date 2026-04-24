package com.aiexplainer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ScriptGeneratorService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateScript(String topic, String difficulty, String model) {
        String prompt = buildPrompt(topic, difficulty);

        String selectedModel = (model != null && !model.isEmpty()) ? model : this.model;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", selectedModel);
        requestBody.put("max_tokens", 1500);

        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", List.of(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> messageResponse = (Map<String, Object>) choices.get(0).get("message");
        return (String) messageResponse.get("content");
    }

    private String buildPrompt(String topic, String difficulty) {
        return String.format("""
                Explain "%s" using the Feynman technique.
                Difficulty level: %s
                Target audience: college engineering student
                
                STRICT RULES:
                - Use simple language and short sentences
                - Include one real-life analogy
                - Break into exactly 5 scenes
                - NEVER say "Scene 1", "next slide", "in this slide", "moving on", "as you can see"
                - Write narration as natural flowing speech only
                - Each narration must be 4-6 full sentences (minimum 60 words per scene)
                - Provide detailed explanations with examples
                - Make it educational and comprehensive
                
                Format EXACTLY as:
                [SCENE X: Title]
                Narration: (4-6 detailed sentences explaining the concept thoroughly)
                Visual: (one keyword for image search)
                
                Scenes:
                Scene 1: What is the problem? (Explain the core issue in detail)
                Scene 2: Real-life analogy (Use a detailed everyday example)
                Scene 3: Core concept explained (Break down the technical details)
                Scene 4: Step-by-step example (Walk through a complete example)
                Scene 5: Summary and key takeaway (Recap with practical insights)
                
                Total script should be 300-400 words.
                """, topic, difficulty);
    }
}
