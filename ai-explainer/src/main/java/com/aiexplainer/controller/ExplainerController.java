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

    @PostMapping("/generate")
    public ResponseEntity<ExplainerResponse> generate(@RequestBody ExplainerRequest request) {
        try {
            String topic = request.getTopic();
            String difficulty = request.getDifficulty() != null ? request.getDifficulty() : "beginner";
            String model = request.getModel();
            String fileId = topic.replaceAll("\\s+", "_").toLowerCase() + "_" + System.currentTimeMillis();

            String script = scriptGenerator.generateScript(topic, difficulty, model);
            List<Scene> scenes = sceneParser.parseScenes(script);

            if (scenes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ExplainerResponse.builder()
                        .status("ERROR")
                        .message("No scenes parsed from script.")
                        .build());
            }

            String audioPath = voiceGenerator.generateAudio(script, fileId);
            videoAssembler.assembleVideo(scenes, audioPath, fileId);

            return ResponseEntity.ok(ExplainerResponse.builder()
                .topic(topic)
                .fullScript(script)
                .scenes(scenes)
                .audioFileName(fileId + ".mp3")
                .videoFileName(fileId + ".mp4")
                .status("SUCCESS")
                .message("Video generated successfully")
                .build());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ExplainerResponse.builder()
                    .status("ERROR")
                    .message(e.getMessage())
                    .build());
        }
    }

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

    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(outputDir, fileName).toAbsolutePath();

            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
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
