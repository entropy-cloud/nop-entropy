# AGENTS.md - Nop Entropy Development Guide

This file is the **project-local** quick reference for AI assistants contributing to this repository.

## Where to read first

- **Check skills first**: Before exploring code or implementing solutions, check if an existing skill can solve the problem directly. Skills provide curated best practices and commands.
- Primary index: `docs-for-ai/INDEX.md`
- AI development guide: `docs-for-ai/01-core-concepts/ai-development.md`
- Coding conventions: `docs-for-ai/07-best-practices/code-style.md`
- Error handling: `docs-for-ai/07-best-practices/error-handling.md`
- Testing: `docs-for-ai/07-best-practices/testing.md`
- Task-based runbook: `docs-for-ai/12-tasks/README.md`

## Git Workflow

**IMPORTANT**: Commit frequently to avoid losing work.

- After completing a significant feature or fix, commit immediately
- Each commit should represent a logical unit of work
- Use clear, descriptive commit messages
- Never let uncommitted changes accumulate across multiple features

## Nop IoC vs Spring (high-impact differences)

- **Field injection visibility**: NopIoC does **not** support injecting into `private` fields.
	- ✅ Prefer `protected` or package-private fields when using `@Inject`.
	- ✅ Prefer **setter injection** when you want explicit dependencies but don't want constructor-based wiring.
	- ❌ Avoid examples like `@Inject private Foo foo;` in code and documentation.

- **Value/config injection**: inject configuration values with `@InjectValue` (avoid Spring-only patterns like `@Value`).

- **AOP usage**: Do not assume Spring AOP patterns (`@Aspect`, `@Around`) work in Nop. Verify actual implementations exist in codebase before documenting or using them. Nop uses source-code generated AOP, not runtime bytecode manipulation.

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
  - **Package names**: modules use `io.nop.<module-name>` (e.g., `io.nop.ai.core`, `io.nop.ai.shell`)
  - Avoid noisy refactors; keep diffs minimal and focused

## Error handling essentials

- Prefer `NopException` + ErrorCode for business errors
- Include parameters via `.param(...)` and keep the original cause
- Log with SLF4J (no `System.out`/`System.err`)
- Do **not** hardcode Chinese error messages in code; use error codes + parameters (i18n-ready)


## Quick lookup

| Topic | Doc |
|------|-----|
| AI development conventions | `docs-for-ai/01-core-concepts/ai-development.md` |
| AI developer guide | `docs-for-ai/01-core-concepts/ai-developer-guide.md` |
| Service layer | `docs-for-ai/03-development-guide/service-layer.md` |
| CRUD | `docs-for-ai/03-development-guide/crud-development.md` |
| Data access | `docs-for-ai/03-development-guide/data-access.md` |
| Transactions | `docs-for-ai/04-core-components/transaction.md` |
| Exception handling | `docs-for-ai/04-core-components/exception-handling.md` |
| GraphQL | `docs-for-ai/03-development-guide/api-development.md` |
| API quick reference | `docs-for-ai/09-quick-reference/api-reference.md` |
| Task-based runbook | `docs-for-ai/12-tasks/README.md` |
| Project structure | `docs-for-ai/03-development-guide/project-structure.md` |
| ORM SQLLib | `docs-for-ai/03-development-guide/orm-sqllib.md` |
| ORM advanced features | `docs-for-ai/03-development-guide/orm-advanced-features.md` |
| AutoTest | `docs-for-ai/11-test-and-debug/autotest-guide.md` |
