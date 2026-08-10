# AGENTS.md — Development rules

The working contract for any agent (Claude Code, Codex, …) or human developer
acting on this repository. It overrides any personal habit.

Related documents: [SPECS.md](./SPECS.md) (the what) ·
[ARCHITECTURE.md](./ARCHITECTURE.md) (the how) · [TASKS.md](./TASKS.md)
(the order) · [docs/freshrss-api.md](./docs/freshrss-api.md) (the remote API) ·
[PROMPT.md](./PROMPT.md) (the initial intent, frozen).

---

## 1. Working method

Work is organised into **Goals**, themselves broken down into **tasks**.
Everything is recorded in [TASKS.md](./TASKS.md), which is the project's
persistent memory.

**One commit per task, in order. Tasks follow one another without asking for
approval.**

For each task:

1. State briefly the technical choice made, and why.
2. Move the task to `[-]` in TASKS.md.
3. Implement **that task only**.
4. Write the tests in the same increment as the code.
5. Run the full verification (§5) and **report the actual output**.
6. Update the affected documentation (§6).
7. Move the task to `[x]`, commit, then move on to the next one.

If a task turns out to be bigger than expected, split it across several commits —
but never merge two tasks into one.

The granularity is **the commit, not the conversation**: it is the commit that
makes the work reviewable and reversible step by step. That is what makes
autonomous progress safe.

### 1.1 The fundamental rule

Never consider that:

```
code written = task finished
```

The rule is:

```
code written → tests → verification → documentation → TASKS.md = [x]
```

A task whose verification fails is **not** `DONE`. A task declared `DONE`
without the verification output having been seen is a lie, and the next agent
will pay for it.

### 1.2 When to stop anyway

Automatic chaining does not excuse you from knowing when to break off. Four
cases, and only those:

- **Verification (§5) fails and fixing it calls for a judgement call** —
  lowering a version, relaxing a quality rule, giving up a test.
- **The specification is ambiguous on a business rule.** Never settle a
  user-visible behaviour silently.
- **An outgoing or hard-to-reverse action**: `git push`, publishing, history
  rewriting, deleting data.
- **A structural choice imposes itself** that would contradict
  [ARCHITECTURE.md](./ARCHITECTURE.md) or [SPECS.md](./SPECS.md).

Outside those cases: decide, document the decision in the commit message, and
carry on.

### 1.3 Resuming after an interruption

An agent can be stopped at any moment. On restarting:

1. read this file;
2. read [TASKS.md](./TASKS.md);
3. identify the `[-]` (IN PROGRESS) tasks;
4. **check the actual state of the code**, never assume that a `[-]` task is
   finished;
5. run the verification (§5) to see where the repository stands;
6. resume the task.

The `/status` command produces this reading automatically.

---

## 2. Prohibitions

- ❌ Delivering code that does not compile, or a feature without its tests.
- ❌ Declaring a task finished without having seen the verification output.
- ❌ Leaving dead code, an unused class, an ignored parameter.
  (No waiver in force; any exception is recorded in ARCHITECTURE.md §9.2 so as
  to be visible rather than tacit.)
- ❌ Writing a `TODO` without a matching task in [TASKS.md](./TASKS.md).
- ❌ Using a deprecated Android API.
- ❌ Importing `android.*`, `androidx.*`, Room, DataStore, Ktor, Hilt or Compose
  from `:domain`.
- ❌ Putting business logic in a ViewModel or a Composable.
- ❌ Letting a FreshRSS API detail (token, `continuation`, header, hexadecimal
  identifier) cross the `data` layer — see ARCHITECTURE.md §2.1.
- ❌ Creating a business singleton (a state-carrying `object`) — scope is
  declared to Hilt.
- ❌ Calling `System.currentTimeMillis()` anywhere other than in the `Clock`
  implementation.
- ❌ Referencing `kotlinx.coroutines.Dispatchers` anywhere other than in
  `DispatcherModule`.
- ❌ Introducing a dependency without a written justification in the commit
  message.
- ❌ Anticipating: do not create structure "for later". An abstraction arrives
  with its second use case, not before.

When choosing between several solutions, the order of preference is:
**simplicity → readability → testability → maintainability → official Android
API**.

---

## 3. The FreshRSS API

One rule, and it has no exception:

> **Never invent the behaviour of an endpoint.**

Before any decision touching authentication, pagination, article retrieval, read
status, marking or error handling:

1. read [docs/freshrss-api.md](./docs/freshrss-api.md);
2. if the point is not there, or is there as uncertain (§6 of that survey),
   **read the source** —
   [`p/api/greader.php`](https://github.com/FreshRSS/FreshRSS/blob/edge/p/api/greader.php)
   is authoritative on parameters and response shapes, the
   [official documentation](https://freshrss.github.io/FreshRSS/fr/users/06_Mobile_access.html)
   on the expected usage;
3. **update `docs/freshrss-api.md`** with what was observed.

**Never** infer the API's behaviour from an existing implementation in this
repository: that would cast an error in stone.

A point that remains uncertain after reading is **reported as such**, not
assumed. It joins §6 of `docs/freshrss-api.md` and a task in TASKS.md.

---

## 4. Tests

- **No feature without tests, in the same commit.**
- One test per behaviour, named after the observable behaviour.
- The domain is tested in pure JVM: no Robolectric, no emulator, no Android.
- Doubles are versioned **Fakes**, not generated mocks.
- Time and dispatchers are injected; tests use `kotlinx-coroutines-test` and its
  virtual scheduler. Never `Thread.sleep`.
- The API layer is tested with Ktor's `MockEngine`, against **literal** HTTP
  responses — including malformed, truncated, or plain text where JSON was
  expected.
- Coverage target: **~100 % on `:domain`**.

### 4.1 Visual rendering (Roborazzi)

Interface tests check **what is displayed**; screenshots check **what it looks
like**. A regression in layout, contrast or dark theme breaks no textual
assertion.

- The references live in `app/src/test/screenshots/`, **versioned**: a review
  must see the visual change in the diff, not merely read that a test failed.
- Every screen is captured in light **and** in dark. The dark theme is never the
  one you look at while developing: that is where contrast defects settle in
  unseen. This is not theoretical — Phase 0 shipped a black title on a black
  background, invisible otherwise.
- Dynamic colour is disabled and the screen format pinned
  (`@Config(qualifiers)`): without that, the reference would depend on the
  wallpaper or on Robolectric's default configuration.

```bash
./gradlew :app:verifyRoborazziDebug   # compare against the references
./gradlew :app:recordRoborazziDebug   # re-record after an intended change
```

⚠️ **These commands are not in the §5 verification, nor in CI.** Native graphics
rendering costs several minutes of machine time; paying it on every commit and
every Pull Request is not justified.

**A consequence to be owned:** a visual regression is caught automatically by
nobody. **Whoever touches the interface runs `verifyRoborazziDebug` before
committing** — that is the only net.

### A screenshot is only worth anything if it renders what the application renders

The screenshot harness wraps the content in a `Surface`. This is not a staging
detail: `Surface` installs `LocalContentColor`, and without it any text that
does not set its colour falls back to **black**.

In Phase 0, this defect showed up on a screenshot — a black title on a black
background — and it was "fixed" **where it was visible**, that is, in the
harness. The images became correct again; the application stayed at fault. The
defect was only found again several Goals later, on the first run on a device,
and by then it made **the whole login screen unreadable**.

The rule that follows: **when a screenshot reveals a defect, fix the
application, never the harness** — unless you can show that the harness departs
from what production does, and then bring it closer to production rather than
the other way round. A harness more lenient than the application turns a suite
of screenshots into scenery.

**Re-recording is not innocuous**: a `record` accepts every difference
wholesale, regressions included. Only run it after seeing that the visual change
is the one you wanted, and **look at the images** produced before committing.

An agent that touches the interface **actually looks at the screenshots** — it
has the means to — rather than merely observing that a Gradle task succeeded.

---

## 5. Verification

This repository builds with the JDK bundled with Android Studio. `gradlew` reads
`JAVA_HOME` first and only consults the `PATH` if it is absent: **setting
`JAVA_HOME` is enough, never tamper with the `PATH`.**

- **Claude Code agents** — nothing to do: `JAVA_HOME` and `ANDROID_HOME` are
  declared in `.claude/settings.local.json` (unversioned, because the paths
  depend on the machine). Run `./gradlew …` directly.
- **In an interactive shell**:

  ```bash
  export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
  export ANDROID_HOME=$HOME/Android/Sdk
  ```

⚠️ **Never** prefix a command with `export PATH="$JAVA_HOME/bin:$PATH"`. Since
the value is only resolved at execution time, the command becomes impossible to
match against a permission rule: the harness asks for confirmation on every
call, and no reusable rule can be recorded.

### 5.1 Writing commands that do not ask for confirmation again

A command can only be captured in a permission rule if its shape repeats. Four
habits are enough to avoid most of the prompts:

| Do | Rather than |
|---|---|
| Write files with the **Write** and **Edit** tools | `cat > file <<'EOF'`, `sed -i '…'`, `python3 - <<'PY'` |
| `git commit -m "…"` (the identity is in `.git/config`) | `git -c user.email=… -c user.name=… commit …` |
| A stable `grep` pattern, or reading the whole output | a `grep -E "…"` that differs on every call |
| A single verification command (below) | one-off variations of Gradle tasks |

The shared rules live in `.claude/settings.json`, versioned and free of
machine-specific paths. `git push` is **deliberately absent** from it: an
outgoing action gets confirmed.

### 5.2 The command

To be run **before any commit**:

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

That is exactly what `/verify` does.

`koverVerify` fails below 98 % coverage on `:domain`.

Automatic formatting fix:

```bash
./gradlew ktlintFormat
```

⚠️ **`ktlintFormat` only fixes `:domain`.** The ktlint-gradle plugin does not
discover AGP 9's Android source sets: on `:app`, it only registers a task for
`.kts` files. Style rules are applied there by **`detekt-formatting`**, which
reports them without fixing them.

Practical consequence: in `:app`, a style violation is repaired by hand. The
most frequent one is import ordering — lexicographic, with `java`, `javax`,
`kotlin` and the aliases at the end. See ARCHITECTURE.md §8.0 for the story of
this safeguard, which stayed empty for several Goals.

Nothing is declared finished without this command having been run **and its
output actually seen**. On failure, report the output; never announce a success
you have not observed.

### 5.3 The local test stack, and shutting it down

An emulator and a real FreshRSS instance can be raised on the development
machine in one command. It is **optional** — nothing in §5.2 depends on it, and
the CI ignores it — but it is the only thing that exercises what lies below the
transport layer. See [envTest/README.md](./envTest/README.md), which also
records the defect its very first run uncovered: `http://` was promised by
SPECS.md §3.1 and refused by the manifest, for fourteen Goals.

```bash
./envTest/test-stack.sh init   # once
./envTest/test-stack.sh run    # afterwards
./envTest/test-stack.sh stop   # at the end of every Goal
```

> **Always shut the stack down at the end of a Goal.** An emulator holds four
> gigabytes of memory and a core, and a container holds a port; left running,
> they are paid for by every task that follows, including those that never
> needed them. `stop` shuts down without destroying: the AVD, the container,
> the user, the feeds and the accumulated read state all survive, and `run`
> finds them again. Shutting down is therefore never a loss — which is exactly
> why there is no excuse for skipping it.

### 5.4 Definition of "finished"

- [ ] `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug` passes.
- [ ] If the interface changed: `./gradlew :app:verifyRoborazziDebug` passes, or
      the references have been re-recorded **and looked at**.
- [ ] The tests cover the added behaviour, including its edge cases.
- [ ] No dead code, no orphan `TODO`.
- [ ] [ARCHITECTURE.md](./ARCHITECTURE.md) §9 remains accurate — it describes
      **packages and their role**, so it is only updated if the architecture
      changes, not on every file added.
- [ ] The matching checkbox in [TASKS.md](./TASKS.md) is ticked.
- [ ] The commit follows §7.

---

## 6. Documentation

Documentation is part of the task, not of its aftermath.

| Change | File to update |
|---|---|
| New user-visible behaviour | [SPECS.md](./SPECS.md) |
| Architecture decision, dependency, splitting | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| New package, or package whose role changes | [ARCHITECTURE.md](./ARCHITECTURE.md) §9 |
| An observation about the FreshRSS API | [docs/freshrss-api.md](./docs/freshrss-api.md) |
| New development rule | this file |
| Contribution procedure | [CONTRIBUTING.md](./CONTRIBUTING.md) |
| Progress, new task, blocker | [TASKS.md](./TASKS.md) |

[PROMPT.md](./PROMPT.md) is **frozen**: it preserves the initial intent and is
not updated. Where an applicable rule has superseded it on some point, it is
this file that is authoritative.

---

## 7. Git

**One commit = one coherent task.**
[Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <description in the imperative, lowercase>

<optional body: why, not what>

Réf: GOAL-00X-TYY
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`.

Usual scopes: `domain`, `data`, `api`, `auth`, `feed`, `discover`, `cache`,
`ui`, `di`, `settings`, `gradle`, `harness`.

Commit messages are written **in French** (§9): the examples below are therefore
in French, and stay that way.

```
feat(auth): implémenter ClientLogin contre l'API FreshRSS
test(api): couvrir la pagination par curseur, curseur invalide compris
docs(architecture): décrire la confinement des détails FreshRSS
```

Reference the task identifier (`GOAL-002-T03`) in the message footer: that is
what ties the Git history to TASKS.md.

Never commit: `local.properties`, `.claude/settings.local.json`, a keystore, a
key, a token, a build screenshot.

---

## 8. Detecting inconsistencies

The repository can end up in a contradictory state. Three common forms:

| Symptom | Source of truth |
|---|---|
| TASKS.md says `[x]`, the code does not compile | **The code.** Move the task back to `[-]` and fix it |
| TASKS.md says `[ ]`, the feature exists | **The code.** Tick it, after checking that it is tested |
| ARCHITECTURE.md describes A, the code does B | **ARCHITECTURE.md**, unless B is better — in which case update the document and say so |
| SPECS.md describes an absent behaviour | **SPECS.md.** That is a missing task |
| `docs/freshrss-api.md` contradicts the real server | **The server.** Fix the survey |

In every case:

1. identify the inconsistency;
2. **do not hide it**;
3. fix whichever side is wrong;
4. report the decision in the report and in the commit message.

---

## 9. Code conventions

Applied by ktlint, Detekt and `.editorconfig`.

### Formatting

- 4-space indentation (2 for XML, YAML, TOML, JSON).
- 120 columns maximum per line.
- **Trailing commas everywhere** on multi-line lists.
- Explicit imports, never a star.

### Naming

| Element | Convention | Example |
|---|---|---|
| Kotlin file | Name of the main declaration | `DiscoverViewModel.kt` |
| Class, interface, enum | `PascalCase` | `FreshRssApi` |
| Function, property | `camelCase` | `loadNextPage`, `isRead` |
| `@Composable` | `PascalCase` | `DiscoverScreen` |
| File constant | `private val PascalCase` at the top of the file | `private val CardHeight = 96.dp` |
| Test | descriptive `camelCase`, no backticks | `anAbsentContinuationEndsTheFeed` |
| Fake | `Fake` prefix | `FakeArticleRepository` |

### Code documentation

- KDoc **in English**, on what is not obvious: a choice, a constraint, a reason.
  No paraphrasing of the signature.
- A comment explains **why**, never **what**.
- **English is the language of KDoc and code comments; French stays the
  language of commit messages** (§9). A KDoc block or a code comment written in
  French is a deviation to be fixed.

An example of the expected style:

```kotlin
/**
 * A missing `continuation` is the only end-of-feed signal: the API returns no
 * total count. An invalid cursor, however, is silently reset to the start on
 * the server side — hence the explicit check.
 */
```

### Compose

- A public Composable takes `modifier: Modifier = Modifier` as its **first
  optional parameter**, after the mandatory ones.
- No computation in a Composable: it displays `UiState`, it does not derive it.
- Every screen has a private `@Preview` that works **without injection**.
- Recurring dimensions go through `Spacing`, not through scattered `.dp`.
- Every displayed string is a resource, never a literal.

---

## 10. What to do when stuck

- **The Android SDK is missing a platform** → install it via Android Studio's
  SDK Manager; do not work around it by silently lowering a version.
- **A dependency requires a higher `compileSdk`** → report it and offer the
  choice, rather than downgrading the dependency without saying so.
- **The specification is ambiguous** → ask the question. Do not settle a
  business rule silently.
- **The FreshRSS API's behaviour is uncertain** → §3. Read the source, observe,
  document. Never assume.
- **An abstraction resists** → say so. Working around an abstraction is a debt;
  fixing it is a step.
