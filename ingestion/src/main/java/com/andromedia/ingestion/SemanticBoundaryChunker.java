package com.andromedia.ingestion;

import com.andromedia.common.ChunkConstants;
import com.andromedia.common.ChunkIdGenerator;
import com.andromedia.common.ChunkUnitType;
import com.andromedia.common.CodeChunk;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SemanticBoundaryChunker implements ChunkingService {

  private static final Pattern CLASS_BODY_PATTERN =
      Pattern.compile(
          "(?s)\\b(?:class|interface|enum|record)\\s+\\w+\\b[^{]*\\{([\\s\\S]*)\\}\\s*$");

  @Override
  public List<CodeChunk> chunkFile(Path filePath, String content, String extension, String language) {
    String absolutePath = filePath.toAbsolutePath().normalize().toString();
    String fileName = filePath.getFileName().toString();

    if ("java".equalsIgnoreCase(language)) {
      List<CodeChunk> methodChunks = chunkJavaMethods(absolutePath, fileName, extension, language, content);
      if (!methodChunks.isEmpty()) {
        return methodChunks;
      }
      Optional<CodeChunk> classChunk =
          chunkJavaClass(absolutePath, fileName, extension, language, content);
      if (classChunk.isPresent()) {
        return List.of(classChunk.get());
      }
    }

    return List.of(buildFileChunk(absolutePath, fileName, extension, language, content));
  }

  private static List<CodeChunk> chunkJavaMethods(
      String absolutePath,
      String fileName,
      String extension,
      String language,
      String content) {
    Optional<String> className = JavaMethodExtractor.findClassName(content);
    List<JavaMethodExtractor.MethodSpan> methods = JavaMethodExtractor.extractMethods(content);
    if (methods.isEmpty()) {
      return List.of();
    }

    String resolvedClassName = className.orElse("");
    List<CodeChunk> chunks = new ArrayList<>();
    for (JavaMethodExtractor.MethodSpan method : methods) {
      chunks.add(
          buildChunk(
              absolutePath,
              fileName,
              extension,
              language,
              resolvedClassName,
              method.methodName(),
              ChunkUnitType.METHOD,
              method.startLine(),
              method.endLine(),
              method.content()));
    }
    return chunks;
  }

  private static Optional<CodeChunk> chunkJavaClass(
      String absolutePath,
      String fileName,
      String extension,
      String language,
      String content) {
    Optional<String> className = JavaMethodExtractor.findClassName(content);
    if (className.isEmpty()) {
      return Optional.empty();
    }

    Matcher matcher = CLASS_BODY_PATTERN.matcher(content.trim());
    if (!matcher.find()) {
      return Optional.empty();
    }

    int classStartLine = findLineNumber(content, matcher.start(1));
    int classEndLine = findLineNumber(content, matcher.end(1));
    String classBody = matcher.group(1).trim();
    if (classBody.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        buildChunk(
            absolutePath,
            fileName,
            extension,
            language,
            className.get(),
            null,
            ChunkUnitType.CLASS,
            classStartLine,
            classEndLine,
            classBody));
  }

  private static CodeChunk buildFileChunk(
      String absolutePath,
      String fileName,
      String extension,
      String language,
      String content) {
    int lineCount = countLines(content);
    return buildChunk(
        absolutePath,
        fileName,
        extension,
        language,
        null,
        null,
        ChunkUnitType.FILE,
        1,
        lineCount,
        content);
  }

  private static CodeChunk buildChunk(
      String absolutePath,
      String fileName,
      String extension,
      String language,
      String className,
      String methodName,
      ChunkUnitType unitType,
      int startLine,
      int endLine,
      String content) {
    int lineCount = Math.max(1, endLine - startLine + 1);
    boolean oversized = lineCount > ChunkConstants.OVERSIZED_LINE_THRESHOLD;
    String chunkId =
        ChunkIdGenerator.generate(absolutePath, unitType, className, methodName, startLine);
    return new CodeChunk(
        chunkId,
        content,
        absolutePath,
        fileName,
        extension,
        language,
        emptyToNull(className),
        emptyToNull(methodName),
        unitType,
        startLine,
        endLine,
        oversized);
  }

  private static String emptyToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  private static int countLines(String content) {
    if (content.isEmpty()) {
      return 1;
    }
    return (int) content.lines().count();
  }

  private static int findLineNumber(String content, int charIndex) {
    int line = 1;
    for (int i = 0; i < charIndex && i < content.length(); i++) {
      if (content.charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }
}
