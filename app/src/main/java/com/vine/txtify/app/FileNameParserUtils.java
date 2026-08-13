package com.vine.txtify.app;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class to parse and clean raw multi-line pasted text containing file names.
 * Strips numbering (e.g., "1.", "1,", "1)"), bullets, quotes, and extra whitespace.
 */
public class FileNameParserUtils {

    /**
     * Parses a raw multi-line string into a Set of clean file names.
     *
     * Handles inputs like:
     * - "1. MainActivity.java"
     * - "1, MainActivity.java"
     * - "1) MainActivity.java"
     * - "[1] MainActivity.java"
     * - "- activity_main.xml"
     * - "* build.gradle"
     * - "'MyFile.txt'"
     *
     * @param rawInput Multi-line string pasted by the user.
     * @return Set of sanitized file names.
     */
    public static Set<String> parseFileNames(String rawInput) {
        Set<String> cleanNames = new HashSet<>();
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return cleanNames;
        }

        // Split text by line breaks
        String[] lines = rawInput.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Step 1: Remove leading numbers with prefixes/suffixes (e.g., "1.", "1,", "1)", "[1]")
            String cleaned = trimmed.replaceAll("^(\\[?\\d+\\]?|\\d+)[.\\,)]?\\s*", "");

            // Step 2: Remove leading bullet symbols (e.g., "-", "*", "•")
            cleaned = cleaned.replaceAll("^[\\-*•]\\s*", "");

            // Step 3: Remove surrounding single or double quotes
            cleaned = cleaned.replaceAll("^[\"']|[\"']$", "");

            // Step 4: Final trim of surrounding whitespace
            cleaned = cleaned.trim();

            if (!cleaned.isEmpty()) {
                cleanNames.add(cleaned);
            }
        }
        return cleanNames;
    }

    /**
     * Utility method to check if a actual file name matches any of the target names in the set.
     * Performs a case-insensitive check.
     *
     * @param actualFileName The name of the file being checked (e.g., "MainActivity.java")
     * @param targetNames Set of cleaned target file names.
     * @return true if there is a match, false otherwise.
     */
    public static boolean matchesAny(String actualFileName, Set<String> targetNames) {
        if (actualFileName == null || targetNames == null || targetNames.isEmpty()) {
            return false;
        }

        for (String target : targetNames) {
            if (actualFileName.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}