package com.andromedia.llm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andromedia.common.ChunkUnitType;
import com.andromedia.common.RetrievalReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextAssemblerTest {

  @TempDir
  Path tempDirectory;

  @Test
  void expandsMethodHitWithHeaderAndNeighborWindow() throws Exception {
    Path sourceFile = tempDirectory.resolve("AuthService.java");
    Files.writeString(
        sourceFile,
        """
        package demo;

        import java.util.List;

        public class AuthService {
          private final List<String> roles;

          public boolean validateJwtToken(String token) {
            return token != null && token.startsWith("Bearer");
          }
        }
        """);

    ContextExpansionProperties properties = new ContextExpansionProperties();
    properties.setNeighborLineWindow(2);
    ContextAssembler assembler = new ContextAssembler(properties);

    RetrievalReference hit =
        new RetrievalReference(
            "chunk-1",
            sourceFile.toString(),
            "AuthService.java",
            "AuthService",
            "validateJwtToken",
            ChunkUnitType.METHOD,
            9,
            11);

    AssembledContext context = assembler.assemble(List.of(hit));

    assertTrue(context.blocks().size() >= 2);
    String prompt = context.toPromptText();
    assertTrue(prompt.contains("package demo"));
    assertTrue(prompt.contains("validateJwtToken"));
    assertTrue(prompt.contains("startsWith(\"Bearer\")"));
  }
}
