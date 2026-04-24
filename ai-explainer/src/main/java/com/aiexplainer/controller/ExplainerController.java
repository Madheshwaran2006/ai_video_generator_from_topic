package com.aiexplainer.controller;

import com.aiexplainer.model.*;
import com.aiexplainer.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ExplainerController {

    @Autowired private ScriptGeneratorService scriptGenerator;
    @Autowired private SceneParserService sceneParser;
    @Autowired private VoiceGeneratorService voiceGenerator;
    @Autowired private VideoAssemblerService videoAssembler;

    @Value("${output.dir}")
    private String outputDir;

    // Full pipeline endpoint
    @PostMapping("/generate")
    public ResponseEntity<ExplainerResponse> generate(@RequestBody ExplainerRequest request) {
        try {
            String topic = request.getTopic();
            String difficulty = request.getDifficulty() != null ? request.getDifficulty() : "beginner";
            String model = request.getModel();
            String fileId = topic.replaceAll("\\s+", "_").toLowerCase() + "_" + System.currentTimeMillis();

            // Step 1: Generate script
            String script = scriptGenerator.generateScript(topic, difficulty, model);

            // Step 2: Parse into scenes
            List<Scene> scenes = sceneParser.parseScenes(script);
            System.out.println("DEBUG: script length=" + script.length());
            System.out.println("DEBUG: scenes count=" + scenes.size());
            System.out.println("DEBUG: script preview=" + script.substring(0, Math.min(300, script.length())));

            if (scenes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ExplainerResponse.builder()
                        .status("ERROR")
                        .message("No scenes parsed from script. Script: " + script.substring(0, Math.min(500, script.length())))
                        .build());
            }

            // Step 3: Generate voice audio
            String audioPath = voiceGenerator.generateAudio(script, fileId);

            // Step 4: Assemble video
            String videoPath = videoAssembler.assembleVideo(scenes, audioPath, fileId);

            ExplainerResponse response = ExplainerResponse.builder()
                .topic(topic)
                .fullScript(script)
                .scenes(scenes)
                .audioFileName(fileId + ".mp3")
                .videoFileName(fileId + ".mp4")
                .status("SUCCESS")
                .message("Video generated successfully")
                .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ExplainerResponse.builder()
                    .status("ERROR")
                    .message(e.getMessage())
                    .build());
        }
    }

    // Script only endpoint (no audio/video)
    @PostMapping("/script")
    public ResponseEntity<ExplainerResponse> scriptOnly(@RequestBody ExplainerRequest request) {
        try {
            String script = scriptGenerator.generateScript(
                request.getTopic(),
                request.getDifficulty() != null ? request.getDifficulty() : "beginner",
                request.getModel()
            );
            List<Scene> scenes = sceneParser.parseScenes(script);

            return ResponseEntity.ok(ExplainerResponse.builder()
                .topic(request.getTopic())
                .fullScript(script)
                .scenes(scenes)
                .status("SUCCESS")
                .build());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ExplainerResponse.builder()
                    .status("ERROR")
                    .message(e.getMessage())
                    .build());
        }
    }

    // Download generated video
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(outputDir, fileName).toAbsolutePath();

            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
            }

            Resource resource = new FileSystemResource(filePath);
            String contentType = fileName.endsWith(".mp3") ? "audio/mpeg" : "video/mp4";

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
