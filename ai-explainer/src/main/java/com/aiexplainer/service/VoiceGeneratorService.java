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

        String cleanScript = script
            .replaceAll("\\[SCENE \\d+:[^\\]]+\\]", "")
            .replaceAll("(?i)Narration:\\s*", "")
            .replaceAll("(?i)Visual:\\s*[^\\n]+", "")
            .replaceAll("(?i)scene \\d+", "")
            .replaceAll("(?i)next slide", "")
            .replaceAll("(?i)in this slide", "")
            .replaceAll("(?i)moving on", "")
            .replaceAll("(?i)as you can see", "")
            .replaceAll("\\*\\*", "")
            .replaceAll("\\*", "")
            .replaceAll("\\n+", " ")
            .replaceAll("\\s+", " ")
            .trim();

        String wavPath = outputPath.toString().replace(".mp3", ".wav");

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

        Path psFile = Paths.get(outputDir, fileName + ".ps1");
        Files.writeString(psFile, psScript);

        ProcessBuilder pb = new ProcessBuilder(
            "powershell", "-ExecutionPolicy", "Bypass", "-File", psFile.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();

        ProcessBuilder ffmpegPb = new ProcessBuilder(
            ffmpegPath, "-y", "-i", wavPath, outputPath.toString()
        );
        ffmpegPb.redirectErrorStream(true);
        Process ffmpegProcess = ffmpegPb.start();
        ffmpegProcess.waitFor();

        Files.deleteIfExists(psFile);
        Files.deleteIfExists(Paths.get(wavPath));

        return outputPath.toString();
    }
}
