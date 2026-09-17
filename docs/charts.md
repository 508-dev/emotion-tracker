# Charts

Open **Charts** in the hamburger menu, then **Emotion flow**. The chart shows
all saved journal entries, including CSV imports. Each entry contributes once
to every emotion on the path from Positive/Negative to the saved emotion.
Entries saved at an intermediate emotion stop at that node. Tap labels for
counts, including how many entries ended there.

Paths are reconstructed from the current emotion tree using stable ids and
actual parent/child relationships. They are not historical tap logs:
backtracking and abandoned selections are not recorded. Moving an emotion
in the JSON moves its existing entries' paths in the chart. Removed ids are
excluded from the bands, with an explicit count shown above the chart.

The chart updates from the journal's Room flow, including after imports or
deleting entries. It has no new dependencies or database schema changes.

## Adding another chart

Add a catalog item to `ui/charts/ChartsScreen.kt` and a `charts/...` route in
`ui/navigation/AppNavHost.kt`. All chart routes keep Charts selected in the
drawer. Keep chart aggregation and geometry independent of Compose, as in
`EmotionFlow.kt`, so counts and layout invariants can be tested on the JVM.

`SankeyChart` draws ribbons with Compose Canvas and exposes labels as regular
Compose elements for accessibility. Every ribbon shares the same count-to-
height scale; extra space around small nodes keeps their labels readable
without inflating their counts. A single two-dimensional scroll handler allows
horizontal, vertical, and diagonal drags and flings across the full diagram.
The axis scroll states retain measurement, bounds, and saved positions; their
individual gesture handlers are disabled to avoid locking a drag to one axis.
Labels and detail dialogs provide exact counts even when
a rare path is too thin to see clearly.

Run `./gradlew testDebugUnitTest ktlintCheck` for aggregation/layout tests and
formatting, and `./gradlew check assembleDebug` before opening a PR.
