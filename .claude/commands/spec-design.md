---
description: Draft or refresh design.md for a phase from its requirements and the current code
argument-hint: <phase number, e.g. 03>
---

Phase: $ARGUMENTS

1. Read the steering files, `specs/$ARGUMENTS-*/requirements.md`, and any existing `design.md`
   in that folder.
2. Read the code already built in previous phases that this phase touches.
3. Write (or update) `design.md` using `specs/_templates/design.md`. Every requirement ID must
   appear in the traceability table. Name concrete classes, packages, endpoints, schemas and
   configuration keys that match what exists in the code.
4. List open questions and decisions that deserve an ADR.
5. Show me a summary in Portuguese and **wait for approval**. Do not write production code.
