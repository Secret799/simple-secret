# Simple Secret Agent Entry

Read `AGENT.md` completely before changing this repository. It is the canonical source for project architecture,
build, testing, documentation, and security rules.

## Agent Harness

Use the installed `agent-harness` skill for every implementation or code-change request. Discussion, explanation, and
unrelated read-only inspection do not require a Harness task.

Resolve the Harness control root before editing:

1. Prefer the `HARNESS_ROOT` environment variable when it points to a directory containing both `harness.json` and
   `bin/harnessctl`.
2. Otherwise, resolve the real path of the installed `$HOME/.agents/skills/agent-harness/SKILL.md`. When that skill is
   linked from a Harness checkout, use the checkout containing `harness.json` and `bin/harnessctl`.
3. If neither method resolves a valid control root, stop and ask for its location instead of editing unmanaged.

After resolving the control root, follow the installed skill's lifecycle. In particular, run `harnessctl current
--json`, reuse only a matching task, create and claim a dedicated branch and worktree when needed, capture a baseline
snapshot, and load the generated role context before changing files. This repository is registered as
`simple-secret` in its shared Harness instance.

Repository registration is only a reusable name-to-path mapping; it is not an active task or background trigger.

## Environment Setup

The user or environment administrator performs this one-time setup outside task branches:

```bash
export HARNESS_ROOT=/path/to/AgentHarness
mkdir -p "$HOME/.agents/skills"
ln -s "$HARNESS_ROOT/skills/agent-harness" "$HOME/.agents/skills/agent-harness"
```

Do not replace an existing skill path or persist environment changes without explicit user authorization.
