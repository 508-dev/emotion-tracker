# Contributing

## Local Checks

Run the narrowest relevant checks while iterating:

```bash
./gradlew ktlintCheck
./gradlew testDebugUnitTest
```

Before opening or updating a PR, run:

```bash
./gradlew check
```

`./scripts/lint.sh`, `./scripts/test.sh`, and `./scripts/check-all.sh` are
thin wrappers around the same `./gradlew` tasks, kept for consistency with
CI. Use whichever is convenient.

## Pull Requests

Use the PR template. Include what changed, why, and how it was validated.

Avoid committing local state such as build outputs, `local.properties`,
`keystore.properties`, `.idea/`, and `.context/`.

## Editing The Emotion Tree

`app/src/main/assets/emotion_tree.json` is meant to be edited directly — see
the README's "The emotion tree" section and `DECISIONS.md`. Run
`./gradlew testDebugUnitTest` after any edit; `EmotionTreeTest` validates the
file's shape (unique ids, non-blank labels, parseable colors).

## Agent Notes

- Keep convention changes paired with docs updates (`docs/`, `README.md`).
- Validate with `./gradlew check` before treating a change as complete.
