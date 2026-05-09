package io.forest.composableretrieval.adapters;

import io.forest.composableretrieval.core.port.RetrieverPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Adapter — implements {@link RetrieverPort} by searching .txt files in a directory.
 *
 * <p>Example for demonstrating composable retrieval with file-based documents.
 * Uses simple keyword matching across file contents.
 */
public class FileSystemRetrieverAdapter implements RetrieverPort {

    private final Path directory;

    public FileSystemRetrieverAdapter(String directory) {
        this.directory = Path.of(directory);
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) throws Exception {
        String[] keywords = query.toLowerCase().split("\\s+");
        List<RetrievalResult> results = new ArrayList<>();

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return results;
        }

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.toString().endsWith(".txt"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        String lowerContent = content.toLowerCase();
                        int matchCount = 0;

                        // Count keyword matches
                        for (String keyword : keywords) {
                            if (lowerContent.contains(keyword)) {
                                matchCount++;
                            }
                        }

                        if (matchCount > 0) {
                            double score = (double) matchCount / keywords.length;
                            String fileId = file.getFileName().toString();
                            // Use first 200 chars as snippet
                            String snippet = content.length() > 200
                                ? content.substring(0, 200) + "…"
                                : content;
                            results.add(new RetrievalResult(fileId, snippet, score));
                        }
                    } catch (IOException e) {
                        // Skip file on read error
                    }
                });
        }

        // Sort by score descending
        results.sort(Comparator.comparingDouble(RetrievalResult::score).reversed());

        return results.stream().limit(topK).toList();
    }
}
