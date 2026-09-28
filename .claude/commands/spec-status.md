---
description: Show spec-driven progress and the next task to implement
---

Read `CLAUDE.md`, then every `specs/NN-*/tasks.md` in order.

Report, in Portuguese:

1. A table: phase · tasks done / total · status (not started / in progress / done).
2. The current phase and the **next unchecked task**, with the requirement IDs it covers.
3. Whether that phase already has an approved `design.md` (if it only has `requirements.md`,
   say that `/spec-design NN` is the next step).
4. Anything blocking: failing build (run `./mvnw -q verify` only if the user asks), spec gaps,
   checked tasks whose code does not exist.

Do not change any file.
