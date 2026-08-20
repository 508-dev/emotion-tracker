# Claude Code Instructions

The canonical agent instructions are in `AGENTS.md`.

Follow the same repo rules as Codex:

- Read before editing.
- Prefer repo-provided entrypoints: `./gradlew <task>` for build/lint/test,
  or the thin wrappers in `scripts/`.
- Keep changes scoped.
- Update `docs/`, tests, and the `gradle/libs.versions.toml` version
  comments when contracts or pinned dependency versions change.
- Use gitignored `.context/` for concise workspace-local operational memory.
  Promote durable knowledge into tracked docs instead of committing `.context/`.
