package com.aiexplainer.service;

import com.aiexplainer.model.Scene;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class SceneParserService {

    public List<Scene> parseScenes(String script) {
        List<Scene> scenes = new ArrayList<>();

        // Split script into scene blocks by [SCENE X: ...]
        String[] blocks = script.split("(?=\\[SCENE \\d+)");

        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;

            try {
                // Extract scene number and title
                Pattern headerPattern = Pattern.compile("\\[SCENE (\\d+):\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
                Matcher headerMatcher = headerPattern.matcher(block);
                if (!headerMatcher.find()) continue;

                int sceneNumber = Integer.parseInt(headerMatcher.group(1).trim());
                String title = headerMatcher.group(2).trim();

                // Remove the header from block
                String afterHeader = block.substring(headerMatcher.end()).trim();

                // Try to extract narration between "Narration:" and "Visual:"
                String narration = "";
                String visual = "technology";

                Pattern narrationPattern = Pattern.compile("Narration:\\s*(.+?)\\s*(?:Visual:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                Matcher narrationMatcher = narrationPattern.matcher(afterHeader);

                if (narrationMatcher.find()) {
                    narration = narrationMatcher.group(1).trim();
                } else {
                    // Groq may not use "Narration:" label — take all text before "Visual:"
                    Pattern plainPattern = Pattern.compile("^(.+?)\\s*(?:Visual:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                    Matcher plainMatcher = plainPattern.matcher(afterHeader);
                    if (plainMatcher.find()) {
                        narration = plainMatcher.group(1).trim();
                    } else {
                        // Take everything except last line (which is Visual)
                        String[] lines = afterHeader.split("\\n");
                        StringBuilder nb = new StringBuilder();
                        for (int i = 0; i < lines.length - 1; i++) {
                            if (!lines[i].trim().toLowerCase().startsWith("visual:")) {
                                nb.append(lines[i].trim()).append(" ");
                            }
                        }
                        narration = nb.toString().trim();
                    }
                }

                // Extract visual keyword
                Pattern visualPattern = Pattern.compile("Visual:\\s*(.+)", Pattern.CASE_INSENSITIVE);
                Matcher visualMatcher = visualPattern.matcher(block);
                if (visualMatcher.find()) {
                    visual = visualMatcher.group(1).trim();
                }

                // Clean up narration
                narration = narration
                    .replaceAll("\\*\\*", "")
                    .replaceAll("\\*", "")
                    .replaceAll("Visual:.*", "")
                    .trim();

                Scene scene = Scene.builder()
                    .sceneNumber(sceneNumber)
                    .title(title)
                    .narration(narration)
                    .visualKeyword(visual)
                    .build();

                scenes.add(scene);
                System.out.println("Parsed scene " + sceneNumber + ": " + title);
                System.out.println("Narration length: " + narration.length() + " chars");
                System.out.println("Narration preview: " + narration.substring(0, Math.min(80, narration.length())));

            } catch (Exception e) {
                System.out.println("Failed to parse block: " + e.getMessage());
            }
        }

        System.out.println("Total scenes parsed: " + scenes.size());
        return scenes;
    }
}
