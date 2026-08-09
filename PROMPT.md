# PROMPT.md — Initialisation prompt (frozen)

> **This file does not get updated.** It preserves the project's initial intent,
> as it was formulated before a single line was written.
>
> It served once, to create the Harness (Phase 0). After that, agents work from
> [AGENTS.md](./AGENTS.md), [SPECS.md](./SPECS.md),
> [ARCHITECTURE.md](./ARCHITECTURE.md) and [TASKS.md](./TASKS.md).
>
> **Where an applicable rule has superseded this text on some point, it is
> [AGENTS.md](./AGENTS.md) that is authoritative.** The observed divergences are
> listed at the end of the file.

---

## Role

You are the agent responsible for the **initialisation of the FreshRSS Discover
project and of its development Harness for Claude Code**.

This step constitutes **Phase 0 — Harness / Initialisation**.

The aim is not solely to create the application, but to put in place an
environment that then allows several Claude Code agents to develop the project
incrementally, autonomously and reproducibly.

The Harness must make it possible to turn a high-level objective into a series
of executable, validated and traceable tasks.

**Do not begin the full implementation of the application during this step.**

## 1. Project

**FreshRSS Discover** is a native Android application acting as a client for a
FreshRSS server.

The application retrieves the user's RSS articles through the FreshRSS API and
presents them in a vertical feed inspired by the principle of **Google Discover
/ Google Feed**.

The intended experience is:

```
FreshRSS → Articles from the various feeds → Source interleaving
→ Continuous vertical feed → The user scrolls
→ Articles seen long enough become read
→ New articles are loaded
```

Main planned features: connection to a FreshRSS server; authentication through
the API; retrieval of subscriptions; retrieval of articles; interleaving of
articles coming from the various feeds; infinite vertical feed; pagination;
automatic marking as read once an article has been visible long enough; read
status synchronisation; *Pull to Refresh*; retrieval of new articles; opening
the original article; local cache and network resilience; a modern Android
interface.

## 2. FreshRSS documentation

The official documentation on mobile access is the reference:
<https://freshrss.github.io/FreshRSS/fr/users/06_Mobile_access.html>

The integration must use the **Google Reader compatible API**, at
`https://<server>/api/greader.php`.

`POST /api/greader.php/accounts/ClientLogin` authenticates. The password used
must be the **API password**, distinct from the main password. Authenticated
requests use `Authorization: GoogleLogin auth=<auth>`.
`GET /api/greader.php/reader/api/0/token` provides the modification token.

Endpoints to study precisely before implementation:

```
GET /reader/api/0/subscription/list?output=json
GET /reader/api/0/unread-count?output=json
GET /reader/api/0/tag/list?output=json
GET /reader/api/0/stream/contents/reading-list
```

The documentation must be consulted before any decision concerning:
authentication, pagination, article retrieval, retrieval of new articles, read
status, marking as read, the modification token, error handling.

**Never invent the behaviour of an endpoint.** Undocumented points must be
identified as such and validated before implementation.

## 3. Repository structure

```
PROMPT.md · SPECS.md · AGENTS.md · ARCHITECTURE.md
TASKS.md · CONTRIBUTING.md · README.md

.claude/commands/{goal,task,status,verify}.md
```

The root files constitute the project's persistent memory; the Claude Code
commands constitute the interface for driving it.

## 4. Role of the files

| File | Role |
|---|---|
| `PROMPT.md` | Initialisation prompt, used once only |
| `SPECS.md` | Functional source of truth — **what the application must do** |
| `AGENTS.md` | Source of truth for the rules — **how agents must work** |
| `ARCHITECTURE.md` | Technical source of truth — **how the application is designed** |
| `TASKS.md` | State of the work — **what must be done, is in progress, is finished** |
| `CONTRIBUTING.md` | How to contribute |
| `README.md` | General documentation |

## 5 to 8. The Harness and the `/goal` command

`/goal` receives a high-level objective and turns it into achievable tasks:

```
Goal → Analysis → Breakdown → Plan → TASKS.md
     → Execution → Validation → Documentation
```

**Step 1 — Understand the context.** Read `AGENTS.md`, `SPECS.md`,
`ARCHITECTURE.md`, `TASKS.md` — this is mandatory — then only the code files
that are needed. Do not modify the code immediately.

**Step 2 — Check the dependencies.** Determine what already exists, what is
missing, the tasks concerned, the architecture constraints, the existing tests.
Do not recreate an existing feature.

**Step 3 — Break it down.** Turn the objective into tasks small enough to be
carried out and validated independently. Avoid vague tasks of the kind "Do the
API", "Do the interface", "Finish authentication".

**Step 4 — Plan before execution.** Present the Goal, the plan, the files
concerned and the validation, then start. The Harness favours autonomy: only ask
a question if the decision cannot be derived from `SPECS.md`, `ARCHITECTURE.md`,
`AGENTS.md`, the state of the code or the conventions.

## 9 to 12. Execution, TASKS.md, identifiers

Once the plan is established: take the first unfinished task, implement it, run
the validations, fix, mark it finished, move on to the next. Do not modify the
repository massively without intermediate validation.

States: `[ ]` TODO · `[-]` IN PROGRESS · `[x]` DONE · `[!]` BLOCKED.

Every Goal has a stable identifier (`GOAL-001`, `GOAL-002`, …), and so does
every task (`GOAL-002-T01`), so that they can be referenced in commits.

Before creating a Goal, `/goal` checks `TASKS.md`: if an identical or very close
objective exists, do not create a duplicate — offer to resume the existing one.

## 13 to 15. The other commands

- **`/task [ID]`** — work on a specific task; without an identifier, select the
  next relevant task.
- **`/status`** — a synthetic view derived from `TASKS.md` **and from the actual
  state of the repository**.
- **`/verify`** — build, test, analyse, check the important files, the Git
  changes, the obvious errors, and that the tasks declared `DONE` are really
  validated. Result as `PASS` / `WARN` / `FAIL`. A task whose validation fails
  is not `DONE`.

## 16. Fundamental rule

The Harness must never consider that `code written = task finished`.

```
code written → tests → validation → documentation → TASKS.md = DONE
```

## 17. Resuming after an interruption

The system must allow an agent to be interrupted at any moment. On restarting:
read `AGENTS.md`, read `TASKS.md`, identify the `IN PROGRESS` tasks, check the
actual state of the code, resume the task.

**Never automatically consider an `IN PROGRESS` task to be finished.**

## 18. Detecting inconsistencies

`TASKS.md` says `DONE` but the code does not compile; `TASKS.md` says `TODO` but
the feature exists; `ARCHITECTURE.md` describes A but the code does B.

In those cases: identify the inconsistency, do not hide it, fix whichever side
is wrong, report the decision in the report.

## 19. Updating the documentation

New feature → `SPECS.md`, `ARCHITECTURE.md`, `TASKS.md`.
Architectural change → `ARCHITECTURE.md`, **mandatorily**.
New rule → `AGENTS.md`. Contribution procedure → `CONTRIBUTING.md`.

## 20. FreshRSS-specific Goals

For a Goal touching the API: consult `SPECS.md`, the FreshRSS section of
`ARCHITECTURE.md`, the official FreshRSS documentation, check the current
implementation, determine the endpoints needed, identify the parameters actually
supported, implement, test, document.

**Never infer the API's behaviour solely from an existing implementation.**

## 21. Architecture of the FreshRSS client

```
UI → ViewModel → Use Case → Repository → FreshRssApi → HTTP → FreshRSS
```

The following details stay confined to the FreshRSS layer: `ClientLogin`,
`Auth`, the modification token, headers, endpoints, response formats, handling
of specific HTTP errors.

## 22 to 23. Phase 0 and its checklist

Create the seven root files and the four commands; document the FreshRSS API
from its official documentation; identify the points requiring validation; check
the consistency of the documents and of the Harness.

## 24. What must NOT be done during this phase

Do not implement: FreshRSS authentication, article retrieval, pagination, the
Discover feed, automatic marking as read, status synchronisation, *Pull to
Refresh*, local cache, the Settings screen.

The Harness must be ready to receive them; they will be built by the following
Goals.

## 25. Success criterion

Phase 0 is a success when a new Claude Code agent can arrive in the repository,
run `/status` then `/goal Implement FreshRSS authentication`, and automatically
obtain: context analysis → plan → breakdown into tasks → update of `TASKS.md` →
implementation → tests → validation → documentation → Goal finished, **without
needing the whole project context to be given to it again**.

## 26. End of the initialisation

When Phase 0 is finished, **do not automatically begin Phase 1**. Provide a
report stating: the files created, the files modified, the architecture chosen,
the FreshRSS documentation studied, the commands created, the Goals initially
defined, the blocking points or remaining decisions.

The repository must be left in a clean state, directly usable by the Harness.

---

## Accepted divergences

What the implementation did differently from this text, and why. The applicable
rule is the one in [AGENTS.md](./AGENTS.md).

| Point of the prompt | What was done | Reason |
|---|---|---|
| §9, §16: validation at every step | Tasks follow one another without asking for approval; stopping is governed by AGENTS.md §1.2 | One validation per task would make autonomous progress impossible. The granularity of review is the **commit**, which stays reversible |
| §2: the official documentation as reference | The documentation establishes usage, but **`p/api/greader.php` is authoritative** on parameters and response shapes | The official documentation details neither the pagination parameters nor the JSON returned. Sticking to it would have forced guessing — which §2 forbids elsewhere |
| §3: repository structure | Addition of `docs/freshrss-api.md` and of `CLAUDE.md` | The API survey is too bulky for `ARCHITECTURE.md` and is updated at a different pace |
| §24: implement nothing | An executable skeleton was delivered: theme, navigation, `PlaceholderScreen`, Roborazzi pipeline | Without it, `/verify` would have nothing to verify and the Harness would be unverifiable. None of the features in the §24 list was written |
| §22: documentation structure only | The repository starts from an existing Android template, stripped of its business logic | To provide an architecture, quality tooling and CI that are proven rather than rebuilt |
