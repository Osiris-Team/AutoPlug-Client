package com.osiris.autoplug.client.ai;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LLMClassContextCollector {

    private static final int CONTEXT_RADIUS = 2;
    private static final int MAX_SNIPPETS_PER_FILE = 6;

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "bin", "out", "node_modules", ".gradle"
    );

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "\\b(public|protected|private|static|final|\\s)+[\\w\\<\\>\\[\\]]+\\s+(\\w+)\\s*\\([^\\)]*\\)\\s*(\\{|throws)"
    );

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Path root = Paths.get(".").toAbsolutePath().normalize();

        printBanner();

        System.out.print("🔍 Scanning working directory for .java files... ");
        List<Path> javaFiles = discoverJavaFiles(root);
        System.out.println("Found " + javaFiles.size() + " files.\n");

        if (javaFiles.isEmpty()) {
            System.out.println("❌ No .java files found in the current directory or its subdirectories.");
            return;
        }

        // 1. Prompt for Class Name (with smart resolution & suggestions)
        String targetClassName = promptClassName(scanner, javaFiles);
        if (targetClassName == null) return;

        // 2. Prompt for LLM Objective
        String taskMode = promptTaskMode(scanner);
        String customPrompt = null;
        if ("custom".equals(taskMode)) {
            System.out.println("\n💬 Enter your specific prompt / question for the LLM:");
            System.out.print("> ");
            customPrompt = scanner.nextLine().trim();
        }

        // 3. Prompt for Output Preferences
        System.out.print("\n📋 Copy result to clipboard? [Y/n]: ");
        boolean copyToClip = !scanner.nextLine().trim().equalsIgnoreCase("n");

        String defaultFilename = targetClassName + "-context.md";
        System.out.print("💾 Save to file (" + defaultFilename + ")? [Y/n]: ");
        boolean saveToFile = !scanner.nextLine().trim().equalsIgnoreCase("n");

        System.out.print("🖥️  Print full preview to terminal? [y/N]: ");
        boolean printStdout = scanner.nextLine().trim().equalsIgnoreCase("y");

        // 4. Run Analysis
        System.out.println("\n⚙️  Analyzing class hierarchy, usages, and call sites...");
        String report = generateReport(root, javaFiles, targetClassName, taskMode, customPrompt);

        // 5. Deliver Results
        System.out.println("\n" + "=");
        System.out.println("                     RESULTS");
        System.out.println("=");

        if (printStdout) {
            System.out.println(report);
            System.out.println("=");
        }

        if (copyToClip) {
            boolean success = copyToClipboard(report);
            if (success) {
                System.out.println("✔ Copied entire prompt + context to Clipboard! (Ready to Ctrl+V / Cmd+V into LLM)");
            } else {
                System.out.println("⚠️  Clipboard not available in this environment. Falling back to file.");
                saveToFile = true;
            }
        }

        if (saveToFile) {
            try {
                Path outPath = Paths.get(defaultFilename);
                //Files.writeString(outPath, report, StandardCharsets.UTF_8);
                System.out.println("✔ Saved report to file: " + outPath.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("❌ Failed to save file: " + e.getMessage());
            }
        }

        int estimatedTokens = report.length() / 4;
        System.out.printf("📊 Payload size: ~%,d characters (~%,d estimated tokens)\n", report.length(), estimatedTokens);
        System.out.println("✨ Done!\n");
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        🤖  Interactive LLM Java Context Collector        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    private static String promptClassName(Scanner scanner, List<Path> javaFiles) {
        while (true) {
            System.out.print("Target Java Class Name (e.g. OrderService): ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.println("Class name cannot be empty. Try again.\n");
                continue;
            }

            // Clean up if user typed "OrderService.java"
            if (input.endsWith(".java")) {
                input = input.substring(0, input.length() - 5);
            }

            String finalInput = input;
            boolean exactMatch = javaFiles.stream().anyMatch(p -> p.getFileName().toString().equals(finalInput + ".java"));

            if (exactMatch) {
                return input;
            }

            // Case-insensitive / Substring suggestions
            List<String> suggestions = javaFiles.stream()
                    .map(p -> p.getFileName().toString().replace(".java", ""))
                    .filter(name -> name.toLowerCase().contains(finalInput.toLowerCase()))
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());

            if (!suggestions.isEmpty()) {
                System.out.println("⚠️  Exact class `" + input + ".java` not found. Did you mean:");
                for (int i = 0; i < suggestions.size(); i++) {
                    System.out.printf("   [%d] %s\n", i + 1, suggestions.get(i));
                }
                System.out.print("Select a number, or press Enter to retry [1-" + suggestions.size() + "]: ");
                String choice = scanner.nextLine().trim();
                try {
                    int idx = Integer.parseInt(choice) - 1;
                    if (idx >= 0 && idx < suggestions.size()) {
                        return suggestions.get(idx);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                System.out.println("⚠️  Class not found. You can still continue if it's defined inside an inner class or dynamically.");
                System.out.print("Continue with `" + input + "` anyway? [y/N]: ");
                if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                    return input;
                }
            }
            System.out.println();
        }
    }

    private static String promptTaskMode(Scanner scanner) {
        System.out.println("\nSelect your LLM Objective:");
        System.out.println("  [1] 🐛 Debugging / Find Bugs, Concurrency & Null Hazards (Default)");
        System.out.println("  [2] 🧪 Unit Test Suite Generation (JUnit 5 + Mockito)");
        System.out.println("  [3] 🔨 Refactoring & Architecture Review");
        System.out.println("  [4] 📖 Code Explanation & Usage Walkthrough");
        System.out.println("  [5] ✍️  Custom Question / Prompt");
        System.out.print("Choose [1-5] (Default: 1): ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "2": return "test";
            case "3": return "refactor";
            case "4": return "explain";
            case "5": return "custom";
            default:  return "debug";
        }
    }

    private static String generateReport(
            Path root,
            List<Path> javaFiles,
            String targetClassName,
            String taskMode,
            String customPrompt
    ) {
        Pattern usagePattern = Pattern.compile("\\b" + Pattern.quote(targetClassName) + "\\b");
        Pattern definitionPattern = Pattern.compile("\\b(class|interface|enum|record)\\s+" + Pattern.quote(targetClassName) + "\\b");

        Path definitionFile = null;
        List<String> definitionContent = null;
        Map<Path, List<Integer>> referenceMatches = new LinkedHashMap<>();
        Map<Path, Boolean> importedInFile = new LinkedHashMap<>();

        for (Path file : javaFiles) {
            List<String> lines = safeReadLines(file);
            if (lines.isEmpty()) continue;

            boolean isDef = file.getFileName().toString().equals(targetClassName + ".java")
                    || lines.stream().anyMatch(l -> definitionPattern.matcher(l).find());

            if (isDef && definitionFile == null) {
                definitionFile = file;
                definitionContent = lines;
                continue;
            }

            List<Integer> matchedLines = new ArrayList<>();
            boolean hasImport = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (usagePattern.matcher(line).find()) {
                    if (line.startsWith("import ")) {
                        hasImport = true;
                    } else {
                        matchedLines.add(i + 1);
                    }
                }
            }

            if (!matchedLines.isEmpty()) {
                referenceMatches.put(file, matchedLines);
            }
            if (hasImport) {
                importedInFile.put(file, true);
            }
        }

        return formatMarkdown(root, targetClassName, definitionFile, definitionContent, referenceMatches, importedInFile, taskMode, customPrompt);
    }

    private static String formatMarkdown(
            Path root,
            String targetClassName,
            Path defFile,
            List<String> defContent,
            Map<Path, List<Integer>> referenceMatches,
            Map<Path, Boolean> importedInFile,
            String taskMode,
            String customPrompt
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. LLM System Instructions
        sb.append("# Task Instructions\n\n");
        sb.append(buildTaskInstructions(targetClassName, taskMode, customPrompt));
        sb.append("\n---\n\n");

        // 2. High-level Summary
        sb.append("## Overview\n");
        sb.append("- **Class**: `").append(targetClassName).append("`\n");
        sb.append("- **Declared in**: ").append(defFile != null ? "`" + root.relativize(defFile) + "`" : "*(External/Not found)*").append("\n");
        sb.append("- **Referenced by**: ").append(referenceMatches.size()).append(" other files\n\n");

        // 3. Class Implementation
        sb.append("## Target Class Implementation\n\n");
        if (defFile != null && defContent != null) {
            sb.append("File: `").append(root.relativize(defFile)).append("`\n");
            sb.append("```java\n");
            for (int i = 0; i < defContent.size(); i++) {
                sb.append(String.format("%4d | %s\n", i + 1, defContent.get(i)));
            }
            sb.append("```\n\n");
        } else {
            sb.append("> ⚠️ **Notice**: Full class declaration was not found in the scanned workspace.\n\n");
        }

        // 4. Call Sites & Usages
        sb.append("## Call Sites & Dependencies\n\n");
        if (referenceMatches.isEmpty()) {
            sb.append("No active call sites found in other `.java` files.\n");
        } else {
            for (Map.Entry<Path, List<Integer>> entry : referenceMatches.entrySet()) {
                Path file = entry.getKey();
                List<Integer> lines = entry.getValue();
                List<String> fileLines = safeReadLines(file);

                sb.append("### Caller: `").append(root.relativize(file)).append("`\n");
                if (importedInFile.containsKey(file)) {
                    sb.append("*(Explicitly imported)*\n\n");
                }

                List<int[]> mergedRanges = mergeContextRanges(lines, fileLines.size(), CONTEXT_RADIUS);

                int snippetsCount = 0;
                sb.append("```java\n");
                for (int[] range : mergedRanges) {
                    if (snippetsCount >= MAX_SNIPPETS_PER_FILE) {
                        sb.append("     ... [Additional references omitted for brevity] ...\n");
                        break;
                    }

                    String enclosingMethod = findEnclosingMethod(fileLines, range[0]);
                    if (enclosingMethod != null) {
                        sb.append("  // Method context: ").append(enclosingMethod).append("\n");
                    }

                    for (int i = range[0]; i <= range[1]; i++) {
                        String marker = lines.contains(i) ? ">>" : "  ";
                        sb.append(String.format("%s %4d | %s\n", marker, i, fileLines.get(i - 1)));
                    }
                    if (range != mergedRanges.get(mergedRanges.size() - 1)) {
                        sb.append("     ... [snip] ...\n");
                    }
                    snippetsCount++;
                }
                sb.append("```\n\n");
            }
        }

        return sb.toString();
    }

    private static String buildTaskInstructions(String className, String taskMode, String customPrompt) {
        switch (taskMode) {
            case "custom":
                return customPrompt;
            case "test":
                return "You are a QA automation expert in Java (JUnit 5, AssertJ, Mockito).\n"
                        + "Create a comprehensive test suite for `" + className + "`.\n"
                        + "Requirements:\n"
                        + "1. Check the call sites below to see real-world usage patterns, expected data, and parameters.\n"
                        + "2. Test happy paths, null inputs, edge cases, and exception scenarios.\n"
                        + "3. Provide clean, copy-pasteable test code.";
            case "refactor":
                return "You are a senior Java software architect.\n"
                        + "Review `" + className + "` and its caller dependencies below for refactoring.\n"
                        + "Tasks:\n"
                        + "1. Identify code smells, tight coupling, and abstraction leaks.\n"
                        + "2. Suggest design pattern improvements (e.g. Strategy, Factory, Composition over Inheritance).\n"
                        + "3. Provide concrete refactored code snippets with minimal caller disruption.";
            case "explain":
                return "You are an expert Java developer.\n"
                        + "Explain the role and architecture of `" + className + "`.\n"
                        + "Tasks:\n"
                        + "1. Summarize what this class does and what responsibilities it holds.\n"
                        + "2. Explain how external callers interact with it and how data flows through it.\n"
                        + "3. Highlight any architectural quirks or notable dependencies.";
            default: // debug
                return "You are a principal Java engineer debugging `" + className + "`.\n"
                        + "Review the implementation and caller sites below to perform a root cause analysis.\n"
                        + "Tasks:\n"
                        + "1. Identify potential bugs, concurrency race conditions, memory leaks, or unhandled NullPointerExceptions.\n"
                        + "2. Trace caller data passed into this class and check for validation gaps.\n"
                        + "3. Provide exact code fixes with minimal blast radius.";
        }
    }

    private static String findEnclosingMethod(List<String> lines, int targetLine) {
        for (int i = targetLine - 1; i >= 0 && i >= targetLine - 35; i--) {
            Matcher m = METHOD_PATTERN.matcher(lines.get(i));
            if (m.find()) {
                return m.group(0).trim().replaceAll("\\{$", "").trim();
            }
        }
        return null;
    }

    private static List<int[]> mergeContextRanges(List<Integer> matchedLines, int totalLines, int radius) {
        List<int[]> raw = new ArrayList<>();
        for (int line : matchedLines) {
            raw.add(new int[]{Math.max(1, line - radius), Math.min(totalLines, line + radius)});
        }
        List<int[]> merged = new ArrayList<>();
        int[] cur = raw.get(0);
        for (int i = 1; i < raw.size(); i++) {
            int[] next = raw.get(i);
            if (next[0] <= cur[1] + 1) {
                cur[1] = Math.max(cur[1], next[1]);
            } else {
                merged.add(cur);
                cur = next;
            }
        }
        merged.add(cur);
        return merged;
    }

    private static boolean copyToClipboard(String content) {
        try {
            StringSelection selection = new StringSelection(content);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<Path> discoverJavaFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isIgnoredPath(p))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static boolean isIgnoredPath(Path path) {
        for (Path part : path) {
            if (IGNORED_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private static List<String> safeReadLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                return Files.readAllLines(path, StandardCharsets.ISO_8859_1);
            } catch (IOException ignored) {
                return Collections.emptyList();
            }
        }
    }
}