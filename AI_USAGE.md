# AI usage

I used Codex as an AI coding assistant during this time-boxed assessment.

## Prompts / assistance requested

- Translate the assignment into a minimal, testable Spring Boot vertical slice.
- Propose a state-machine design rather than distributing transition checks across controllers.
- Review validation/risk-factor logic and suggest a concise test matrix.
- Draft documentation structure and identify security-sensitive areas: masking, role/ownership checks and append-only audit events.

## My review and decisions

I chose a deliberately narrow, working Task 1 rather than presenting untested Redis/React features. I reviewed every generated line, corrected a syntax error in the entity replacement code, adjusted an initially incorrect risk-boundary test (a 20-point medium-risk industry is still LOW), and verified the resulting Maven test command locally.

## Known limitations

The assignment is intentionally larger than a two-hour implementation. Redis link analysis and the React review UI are described as the next increments, not claimed as implemented. The default test profile uses H2 in PostgreSQL compatibility mode to keep `mvn test` independent of Docker. Production would use PostgreSQL and the supplied Flyway migration.
