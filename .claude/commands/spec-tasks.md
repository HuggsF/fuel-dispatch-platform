---
description: Break an approved design.md into small, test-first tasks
argument-hint: <phase number, e.g. 03>
---

Phase: $ARGUMENTS

1. Read `specs/$ARGUMENTS-*/requirements.md` and `design.md` (must be approved).
2. Write or update `tasks.md` using `specs/_templates/tasks.md`:
   - Each task is small enough for one commit (≈ 30–90 minutes of work).
   - Each task starts with the test to write, then the code.
   - Each task lists the requirement IDs it satisfies (`_Req: DOM-1.1, DOM-1.2_`).
   - Every requirement ID is covered by at least one task.
   - The last task is always "verify phase": build green, acceptance criteria checked, docs updated.
3. Keep tasks that are already checked (`[x]`) untouched.
4. Summarize in Portuguese and wait for approval.
