---
name: tdd
description: Deep test-driven development guide for complex test-first design, choosing new public seams, or integration test strategy. Routine single-seam tests are already covered by AGENTS.md.
---

# Test-Driven Development

TDD is the red → green loop. Every section applies on each cycle.

## What a good test is

Tests verify observable behavior through public interfaces, not implementation details. A good test reads like a specification and survives internal refactors.

See [tests.md](tests.md) for good/bad test patterns and [mocking.md](mocking.md) for boundary mocking guidelines (only read if needed).

## Seams: where tests go

A **seam** is the public boundary you test at without reaching inside.

- Name the seam in a line before writing the test (what it catches and what it misses).
- For established seams (listed in `AGENTS.md`), proceed directly. Pause to confirm with the user only when introducing a new public interface or when the right seam is ambiguous.

## Anti-patterns

- **Implementation-coupled**: mocks internal collaborators, tests private methods, or verifies through a side channel (querying the DB directly instead of using the interface). Breaks on refactors when behavior has not changed.
- **Tautological**: assertions recompute the expected value using the same logic as the code under test, so they pass by construction. Expected values must come from an independent source of truth: a known-good literal, a worked example, or the spec.
- **Horizontal slicing**: writing all tests first, then all implementation. Work in **vertical slices** instead: one test → one minimal implementation → repeat, letting each cycle inform the next.

## Rules of the loop

- **Red before green.** Write the failing test first, then only enough code to pass it. Do not add speculative features.
- **One slice at a time.** One seam, one test, one minimal implementation per cycle.
- **Refactor after green.** Keep refactoring separate from the red → green step.
