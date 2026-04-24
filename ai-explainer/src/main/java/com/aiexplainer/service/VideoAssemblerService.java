package com.aiexplainer.service;

import com.aiexplainer.model.Scene;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.io.*;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

@Service
public class VideoAssemblerService {

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${output.dir}")
    private String outputDir;

    private static final String FONT_PATH = "C\\:/aiexplainer/arial.ttf";
    private static final int SCENE_DURATION = 18;

    private int getAudioDuration(String audioPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-i", audioPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("Duration:")) {
                // Format: Duration: 00:01:23.45
                Pattern p = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+)");
                Matcher m = p.matcher(line);
                if (m.find()) {
                    int hours = Integer.parseInt(m.group(1));
                    int minutes = Integer.parseInt(m.group(2));
                    int seconds = Integer.parseInt(m.group(3));
                    return hours * 3600 + minutes * 60 + seconds + 2; // +2 buffer
                }
            }
        }
        process.waitFor();
        return SCENE_DURATION * 5; // fallback
    }
    private final RestTemplate restTemplate = new RestTemplate();

    public String assembleVideo(List<Scene> scenes, String audioPath, String fileName) throws IOException, InterruptedException {
        List<String> imagePaths = downloadImages(scenes, fileName);
        List<String> sceneVideos = new ArrayList<>();

        // Calculate per-scene duration based on actual audio length
        int totalAudioSeconds = getAudioDuration(audioPath);
        int perSceneDuration = Math.max(18, totalAudioSeconds / scenes.size());
        System.out.println("Audio duration: " + totalAudioSeconds + "s, per scene: " + perSceneDuration + "s");

        for (int i = 0; i < scenes.size(); i++) {
            System.out.println("Building scene " + i + " of " + scenes.size());
            try {
                String sceneVideo = buildSceneVideo(scenes.get(i), imagePaths.get(i), fileName, i, scenes.size(), perSceneDuration);
                System.out.println("Scene " + i + " done: " + sceneVideo + " exists=" + Files.exists(Paths.get(sceneVideo)));
                if (!Files.exists(Paths.get(sceneVideo))) {
                    throw new RuntimeException("Scene video not created: " + sceneVideo);
                }
                sceneVideos.add(sceneVideo);
            } catch (Exception e) {
                System.out.println("Scene " + i + " FAILED: " + e.getMessage());
                throw e;
            }
        }

        String concatVideo = concatenateVideos(sceneVideos, fileName);
        String outputVideo = Paths.get(outputDir, fileName + ".mp4").toString();
        mergeAudio(concatVideo, audioPath, outputVideo);

        // Cleanup
        for (String sv : sceneVideos) Files.deleteIfExists(Paths.get(sv));
        Files.deleteIfExists(Paths.get(concatVideo));
        for (String img : imagePaths) Files.deleteIfExists(Paths.get(img));

        return outputVideo;
    }

    private List<String> downloadImages(List<Scene> scenes, String fileName) {
        List<String> paths = new ArrayList<>();
        String[] fallbackColors = {"1a1a2e", "16213e", "0f3460", "1a1a2e", "16213e"};

        for (int i = 0; i < scenes.size(); i++) {
            String imgPath = Paths.get(outputDir, fileName + "_img" + i + ".jpg").toString();
            try {
                String keyword = scenes.get(i).getVisualKeyword() != null
                    ? scenes.get(i).getVisualKeyword().replace(" ", "+")
                    : "technology";
                String url = "https://source.unsplash.com/1280x720/?" + keyword;

                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0");
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Files.write(Paths.get(imgPath), response.getBody());
                } else {
                    createColorImage(imgPath, fallbackColors[i % fallbackColors.length]);
                }
            } catch (Exception e) {
                try { createColorImage(imgPath, fallbackColors[i % fallbackColors.length]); } catch (Exception ignored) {}
            }
            paths.add(imgPath);
        }
        return paths;
    }

    private void createColorImage(String imgPath, String hexColor) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-y",
            "-f", "lavfi",
            "-i", "color=c=0x" + hexColor + ":size=1280x720:duration=1",
            "-frames:v", "1", imgPath
        );
        pb.redirectErrorStream(true);
        pb.start().waitFor();
    }

    private String buildSceneVideo(Scene scene, String imagePath, String fileName, int index, int totalScenes, int sceneDuration)
            throws IOException, InterruptedException {

        String outputPath = Paths.get(outputDir, fileName + "_s" + index + ".mp4").toString();

        String title = sanitizeText(scene.getTitle());
        List<String> narrationLines = splitIntoLines(sanitizeText(scene.getNarration()), 65);

        StringBuilder vf = new StringBuilder();

        // Scale image + dark overlay for readability
        vf.append("scale=1280:720,");
        vf.append("colorchannelmixer=rr=0.25:gg=0.25:bb=0.25,");

        // Fade in and fade out animation
        vf.append("fade=t=in:st=0:d=0.8,");
        vf.append("fade=t=out:st=").append(sceneDuration - 1).append(":d=0.8,");

        // Animated title — top of screen
        vf.append(String.format(
            "drawtext=fontfile='%s':text='%s':fontsize=48:fontcolor=white" +
            ":x='if(lt(t,0.5),(w-text_w)/2 - (0.5-t)*400,(w-text_w)/2)'" +
            ":y=60:shadowcolor=black:shadowx=3:shadowy=3,",
            FONT_PATH, title
        ));

        // Divider line below title
        vf.append("drawbox=x=100:y=130:w=1080:h=3:color=0x7c6af7@0.9:t=fill,");

        // Narration lines — centered vertically in remaining space
        int totalLines = narrationLines.size();
        int lineHeight = 48;
        int totalTextHeight = totalLines * lineHeight;
        int startY = (720 - totalTextHeight) / 2 + 40;

        for (int i = 0; i < narrationLines.size(); i++) {
            double lineDelay = 0.5 + (i * 0.3);
            vf.append(String.format(
                "drawtext=fontfile='%s':text='%s':fontsize=32:fontcolor=0xeeeeee" +
                ":x=(w-text_w)/2:y=%d" +
                ":alpha='if(lt(t,%s),0,min(1,(t-%s)/0.4))'" +
                ":shadowcolor=black:shadowx=2:shadowy=2,",
                FONT_PATH, narrationLines.get(i), startY + (i * lineHeight),
                lineDelay, lineDelay
            ));
        }

        // Progress bar
        int barWidth = (int)(1280.0 * (index + 1) / totalScenes);
        vf.append("drawbox=x=0:y=708:w=1280:h=12:color=0x333333@0.8:t=fill,");
        vf.append(String.format("drawbox=x=0:y=708:w=%d:h=12:color=0x7c6af7@1.0:t=fill", barWidth));

        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-y",
            "-loop", "1",
            "-i", imagePath,
            "-vf", vf.toString(),
            "-t", String.valueOf(sceneDuration),
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-r", "25",
            outputPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder log = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) log.append(line).append("\n");
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new RuntimeException("Scene " + index + " failed: " + log);

        return outputPath;
    }

    private String concatenateVideos(List<String> videoPaths, String fileName) throws IOException, InterruptedException {
        Path concatFile = Paths.get(outputDir, fileName + "_concat.txt");
        StringBuilder sb = new StringBuilder();
        for (String v : videoPaths) {
            String forwardSlash = v.replace("\\", "/");
            sb.append("file ").append(forwardSlash).append("\n");
        }
        Files.writeString(concatFile, sb.toString());
        System.out.println("Concat file content:\n" + sb);

        String outputPath = Paths.get(outputDir, fileName + "_noaudio.mp4").toString();

        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", concatFile.toAbsolutePath().toString(),
            "-c", "copy", outputPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder log = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) log.append(line).append("\n");
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new RuntimeException("Concat failed: " + log);

        Files.deleteIfExists(concatFile);
        return outputPath;
    }

    private void mergeAudio(String videoPath, String audioPath, String outputPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-y",
            "-i", videoPath,
            "-i", audioPath,
            "-shortest",
            "-c:v", "copy",
            "-c:a", "aac",
            outputPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder log = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) log.append(line).append("\n");
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new RuntimeException("Audio merge failed: " + log);
    }

    private List<String> splitIntoLines(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxChars) {
                if (current.length() > 0) {
                    lines.add(current.toString().trim());
                    current = new StringBuilder();
                }
            }
            current.append(word).append(" ");
        }
        if (current.length() > 0) lines.add(current.toString().trim());
        return lines;
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        return text
            .replace("'", "")
            .replace("\"", "")
            .replace(":", " -")
            .replace("[", "").replace("]", "")
            .replace("\\", "")
            .replace("\n", " ")
            .trim();
    }
}
