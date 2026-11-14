# AI usage

I used Codex as an AI coding assistant during this time-boxed assessment.

## Prompts / assistance requested

- Translate the assignment into a minimal, testable Spring Boot vertical slice.
- Propose a state-machine design rather than distributing transition checks across controllers.
- Review validation/risk-factor logic and suggest a concise test matrix.
- Draft documentation structure and identify security-sensitive areas: masking, role/ownership checks and append-only audit events.
- Implement the Redis sorted-set/Lua indexing boundary and the React review-queue shell, then explain the changes for interview preparation.

## My review and decisions

I reviewed every generated line, corrected a syntax error in the entity replacement code, adjusted an initially incorrect risk-boundary test (a 20-point medium-risk industry is still LOW), and verified the Maven test command locally. I chose a Lua script for the Redis adapter so pruning, matching and insertion form one atomic Redis operation. I kept the deterministic in-memory adapter as the default profile so backend tests do not require Docker.

## Known limitations

The Redis implementation has been compiled and the backend test suite passes, but it has not been exercised against a live Redis container in this environment. The React review queue is implemented as a functional shell but has no standalone frontend test suite. The default test profile uses H2 in PostgreSQL compatibility mode to keep `mvn test` independent of Docker. Production would use PostgreSQL and the supplied Flyway migration.
