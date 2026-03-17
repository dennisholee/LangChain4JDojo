package io.forest.ralphloop.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple helper tool used by the {@code CoderAgent} to persist generated source files to disk.
 *
 * <p>The tool creates parent directories as needed and writes the provided content to the
 * specified path. It is intentionally minimal and will overwrite files at the same path.
 */
public class CodeWriterTool {

    /**
     * Writes the given content to the file at {@code path}, creating parent directories when necessary.
     *
     * @param path the fully-qualified path (relative or absolute) including filename
     * @param content the full file contents to write
     */
    @Tool("Writes generated Java code to a specific file path within the project")
    public void writeJavaFile(
        @P("The fully qualified path including filename") String path,
        @P("The complete source code content") String content
    ) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent()); // Ensure directories exist
            Files.writeString(filePath, content);
            System.out.println("Successfully wrote file to: " + filePath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Java file", e);
        }
    }
}
