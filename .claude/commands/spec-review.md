---
description: Verify a finished phase against every acceptance criterion
argument-hint: <phase number, e.g. 02>
---

Phase: $ARGUMENTS

Act as a strict reviewer who did not write this code.

1. Run `./mvnw verify`.
2. For **each** acceptance criterion in `specs/$ARGUMENTS-*/requirements.md`, find the test(s)
   that prove it. Output a table in Portuguese: ID · criterion (short) · test(s) · status
   (covered / partially / missing).
3. Check the hexagonal rules (ArchUnit passing, no framework imports in `domain`).
4. Check `design.md` still matches the code; list drift.
5. Check README / ADRs mention what this phase added.
6. List concrete fixes, most important first. Do not fix anything unless I ask.
