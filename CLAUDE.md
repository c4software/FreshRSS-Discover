# CLAUDE.md

The development rules for this repository live in a single file, shared by every
agent and by human contributors alike:

👉 **[AGENTS.md](./AGENTS.md)** — working method, prohibitions, tests,
verification command, code and commit conventions.

Also read, in this order:

1. [SPECS.md](./SPECS.md) — the functional specification (the **what**)
2. [ARCHITECTURE.md](./ARCHITECTURE.md) — the technical architecture (the **how**)
3. [TASKS.md](./TASKS.md) — the roadmap and the actual progress (the **order**)

If the work touches the FreshRSS API — authentication, pagination, articles,
read status, marking, errors — **also** read
[docs/freshrss-api.md](./docs/freshrss-api.md), and §6 in particular:
**never invent the behaviour of an endpoint** (AGENTS.md §3).

## Harness commands

| Command | Role |
|---|---|
| `/status` | Where the project stands, and what is wrong |
| `/goal <objective>` | Break an objective down into tasks, then carry them out |
| `/task [GOAL-00X-TYY]` | Carry out a specific task, or the next one |
| `/verify` | Build, test, and confront TASKS.md with reality |

When arriving on the repository, start with `/status`.

## Points to watch

**One `TASKS.md` task at a time**, tests included, verification passed **and its
output seen**, then commit — before going any further.

`code written ≠ task finished`:

```
code written → tests → verification → documentation → TASKS.md = [x]
```

Never announce a success you have not observed. Never assume a `[-]` task is
finished: check it.

An optional local test stack — an emulator and a real FreshRSS instance — lives
in [envTest/](./envTest/README.md). **Shut it down at the end of every Goal**
with `./envTest/test-stack.sh stop` (AGENTS.md §5.3): stopping destroys nothing,
so there is no reason to leave four gigabytes running for the next task.
