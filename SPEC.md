This repo is a fresh generation of our co-op's template repo, and needs setup.
The README will need adjustment to reflect the fact that this is an android app
and to reflect the spec, among other files that may need adjustment / deletion.
This spec file should be deleted once we hit MVP, as development will switch to
ticket-based.

## Objective

Our end goal is a FOSS, gpl-3 android app published on the f-droid store or
related foss store.

This app is an emotion tracking app. The objective is to allow a user to, as
quickly as possible upon launch, select and record against a day/time, the
emotion they just felt. They may record multiple emotions per day, or even
record multiple one after another to describe a more complex emotional state.
Later, they can review their emotions on a timeline.

## The emotion wheel

The emotion recording UI is represented by a wheel similar to a color wheel. It
has escalating 'stages'. On open, the user is presented with a large wheel with
two halves: "Positive" and "Negative." On tapping, an animation triggers which
causes the tapped side to encircle the outer edge of the circle, maintaining
color. For example, if the user taps "Positive," and Positive is blue, then, a
blue circle of a slightly larger width encircles the inner circle, and the
"Negative" half animates out.

At that point, the user is looking at a circle split into more than 2 slices
(imagine a pizza or cake), depending on the previously selected emotion. For
example, "Positive" might lead to a slightly more fine-grained set of emotions
such as "Calm," "Excited," each of which increases the granularity of the
emotion wheel. When the wheel has "bottomed out," the inner wheel simply becomes
"save." Meanwhile, the outer wheel has been stacking with the emotional states.

The entire time this is happening, the currently selected level is visible as a
large title above the wheel. To its right are two buttons: One for "Back" to go
up a level, and one for "Save" to record the current lowest-level emotional
state against the timeline. When this is selected, the user is shown some kind
of toast that the given emotion {{emotion}} was saved, and brought back to the
top-level emotion wheel.

There is a hamburger menu in the top left. The emotion wheel is the top item,
the second is the journal, the third is settings.

## The journal

The journal is a relatively simple vertically scrolling list of emotion entries,
grouped by day. Each emotional entry has an option to add a small text
annotation, which will then be displayed under the given emotion.

## Settings

There are only a few settings right now. The first is a button which says
"Export to CSV." It does nothing for now, other than saying "feature to come."
The second is "delete all entries." This triggers a warning that must be
dismissed, and then the button must be pressed twice more, to delete all
entries.

## Technical

The emotion tree should be in a dev-modify friendly format, such as a JSON file
or similar, rather than hidden away in a DB or something. It's fine if it's
encoded at build time or put into a database somehow, but during development it
should be easy to modify.

Since the emotion tree may get tweaked, we don't need to bother saving e.g. a
full tree path to a given emotion in a journal entry: the final emotion itself
is enough. We may want to record emotions as an ID-identified item rather than
as plain text. This will allow us to do mappings e.g. between colors and
emotions via ID. This consideration should be reflected in the dev-friendly JSON
or similar.

This is my first android app, please advise on tooling.
