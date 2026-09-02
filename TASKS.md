# TASKS.md — Roadmap and actual progress

The project's persistent memory. An arriving agent must be able to read this
single file and understand **where the work stopped**.

Related documents: [AGENTS.md](./AGENTS.md) (the rules) ·
[SPECS.md](./SPECS.md) (the what) · [ARCHITECTURE.md](./ARCHITECTURE.md) (the
how) · [TASKS.archive.md](./TASKS.archive.md) (the detail of the closed Goals).

---

## Conventions

| Mark | State |
|---|---|
| `[ ]` | TODO — not started |
| `[-]` | IN PROGRESS — started, **never assumed finished** |
| `[x]` | DONE — code **and** tests **and** verification observed |
| `[!]` | BLOCKED — the reason is written just below |

Identifiers: `GOAL-00X` for a Goal, `GOAL-00X-TYY` for a task. They are
**stable**: an abandoned task is struck through, never renumbered. Commit
messages reference them (AGENTS.md §7).

Reminder (AGENTS.md §1.1): `code written ≠ task finished`.

**Archiving is part of closing.** When a Goal turns entirely `[x]`, its detail —
tasks, decisions, debts — moves to the end of `TASKS.archive.md` (created at the
first closing), and only its row in the overview table stays here. Recorded
incidents follow the same path once their lesson is written into `AGENTS.md`.
That is what keeps this file short enough to be read at every session: the
useful memory is the state, not the history.

---

## Current phase

**Phase 0 — Harness** ✅ finished
**Phase 1 — FreshRSS API** ✅ finished (GOAL-002, GOAL-003)
**Phase 2 — Discover feed** ✅ assembled and delivered

**Phase 3 — Tuning the marking, sharing, English documentation** ✅ finished
(GOAL-019 to GOAL-028)

**The twenty-nine Goals are done.** GOAL-029 worked off the needless
complexity a four-part review had listed on 2026-08-10 — the List/Swipe
duplication above all, whose predicted divergence had already happened twice.
One point remains blocked, out of our hands:
`GOAL-001-T17` — AGP 9.3.1 still crashes on `lintAnalyzeDebugUnitTest`, retried
on 2026-08-08. It will be lifted by an AGP version, not by code from here.

**Next task**: none is due. GOAL-041 (the veil over a foreground reload)
closed on 2026-08-26. GOAL-040 (feed management from the settings)
closed on 2026-08-26, validated on the local stack. Waiting for the author: the device pass on
GOAL-041 (the veil after a 30-minute absence) and on
GOAL-039 (pull on page 1, tab re-tap). Still waiting for the author: the device
observation GOAL-035 left as debt (learned reminder hour, time picker,
statistics screen).

> **The device came back, and it changed what this phase could prove.** It was
> opened on the statement that no device was available, and it was closed with a
> local test stack — an emulator and a real FreshRSS instance
> ([envTest/](./envTest/README.md)) — then on the author's own Pixel 10 Pro.
> That was not a formality. **Six defects were found by running the thing**, and
> not one of them by a test: cleartext refused while SPECS.md §3.1 promised it
> (`GOAL-022-T01`), the share button not reaching the card edge
> (`GOAL-022-T03`), `init` never returning and `adb install` failing in silence
> (`GOAL-022-T02`), too much air under the card, and the double refresh
> (`GOAL-024`). The lesson of `GOAL-001-T22` held: what lives below the
> transport layer, or in the ordering of two calls, is only seen by executing.
>
> **GOAL-025 came from the same place**, without an emulator this time: read
> everything, reload, and the screen emptied itself into a dead end — no pull,
> nothing asking the server again. Reported in two sentences by the author. It
> is the seventh defect of this phase found by using the application — and it
> uncovered an eighth straight away, `GOAL-026`: the reload emptied the screen
> but not the cache, so killing the application resurrected the feed one had
> just exhausted. Neither would have been found by a test; both were found by
> the author, using it.
>
> Hence the rule now in AGENTS.md §5.3 — **shut the stack down at the end of
> every Goal**, since stopping destroys nothing.

---

## Overview

| Goal | Title | State |
|---|---|---|
| GOAL-001 | Harness and initialisation | `[x]` |
| GOAL-002 | FreshRSS authentication | `[x]` |
| GOAL-003 | Paginated article retrieval | `[x]` |
| GOAL-004 | Local cache and network resilience | `[x]` |
| GOAL-005 | Source interleaving | `[x]` |
| GOAL-006 | Discover feed — interface | `[x]` |
| GOAL-007 | Automatic marking as read | `[x]` |
| GOAL-008 | Read status synchronisation | `[x]` |
| GOAL-009 | Pull to refresh | `[x]` |
| GOAL-010 | Opening the original article | `[x]` |
| GOAL-011 | Settings screen | `[x]` |
| GOAL-012 | Swipe view, article by article | `[x]` |
| GOAL-013 | Reading reminder by local notification | `[x]` |
| GOAL-014 | Feed staleness notice | `[x]` |
| GOAL-015 | Quiet launch: cache only, no restore | `[x]` |
| GOAL-016 | Small illustrations stop being stretched | `[x]` |
| GOAL-017 | An already-read article shows it | `[x]` |
| GOAL-018 | CI stops running on deprecated actions | `[-]` |
| GOAL-019 | Automatic marking becomes optional | `[x]` |
| GOAL-020 | The card can be shared, the flag goes, the swipe opens on a tap | `[x]` |
| GOAL-021 | The documentation switches to English, the interface becomes bilingual | `[x]` |
| GOAL-022 | A local test stack, and the defects it revealed | `[x]` |
| GOAL-023 | The card tightens up: source and date in the footer, discreet sharing | `[x]` |
| GOAL-024 | Refreshing twice was needed to see new articles | `[x]` |
| GOAL-025 | An empty feed stops being a dead end | `[x]` |
| GOAL-026 | Killing the app resurrected the feed one had just emptied | `[x]` |
| GOAL-027 | The reload keeps what the server returned, not what looks unread | `[x]` |
| GOAL-028 | A page in flight no longer survives the reload that disowned it | `[x]` |
| GOAL-029 | The 2026-08-10 review: needless complexity is worked off | `[x]` |
| GOAL-030 | An unreachable server announces itself with a toast | `[x]` |
| GOAL-031 | Android 17 asks for the local network at launch | `[x]` |
| GOAL-032 | Read-on-scroll: observable at last, and a threshold that survives scrolling | `[x]` |
| GOAL-033 | A Play Store submission file, and the policy that publishes itself | `[x]` |
| GOAL-034 | Re-tapping the Discover tab returns to the top, then reloads | `[x]` |
| GOAL-035 | The reminder aims at the dominant reading hour, and a stats screen shows it | `[x]` |
| GOAL-036 | The reminder section reads as settings rows, not floating buttons | `[x]` |
| GOAL-037 | A recap of the feed, generated on the device | `[x]` |
| GOAL-038 | Swipe mode becomes a full-screen vertical scroll, TikTok-style | `[x]` |
| GOAL-039 | Immersive-mode reloading, the way short-video feeds do it | `[x]` |
| GOAL-040 | Minimal feed management from the settings | `[x]` |
| GOAL-041 | A foreground reload never shows the previous article again | `[x]` |
| GOAL-042 | Immersive reloading goes back to the List's rules | `[x]` |
| GOAL-043 | One live feed state for both modes | `[x]` |

The state carried here is that of the Goal's own section when it is still
detailed below; a Goal entirely `[x]` keeps only this row, and its detail lives
in [TASKS.archive.md](./TASKS.archive.md).

Goals are broken down into tasks by `/goal` at the moment of taking them on: breaking them down in advance would mean deciding without
knowing the state of the code (AGENTS.md §2, "do not anticipate").

---

## GOAL-001 — Harness and initialisation

**Status: DONE**

Setting up the repository, its documentation and the steering commands. No
application feature.

- [x] `GOAL-001-T01` Analyse the repository and the template
- [x] `GOAL-001-T02` Clone the template and strip out its business logic
- [x] `GOAL-001-T03` Rename project, package and identifiers
- [x] `GOAL-001-T04` Study the FreshRSS documentation and the `greader.php` source
- [x] `GOAL-001-T05` Write `docs/freshrss-api.md`
- [x] `GOAL-001-T06` Write `SPECS.md`
- [x] `GOAL-001-T07` Write `ARCHITECTURE.md`
- [x] `GOAL-001-T08` Write `AGENTS.md`
- [x] `GOAL-001-T09` Write `TASKS.md`, `CONTRIBUTING.md`, `README.md`
- [x] `GOAL-001-T10` Create `/goal`, `/task`, `/status`, `/verify`
- [x] `GOAL-001-T11` Runnable skeleton: theme, navigation, placeholder screen
- [x] `GOAL-001-T12` Roborazzi chain proven, references recorded
- [x] `GOAL-001-T13` Full verification passed and observed

### Decisions taken

| Decision | Reason |
|---|---|
| Template `c4software/tailscale-auto-rules` | Proven Clean architecture, ktlint/detekt/kover/Roborazzi/CI already wired |
| Hilt and Room kept (instead of Koin and SQLDelight) | The template's infrastructure is already proven; migrating would have cost Phase 0 for no gain |
| Ktor chosen for HTTP | No HTTP client in the template: a clean addition, with no conflict |
| Room removed from `app/build.gradle.kts` | A database without an entity does not compile; reapplied by GOAL-004 |
| `PlaceholderScreen` | Makes the skeleton runnable and verifiable without anticipating the screens |

### Debts opened by this Goal

- [x] `GOAL-001-T14` ~~The coverage safeguard is empty.~~ **Lifted by
      `GOAL-002-T02`**: `koverVerify` now really measures, and immediately
      failed at 86.2 % on the first models.
- [x] `GOAL-001-T15` ~~Remove `PlaceholderScreen`~~ **Lifted**: both
      destinations have their real screen, the placeholder screen and its
      strings have been deleted.
- [x] `GOAL-001-T22` ~~The application has never been launched.~~ **Lifted on
      2026-08-07**: installed and run on a Pixel 10 Pro, connected to a real
      FreshRSS instance. The whole journey works — login, feed of real articles
      with illustrations, automatic marking **transmitted to the server**
      (49 articles cached, of which 11 read and synchronised), settings,
      sign-out with confirmation.
      > **Three defects that 487 tests and 30 screenshots had not seen**:
      > 1. the login screen went **under the status bar**, its title overlapped
      >    by the clock. The screenshots render the Composable in isolation,
      >    without system bars: they could not see it;
      > 2. its title was **black on black** in the dark theme, for want of a
      >    `Surface` at the root. See below, it is the most instructive one;
      > 3. the settings screen shows **two stacked titles** — "Paramètres" in
      >    the bar, "Réglages" in the screen.
- [x] `GOAL-001-T23` **The screenshot harness was masking a production defect.**
      > The black-on-black title had already been met in Phase 0, on a
      > screenshot. It had been fixed **in the harness** — a `Surface` added to
      > `ScreenshotTest` — rather than in the application. The images became
      > correct again while production stayed at fault, and the defect only
      > resurfaced on the first real run, several Goals later.
      >
      > Fixed at the root: `MainActivity` now wraps the application in a
      > `Surface`. The harness and production coincide at last.
      > The rule is recorded in AGENTS.md §4.1: **when a screenshot reveals a
      > defect, fix the application, never the harness.**
- [x] `GOAL-001-T24` ~~Two stacked titles in the settings screen~~ **Lifted**:
      the screen title is removed, the `Scaffold` bar is enough. Original
      statement: "Paramètres" (title bar) and "Réglages" (screen header). The
      bar already shows the destination's label — the header is redundant, and
      the two words differ while designating the same thing.
      > 487 tests pass, 30 screenshots conform, and yet **no real run has taken
      > place**: neither on a device nor on an emulator. Attempted on
      > 2026-08-07, `adb devices` returns no device.
      >
      > What the tests cannot establish, and which is therefore not
      > established: the real opening of the Room database on disk, the working
      > of `AndroidKeyStore` encryption — not covered by construction, see
      > `GOAL-002-T18` — the custom tab, image loading over the network, and
      > the behaviour of the list under real scrolling, on which the whole
      > automatic marking rests.
      >
      > **To be done before announcing anything works**:
      > `./gradlew :app:installDebug` then
      > `adb shell am start -n fr.vbrosseau.freshrssdiscover/.MainActivity`,
      > against a real FreshRSS instance.
- [x] `GOAL-001-T16` **Application icon**: "le fil", an adaptive icon drawn for
      the application — a ribbon winding downwards, rather than the RSS waves
      that every other reader already carries. Background, foreground layer and
      monochrome. The template's own is no longer in place.
- [x] `GOAL-001-T21` ~~ktlint checks no Kotlin source of `:app`~~
      **Lifted by `detekt-formatting`**, which embeds the ktlint rules in
      Detekt. The safeguard was not decorative: it immediately revealed
      **22 violations**, including four dead imports left by the `AuthResult`
      → `Outcome` refactor. The original observation:
      > Observed: `./gradlew :app:tasks --all` shows only
      > `ktlintKotlinScriptCheck` — the `.kts` files. `:domain`, for its part,
      > does have `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck` and
      > `ktlintTestFixturesSourceSetCheck`. The ktlint-gradle 12.1.1 plugin does
      > not discover AGP 9's Android source sets.
      >
      > **Consequence: the verification command of AGENTS.md §5 has been
      > partly empty from the start**, exactly as the coverage safeguard was in
      > Phase 0. Proof: in `LoginScreen.kt`, `LinearProgressIndicator` is
      > imported before `Icon` — an order ktlint refuses, and which survived
      > every verification.
      >
      > Detekt, for its part, does cover `:app`: formatting is therefore not
      > entirely unwatched, but ktlint's style rules are not applied there.
      > Chosen route: add `detekt-formatting`, which embeds the ktlint rules in
      > Detekt, rather than trying to make the plugin discover the source sets.
      > The fix was deferred until the end of the parallel work: modifying the
      > Gradle files under running agents would have made them see violations
      > that appeared along the way.
- [!] `GOAL-001-T17` **Android lint disabled on the test sources**
      (`ignoreTestSources = true`). AGP 9.3.1 crashes on its own Kotlin
      analysis components.
      > **Retried on 2026-08-08, the crash persists.** Switching to `false`
      > makes `:app:lintAnalyzeDebugUnitTest` fail on
      > `SymbolLightClassForClassOrObject.getSuperTypes`, exactly the original
      > trace. Worth noting for the next attempt: `:app:lintDebug` alone
      > **passes** — it is the unit-test variant that crashes, and stopping at
      > `lintDebug` would make the problem look solved.
      >
      > What the attempt did teach nonetheless: apart from that crash, the test
      > sources carry only five warnings, all of naming (`ComposableNaming` on
      > screenshot helpers). Nothing structural is waiting behind this
      > safeguard.
      >
      > Stays blocked until an AGP version that fixes it.
- [x] `GOAL-001-T18` **Robolectric raised from API 35 to 36**, the highest level
      it can instantiate — 37 throws `UnknownSdk`, tried before deciding.
      A one-level gap remains with `targetSdk`, and it will only close with a
      Robolectric version that carries image 37.
      The rendering moves a little in passing: the 48 references have been
      re-recorded and **looked at** side by side. Only the antialiasing of the
      rounded shapes differs — sliders, switch, card corners — the layout, the
      texts and the colours are unchanged.
- [x] `GOAL-001-T19` **CI neutralised on `push` — a closed decision, not a
      debt** (`branches: [never]`). Confirmed by the author on 2026-08-08: it
      still figured among the blocked points, which suggested an obstacle
      awaiting removal. There is none.
      > Every run consumes build credit, and the local verification is exactly
      > the same command. The `pull_request` trigger stays active — it consumes
      > too, and can be neutralised the same way if needed. The guarantee
      > therefore rests entirely on AGENTS.md §5, whose output must be
      > **observed** before every commit.

---

## GOAL-018 — CI stops running on deprecated actions

**Status: DONE**

Every release reported two warnings: `setup-java v4 is deprecated`, and
`Node.js 20 is deprecated` for `download-artifact` and `action-gh-release`.
Nothing broke, and that is precisely what makes the thing easy to let drag on —
until the day GitHub removes the Node 20 engine and the release stops without
warning.

### What the analysis establishes

| Action | Before | After | What the major version brings |
|---|---|---|---|
| `actions/checkout` | v4 | v7 | Node 24 |
| `actions/setup-java` | v4 | v5 | Node 24; it is the explicitly deprecated action |
| `actions/upload-artifact` | v4 | v7 | Node 24, ESM module |
| `actions/download-artifact` | v4 | v8 | Node 24; the download digest becomes **blocking** instead of a mere warning |
| `gradle/actions/setup-gradle` | v4 | v6 | Node 24 |
| `softprops/action-gh-release` | v2 | v3 | Node 24 |

No breaking change touches this usage: the release notes were read before the
numbers were changed. The only behaviour change that concerns us — the digest
verified on arrival — goes in the right direction for a signed artefact.

### Tasks

- [x] `GOAL-018-T01` **Raise the six actions**, then observe the CI green on a
      pull request — it is the only active trigger (`GOAL-001-T19`).
      Observed: green run, and **no deprecated-action warning left** in the log.
      The ones remaining come from Gradle, not from GitHub — see
      `GOAL-018-T03`
- [ ] `GOAL-018-T02` **Observe the release**, which can only be proven at the
      next tag: it uses two actions the CI does not go through
- [ ] `GOAL-018-T03` **The remaining Gradle warnings.** "Deprecated Gradle
      features were used in this build, making it incompatible with Gradle 10"
      appears on every task. They come not from the actions but from the build
      itself — plugins or scripts. `--warning-mode all` will name them. Distinct
      from `T01`, and of a different scope: this one touches the build, not the
      integration chain

---

## Blocked points

Just one, out of our hands:

- `GOAL-001-T17` — Android lint cannot analyse the test sources: AGP 9.3.1
  crashes on its own components. Retried on 2026-08-08, the trace is unchanged.
  Will be lifted by an AGP version, not by code from here.

`GOAL-012-T07` left this list on 2026-08-08: it was not a blocker but a
trade-off, settled and recorded in SPECS.md §7.1.

---

## Open questions

The deferred functional decisions are listed in [SPECS.md §8](./SPECS.md).
The uncertainties about the remote API are listed in
[docs/freshrss-api.md §6](./docs/freshrss-api.md). Each one is settled by the
Goal that meets it, then **recorded** — never left implicit in the code.

