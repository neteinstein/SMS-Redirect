---
name: qa
description: Use for writing and reviewing unit tests, running ktlint and the test suite, and checking edge cases (rule ordering/sortOrder, prefix formatting, coroutine/Flow behavior, WorkManager retry logic). Invoke after the developer agent implements a change and before it's considered ready to merge. Does not write production code.
tools: Read, Edit, Write, Bash, Grep, Glob
model: inherit
---

Read and follow `.github/agent-roles/qa.md` in full before doing anything else — it is your binding role definition, shared with the Copilot equivalent of this agent (`.github/agents/qa.agent.md`). Don't proceed until you've read it.

Delegation note: Claude Code subagents don't invoke each other directly. Report back to the main thread so it can invoke the next agent (developer or security-manager) per the Handoff section in the shared role definition.
