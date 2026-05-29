# Andromedia

Multi-module Spring Boot scaffold (Java 21).

```text
    _              __
   / \   ____  ____/ /________
  / _ \ / __ \/ __  / ___/ __ \
 / ___ / / / / /_/ / /  / /_/ /
/_/  |_/_/ /_/\__,_/_/   \____/

    local-first ai developer cli
```

## Build

```bash
mvn clean verify
```

## Run server (WebFlux)

```bash
mvn install -DskipTests
mvn -pl core spring-boot:run
```

Health: `http://localhost:8080/api/v1/health` and `http://localhost:8080/actuator/health`

## Run CLI (Picocli)

```bash
mvn install -DskipTests
cli/bin/andro --help
cli/bin/andro version
```

## Install `andro` on Linux

```bash
# One-command installer (builds jar + installs launcher in ~/.local/bin)
./cli/bin/install-andro
```

If `~/.local/bin` is not already in your PATH, add this to your shell profile (for bash, usually `~/.bashrc`):

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Reload your shell and verify:

```bash
andro --help
andro version
andro search "Lucene"
```

If you see `andro: command not found`, run:

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
which andro
```

If `which andro` is still empty, reinstall the launcher from the repository root:

```bash
./cli/bin/install-andro
```

### Index and search

```bash
# Index a project (default extensions: java, kt, kts, groovy)
andro index /path/to/project

# Search indexed content (BM25 on method/class/file chunks)
andro search "jwt"
andro search "validateToken" -n 5

# Semantic search (after `andro index --embed`)
andro search --semantic "token validation logic"

# Hybrid BM25 + semantic fusion (recommended when embeddings exist)
andro search --hybrid "token validation logic"

# Debug: channel scores + optional expanded LLM context blocks
andro --debug search --hybrid --expand-context "validateToken"

# Debug mode (prints diagnostics to stderr)
andro --debug search "jwt"

# Optional: per-project index location (use same path for index + search)
ANDROMEDIA_JAVA_OPTS='-Dandromedia.index.path=/path/to/project/.andromedia-index' \
  andro index /path/to/project
ANDROMEDIA_JAVA_OPTS='-Dandromedia.index.path=/path/to/project/.andromedia-index' \
  andro search "jwt"
```

### Advanced: run jar directly (fallback)

```bash
java -jar cli/target/andromedia-cli-0.1.0-SNAPSHOT.jar --help
java -jar cli/target/andromedia-cli-0.1.0-SNAPSHOT.jar search "jwt"
```

Note: `./cli/bin/install-andro` builds with `-am` and `-DskipTests` for reliable local installation UX.

## CLI logs and debug mode

- Normal command output is kept clean on stdout.
- Detailed diagnostics are written to `~/.andromedia/logs/andro.log`.
- Use `--debug` on the root command to print query/index diagnostics to stderr:
- A custom ASCII banner is shown only for root entry (`andro`) and root help (`andro --help`).

```bash
andro --debug search "jwt"
tail -n 100 ~/.andromedia/logs/andro.log
```

Default base index directory: `~/.andromedia/index`

Andromedia now stores Lucene indexes in per-project folders:

```text
~/.andromedia/index/<projectId>/
```

`projectId` is the first 8 hexadecimal characters of `sha256(canonicalWorkspaceRootPath)`.

Workspace root is discovered by walking upward from the start directory and using the first
directory that contains:
- `.andromedia/` (authoritative workspace marker, created during indexing)

If `.andromedia/` is not found, Andromedia falls back to the first directory containing one of:
- `.git/`
- `pom.xml`
- `package.json`
- `settings.gradle`
- `settings.gradle.kts`

If no marker is found, the provided start directory is used as the workspace root.

`andro search "..."` is project-scoped by current working directory. Searching from a nested
folder inside the same workspace uses the same project index when `.andromedia/` exists at the
workspace root.

## Retrieval architecture (Phase 1)

Andromedia indexes **semantic-boundary chunks** (not whole files by default):

```text
Source files → Chunking (method-first) → CodeChunks → Lucene BM25
```

- **Java**: one chunk per method when extraction succeeds; class body fallback; file fallback last.
- **Other default extensions** (`kt`, `kts`, `groovy`): file-level chunk in Phase 1.
- Oversized methods stay a **single chunk** in v1 (warned during indexing).
- Search hits include location (`File.java:42-58`) and symbol (`validateToken`) when available.

Phase 2: same chunks can receive OpenRouter embeddings (`openai/text-embedding-ada-002`, 1536-dim) for semantic search.

```bash
export OPENROUTER_API_KEY="your-key"

# Index with embeddings (OpenRouter API calls per chunk)
andro index --embed --recreate /path/to/project

# Semantic vector search (requires embedded index)
andro search --semantic "jwt bearer validation"
```

Phase 3: hybrid retrieval fuses BM25 + semantic with reciprocal rank fusion (RRF), deduplicated by `chunkId`. Retrieval stays at method/class chunk granularity; `ContextAssembler` expands hits into broader LLM context (imports, headers, neighbor lines) when using `--debug --expand-context`.

```bash
andro search --hybrid "jwt bearer validation"
andro --debug search --hybrid --expand-context "validateToken"
```

Without `--embed`, `--hybrid` falls back to BM25-only (semantic channel skipped).

## Modules

| Module | Role |
|--------|------|
| `common` | Shared types (`CodeChunk`, index field constants) |
| `ingestion` | Semantic-boundary chunking |
| `indexing` | Apache Lucene chunk indexing |
| `search` | Lucene BM25 chunk search |
| `llm` | LLM integrations |
| `core` | WebFlux server entry point |
| `cli` | Command-line entry point |
