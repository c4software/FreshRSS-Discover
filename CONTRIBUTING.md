# CONTRIBUTING.md — Contributing

This document describes the **procedure**. The **rules** are in
[AGENTS.md](./AGENTS.md), and they apply to humans and agents alike: there are
not two levels of requirement.

---

## 1. Before writing a line

Read, in this order:

1. [AGENTS.md](./AGENTS.md) — the working method and the prohibitions
2. [SPECS.md](./SPECS.md) — what the application must do
3. [ARCHITECTURE.md](./ARCHITECTURE.md) — how it is designed
4. [TASKS.md](./TASKS.md) — where the work stopped

If your contribution touches the FreshRSS API, **also** read
[docs/freshrss-api.md](./docs/freshrss-api.md).

## 2. Preparing your machine

```bash
export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
```

**Never** modify the `PATH` to put the JDK in it: see AGENTS.md §5.

Claude Code users have nothing to export — those variables are in
`.claude/settings.local.json`, unversioned because the paths depend on the
machine.

## 3. Choosing what to work on

Every contribution corresponds to a **task in [TASKS.md](./TASKS.md)**.

- The task exists → take it, and move it to `[-]`.
- It does not exist → add it first, under the Goal that carries it. If no Goal
  carries it, open one.

This detour is not bureaucracy: it is what lets a later contributor — human or
agent — pick the work up without you.

## 4. Writing

**One task, one commit.** The code and its tests in the same increment.

Reminders that are expensive to forget:

- nothing Android in `:domain` — that is a compilation error, not a review
  remark;
- no FreshRSS API detail above the `data` layer;
- never guess an endpoint's behaviour: read the source, observe, then **update
  `docs/freshrss-api.md`**;
- no `TODO` without a matching task;
- every displayed string is a resource.

Automatic formatting:

```bash
./gradlew ktlintFormat
```

## 5. Verifying

The verification command and its rules are in
[AGENTS.md §5](./AGENTS.md) — **they have only one home**, and it is not here.
Copying them across would create a second place to change, and so guarantee that
one of the two drifts.

What this document adds, because it is procedure rather than rule: run it
**before every commit**, not just before the Pull Request. A batch of ten
commits where you discover at the end that the third one breaks verification is
a batch that has to be taken apart.

## 6. Committing

[Conventional Commits](https://www.conventionalcommits.org/), with the task
reference. Commit messages are written **in French** (AGENTS.md §9), hence the
example below:

```
feat(auth): implémenter ClientLogin contre l'API FreshRSS

Le jeton FreshRSS est déterministe et n'expire pas : il est donc conservé
entre deux lancements plutôt que redemandé à chaque démarrage.

Réf: GOAL-002-T05
```

The body explains **why**, never **what** — the diff already says the what.

Never commit: `local.properties`, `.claude/settings.local.json`, a keystore, a
key, a token.

## 7. Proposing

One Pull Request per task, or per Goal if the tasks are inseparable.

The template ([`.github/pull_request_template.md`](.github/pull_request_template.md))
asks in particular for confirmation that verification passed **and that its
output was seen**. This is not a formality: a box ticked without observation is
the one thing CI cannot catch.

`git push` is not in the shared permissions of `.claude/settings.json`: an
outgoing action is confirmed explicitly.

## 8. Updating the documentation

It is part of the contribution, not of its aftermath. The "which change goes in
which file" table is in [AGENTS.md §6](./AGENTS.md), for the same reason as in
point 5: a single home.

One thing only to remember here: [PROMPT.md](./PROMPT.md) is **frozen**. It
preserves the initial intent and is never updated, even once that intent has
been overtaken — the divergences are recorded at the end of the file.

## 9. If you are stuck

Say so, in `TASKS.md`, by moving the task to `[!]` **with the reason written
right below it**. An unwritten blocker is a lost blocker, and the next
contributor will rediscover it at their own expense.

See AGENTS.md §10 for the common cases.
