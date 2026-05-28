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

# Search indexed content (BM25 on file content)
andro search "jwt"
andro search "validateToken" -n 5

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

## Modules

| Module | Role |
|--------|------|
| `common` | Shared types |
| `ingestion` | File watching / ingestion |
| `indexing` | Apache Lucene indexing |
| `search` | Lucene search |
| `llm` | LLM integrations |
| `core` | WebFlux server entry point |
| `cli` | Command-line entry point |
