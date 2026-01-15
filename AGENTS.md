<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# AGENTS.md - Nop Entropy Development Guide

This file is the **project-local** quick reference for AI assistants contributing to this repository.

For architecture changes, proposals, or ambiguous requests, follow the OPENSPEC instructions above.

## Where to read first

- Primary index: `docs-for-ai/INDEX.md`
- Coding conventions: `docs-for-ai/best-practices/code-style.md`
- Error handling: `docs-for-ai/best-practices/error-handling.md`
- Testing: `docs-for-ai/best-practices/testing.md`

## Nop IoC vs Spring (high-impact differences)

- **Field injection visibility**: NopIoC does **not** support injecting into `private` fields.
	- ✅ Prefer `protected` or package-private fields when using `@Inject`.
	- ✅ Prefer **setter injection** when you want explicit dependencies but don't want constructor-based wiring.
	- ❌ Avoid examples like `@Inject private Foo foo;` in code and documentation.

- **Value/config injection**: inject configuration values with `@InjectValue` (avoid Spring-only patterns like `@Value`).

## Build & test

This repository is Maven-based (see `pom.xml`).

### Full build (recommended)

Windows PowerShell:

```powershell
mvn clean install -T 1C
```

### Quick build without tests

```powershell
mvn clean install -DskipTests -T 1C
```

### Run tests

```powershell
mvn test
```

## Quality gates (before you finish)

1. Build: `mvn -q -DskipTests=false test` (or full `clean install` if you touched multiple modules)
2. Check style: follow `checkstyle.xml` and keep imports grouped (java.* → jakarta.* → third-party → io.nop.*)
3. Tests: add/extend JUnit 5 tests; use Nop AutoTest where the project already uses it

## Code style essentials

- **Naming**: PascalCase (classes), camelCase (methods/variables), UPPER_SNAKE_CASE (constants)
- **Formatting**: 4-space indentation, keep lines ~80–120 chars where reasonable
- **Imports**: grouped and stable ordering: java.* → jakarta.* → third-party → io.nop.*
- Avoid noisy refactors; keep diffs minimal and focused

## Error handling essentials

- Prefer `NopException` + ErrorCode for business errors
- Include parameters via `.param(...)` and keep the original cause
- Log with SLF4J (no `System.out`/`System.err`)
- Do **not** hardcode Chinese error messages in code; use error codes + parameters (i18n-ready)


## Quick lookup

| Topic | Doc |
|------|-----|
| AI development conventions | `docs-for-ai/getting-started/ai/nop-ai-development.md` |
| Service layer | `docs-for-ai/getting-started/service/service-layer-development.md` |
| CRUD | `docs-for-ai/getting-started/business/crud-development.md` |
| Data access | `docs-for-ai/getting-started/dao/entitydao-usage.md` |
| Transactions | `docs-for-ai/getting-started/core/transaction-guide.md` |
| GraphQL | `docs-for-ai/getting-started/api/graphql-guide.md` |
| API quick reference | `docs-for-ai/quick-reference/api-quick-reference.md` |
