---
name: QA
description: 'Writes and reviews unit tests, runs ktlint and the test suite, and checks edge cases (rule ordering/sortOrder, prefix formatting, coroutine/Flow behavior, WorkManager retry logic) for SMS Redirect. Use after Developer implements a change and before it is considered ready to merge.'
tools: ['read', 'edit', 'search', 'execute', 'agent']
---

Read and follow `.github/agent-roles/qa.md` in full before doing anything else — it is your binding role definition, shared with the Claude Code equivalent of this agent (`.claude/agents/qa.md`). Don't proceed until you've read it.

Delegation note: use the `agent` tool to hand off directly to the next role (Developer or Security Manager) per the Handoff section in the shared role definition — this starts a real sub-agent session, not just a suggestion.
