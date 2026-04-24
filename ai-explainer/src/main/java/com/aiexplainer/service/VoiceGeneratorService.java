package com.aiexplainer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

@Service
public class VoiceGeneratorService {

    @Value("${output.dir}")
    private String outputDir;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    public String generateAudio(String script, String fileName) throws IOException, InterruptedException {
        Path outputPath = Paths.get(outputDir, fileName + ".mp3");
        Files.createDirectories(outputPath.getParent());

        // Clean script - remove all scene markers, labels, and formatting
        String cleanScript = script
            .replaceAll("\\[SCENE \\d+:[^\\]]+\\]", "")  // Remove [SCENE X: Title]
            .replaceAll("(?i)Narration:\\s*", "")          // Remove "Narration:"
            .replaceAll("(?i)Visual:\\s*[^\\n]+", "")      // Remove "Visual: keyword"
            .replaceAll("(?i)scene \\d+", "")              // Remove "scene 1", "scene 2"
            .replaceAll("(?i)next slide", "")              // Remove "next slide"
            .replaceAll("(?i)in this slide", "")           // Remove "in this slide"
            .replaceAll("(?i)moving on", "")               // Remove "moving on"
            .replaceAll("(?i)as you can see", "")          // Remove "as you can see"
            .replaceAll("\\*\\*", "")                      // Remove markdown bold
            .replaceAll("\\*", "")                         // Remove markdown italic
            .replaceAll("\\n+", " ")                       // Replace newlines with space
            .replaceAll("\\s+", " ")                       // Collapse multiple spaces
            .trim();

        System.out.println("Original script length: " + script.length());
        System.out.println("Clean script length: " + cleanScript.length());
        System.out.println("Clean script preview: " + cleanScript.substring(0, Math.min(200, cleanScript.length())));

        String wavPath = outputPath.toString().replace(".mp3", ".wav");

        // Use Windows built-in TTS via PowerShell
        String psScript = String.format("""
            Add-Type -AssemblyName System.Speech
            $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
            $synth.SetOutputToWaveFile('%s')
            $synth.Rate = 1
            $synth.Volume = 100
            $synth.Speak('%s')
            $synth.Dispose()
            """,
            wavPath,
            cleanScript.replace("'", " ")
        );

        // Write PowerShell script to temp file
        Path psFile = Paths.get(outputDir, fileName + ".ps1");
        Files.writeString(psFile, psScript);

        // Run PowerShell
        ProcessBuilder pb = new ProcessBuilder(
            "powershell", "-ExecutionPolicy", "Bypass", "-File", psFile.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();

        // Convert WAV to MP3 using FFmpeg full path
        ProcessBuilder ffmpegPb = new ProcessBuilder(
            ffmpegPath, "-y", "-i", wavPath, outputPath.toString()
        );
        ffmpegPb.redirectErrorStream(true);
        Process ffmpegProcess = ffmpegPb.start();
        ffmpegProcess.waitFor();

        // Cleanup temp files
        Files.deleteIfExists(psFile);
        Files.deleteIfExists(Paths.get(wavPath));

        return outputPath.toString();
    }
}
