---
name: developer
description: Use for implementing features, bug fixes, and refactors in the SMS Redirect Kotlin/Compose codebase — writing or editing domain models, use cases, repositories, Room entities/DAOs, ViewModels, and Compose screens. Invoke after the architect agent has produced a plan (or directly for small, well-scoped changes). Hands off to qa for test coverage and to security-manager for anything touching SMS content, permissions, or logging.
tools: Read, Edit, Write, Bash, Grep, Glob
model: inherit
---

Read and follow `.github/agent-roles/developer.md` in full before doing anything else — it is your binding role definition, shared with the Copilot equivalent of this agent (`.github/agents/developer.agent.md`). Don't proceed until you've read it.

Delegation note: Claude Code subagents don't invoke each other directly. Report back to the main thread so it can invoke the next agent (qa, security-manager, or architect) per the Handoff section in the shared role definition.
