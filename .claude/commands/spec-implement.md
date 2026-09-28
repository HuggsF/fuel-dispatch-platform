---
description: Implement the next unchecked task (or a given one), test first
argument-hint: [optional task id, e.g. 02.3]
---

Task: $ARGUMENTS (if empty, take the first unchecked task of the earliest unfinished phase)

1. Read `CLAUDE.md`, the steering files, and the phase's `requirements.md`, `design.md`, `tasks.md`.
2. Restate in Portuguese, in 2–3 lines, what the task delivers and which requirement IDs it covers.
3. Write the failing test(s) first. Run them and confirm they fail for the right reason.
4. Implement the minimum code to make them pass, respecting the hexagonal rules in `structure.md`.
5. Run `./mvnw verify` (or the module-scoped command if the full build is slow) until green.
6. Tick the task in `tasks.md`. If the design had to change, update `design.md` in the same change
   and tell me why.
7. Propose a Conventional Commit message listing the requirement IDs. Do not commit unless I ask.
8. Stop. Do not start the next task.

If the spec is ambiguous or wrong, stop at step 2 and propose the spec change instead.
