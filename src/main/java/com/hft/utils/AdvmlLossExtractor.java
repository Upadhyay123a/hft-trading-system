package com.hft.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simple utility to parse logs/all_backtests_run_with_fetch.log for "Avg Loss: <num>"
 * and write docs/advml_loss.csv (index,loss).
 */
public class AdvmlLossExtractor {
    public static void main(String[] args) throws IOException {
        Path log = Paths.get("logs", "all_backtests_run_with_fetch.log");
        if (!Files.exists(log)) {
            System.err.println("Log file not found: " + log.toAbsolutePath());
            System.exit(2);
        }

        Pattern p = Pattern.compile("Avg Loss:\\s*([0-9]*\\.?[0-9]+)");
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        List<Double> losses = lines.stream()
            .map(p::matcher)
            .filter(Matcher::find)
            .map(m -> Double.parseDouble(m.group(1)))
            .collect(Collectors.toList());

        if (losses.isEmpty()) {
            System.out.println("No Avg Loss entries found in log.");
            System.exit(0);
        }

        Path out = Paths.get("docs", "advml_loss.csv");
        Files.createDirectories(out.getParent());
        List<String> outLines = losses.stream()
            .map(d -> String.format("%d,%.6f", losses.indexOf(d) + 1, d))
            .collect(Collectors.toList());
        // Prepend header
        outLines.add(0, "index,loss");
        Files.write(out, outLines, StandardCharsets.UTF_8);
        System.out.println("Wrote: " + out.toAbsolutePath());
    }
}
