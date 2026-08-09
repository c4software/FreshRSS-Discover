# TASKS.md — Roadmap and actual progress

The project's persistent memory. An arriving agent must be able to read this
single file and understand **where the work stopped**.

Related documents: [AGENTS.md](./AGENTS.md) (the rules) ·
[SPECS.md](./SPECS.md) (the what) · [ARCHITECTURE.md](./ARCHITECTURE.md) (the
how).

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

---

## Current phase

**Phase 0 — Harness** ✅ finished
**Phase 1 — FreshRSS API** ✅ finished (GOAL-002, GOAL-003)
**Phase 2 — Discover feed** ✅ assembled and delivered

**Phase 3 — Tuning the marking, sharing, English documentation** ✅ finished
(GOAL-019 to GOAL-025)

**The twenty-five Goals are done.** One point remains blocked, out of our hands:
`GOAL-001-T17` — AGP 9.3.1 still crashes on `lintAnalyzeDebugUnitTest`, retried
on 2026-08-08. It will be lifted by an AGP version, not by code from here.

**Next task**: none is due.

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
> is the seventh defect of this phase found by using the application.
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
| GOAL-026 | Killing the app resurrected the feed one had just emptied | `[ ]` |

The state carried here is that of the Goal's own section, which is
authoritative. Goals are broken down into tasks by `/goal` at the moment of
taking them on: breaking them down in advance would mean deciding without
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

## GOAL-002 — FreshRSS authentication

**Status: DONE**

Let the user connect the application to their FreshRSS server and keep their
session. Covers SPECS.md §3.

Mandatory reference: [docs/freshrss-api.md §2](./docs/freshrss-api.md).
AGENTS.md §3 reminder: never invent the behaviour of an endpoint.

- [x] `GOAL-002-T01` Observe `ClientLogin` against a real server — exact shape
      of the response, error codes, behaviour with the API disabled — and
      update `docs/freshrss-api.md`
      > Observed against `https://demo.freshrss.org/` on 2026-08-07. It
      > **corrected a misreading of the source**: an unknown user answers
      > `401` and not `400`, so "unknown" and "wrong password" are
      > indistinguishable — which is the desirable behaviour. Other
      > observations: bare `GET` recognition probe → `OK` (a query string
      > breaks it), `check/compatibility` always answers `200` and requires an
      > `Authorization` header in its own request, an unknown path answers
      > `401` and not `404`.
      > **Still not observed** — the demonstration server has no usable API
      > password: `ClientLogin`'s success response and the `503` of a disabled
      > API. **Observed since** — see the "What has been observed" section of
      > `docs/freshrss-api.md`.
- [x] `GOAL-002-T02` `:domain` models: `ServerAddress`, `Credentials`,
      `AuthToken`, sealed error type covering the five causes of SPECS.md §3.3
- [x] `GOAL-002-T03` `ServerAddress` and `AuthSession`: normalisation of the
      entered address (implicit scheme, derivation of `…/api/greader.php`,
      `http://` tolerated and flagged) — pure, exhaustively tested
- [x] `GOAL-002-T04` Wire Ktor into `app/build.gradle.kts` (OkHttp engine,
      content negotiation limited to `application/json`, logging without
      secrets) and provide the client through Hilt
- [x] `GOAL-002-T05` `FreshRssApi`: recognition probe, header-forwarding probe,
      `clientLogin()` — plain text response, `key=value` pairs
- [x] `GOAL-002-T06` Translation of HTTP codes into domain errors
      (`400/401/404/503`, plain text body, connectivity to tell "offline" from
      "unreachable")
- [x] `GOAL-002-T07` `AuthRepository`: interface in `:domain`, implementation
      in `:app/data`, plus `NetworkAvailability`.
      **Added a sixth cause to SPECS.md §3.3**: `Authorization` header stripped
      by a reverse proxy.
- [x] `GOAL-002-T08` Encrypted token storage (DataStore backed by the keystore)
      — never logged. **Handled before T07**, whose repository rests on it.
      **Modified SPECS.md §3.4**: the API password is no longer stored at all,
      since the token does not expire.
- [ ] ~~`GOAL-002-T09` Retrieve and keep the `T` modification token~~
      **Deferred to GOAL-008** (read status synchronisation). The `T` token
      only serves modifying operations: fetching it here would produce a call
      nobody has any use for, and dead code until GOAL-008 (AGENTS.md §2).
      `AuthSession` already carries it, optionally, and `SessionStore` knows how
      to keep it — which a test covers.
- [x] `GOAL-002-T10` API layer tests with `MockEngine`: success, each error
      code, truncated response, JSON response where text is expected. Written in
      the same increment as the code they cover (AGENTS.md §4), so delivered by
      T04, T05 and T06 rather than in a separate pass.
- [x] `GOAL-002-T11` Repository tests, encrypted storage included — delivered by
      T07 and T08, same reason.
- [x] `GOAL-002-T12` `LoginViewModel` and its `UiState`
- [x] `GOAL-002-T13` Login screen, with the explanation of the API password
      (SPECS.md §3.2) and a distinct message per failure cause
- [x] `GOAL-002-T14` Root routing by the presence of a session; token refused →
      login screen pre-filled with the address and the username
- [x] `GOAL-002-T15` Screen tests, and Roborazzi screenshots of the login in
      light and dark — empty, filled, in progress, in error. **Looked at**: they
      revealed a progress indicator that was nearly invisible inside a disabled
      button.
- [x] `GOAL-002-T16` Re-observe `koverVerify` on `:domain` (lifts
      `GOAL-001-T14`) — done as early as T02: the threshold really did fail at
      86.2 %.
- [x] `GOAL-002-T17` Update `ARCHITECTURE.md` §9 and `SPECS.md`

### Decisions taken

| Decision | Reason |
|---|---|
| The API password is not stored | The token does not expire: keeping it is enough. **Modified SPECS.md §3.4** |
| Sixth failure cause: `Authorization` header not forwarded | Without it, a faulty reverse proxy would get the credentials blamed. **Modified SPECS.md §3.3** |
| AES/GCM encryption written by hand | `androidx.security:security-crypto` is deprecated (AGENTS.md §2) |
| `SecretCipher` abstracted | Robolectric does not simulate `AndroidKeyStore`; without it, persistence and erasure would be unprovable |
| `SecretKeySource` extracted (GOAL-002-T18) | The encryption itself stayed unprovable because it manufactured its own key. Splitting it reduces the blind spot to the platform call |
| Recognition probe **before** sending the credentials | A typo would otherwise send the password to a third-party server |
| Header-forwarding probe **after** obtaining the token | Earlier: one wasted round trip per attempt. Later: a session doomed to loop on 401s |
| `invalidateSession()` distinct from `signOut()` | A refused token keeps address and username; a sign-out erases everything |

### Debts opened by this Goal

- [x] `GOAL-002-T18` **Secret encryption is proven** — 9 tests, including the
      round trip, the initialisation vector that changes on every encryption, an
      altered byte that GCM refuses, and unreadable text treated as an absent
      session rather than crashing.
      Robolectric still does not simulate `AndroidKeyStore` — retried, the
      provider throws `NoSuchAlgorithmException`. The class therefore stayed
      unprovable **for the sole reason that it manufactured its own key**: the
      key's provenance has moved behind `SecretKeySource`, and what remains
      uncovered fits in about twenty lines that do nothing but call the
      platform.
- [x] `GOAL-002-T19` ~~Two API points not observed~~ **Lifted.** `ClientLogin`'s
      success response and the `503` of a disabled API have been observed on a
      personal instance.
      > The second one **corrected a documentation error**: the recognition
      > probe answers `OK` and `200` **even with the API disabled**, the
      > short-circuit serving it being placed before the `api_enabled` check.
      > Writing "every endpoint answers 503" was false. The implementation was
      > already correct — it is `ClientLogin` that reveals the `503` — but for a
      > reason that was not written down; a test now locks it in.
- [x] `GOAL-002-T20` ~~No authenticated call exists yet~~ **Lifted by
      `GOAL-003-T06`**: reading the feed is the first authenticated call, and a
      `401` there does erase the tokens while keeping the entry reminder.

---

## GOAL-003 — Paginated article retrieval

**Status: DONE**

Covers SPECS.md §4.1 and §4.4. The delicate point: the `continuation` cursor is
relative and not positional, and an invalid cursor causes a **silent repetition
of the first page** — see docs/freshrss-api.md §3.5 and ARCHITECTURE.md §4.2.

Settles SPECS.md §8 question 1 (page size).

- [x] `GOAL-003-T01` Generalise `AuthResult` into `Outcome<T, E>` — article
      failure is the second use case, so the moment AGENTS.md §2 foresees for
      creating the abstraction, not before
- [x] `GOAL-003-T02` `:domain` models: `Article`, `ArticleId`, `FeedRef`,
      `PageCursor`, `ArticlePage`, `FeedError`
- [x] `GOAL-003-T03` `stream/contents` DTOs and deserialisation — optional
      fields, heterogeneous time units, `categories` carrying the read state
- [x] `GOAL-003-T04` DTO → domain conversion: hexadecimal identifier to decimal,
      illustration extraction, article with no usable link
- [x] `GOAL-003-T05` `FreshRssApi.streamContents()` — authorisation header, `n`,
      `c`, `xt`, and the absence of `continuation` as the only end signal
- [x] `GOAL-003-T06` `ArticleRepository`: `:domain` interface, `:app/data`
      implementation, and `401` → `invalidateSession()` (lifts `GOAL-002-T20`)
- [x] `GOAL-003-T07` Settle the page size and record it in SPECS.md §8 —
      **40**, and question 6 (illustration) settled along the way. A seventh
      question opened: the server does not usefully truncate the summary.
- [x] `GOAL-003-T08` Update `ARCHITECTURE.md` §9

---

## GOAL-004 — Local cache and network resilience

**Status: DONE** — cache read at launch and offline, purge and queue delivered

Covers SPECS.md §5.

- [x] `GOAL-004-T01` Reapply Room: plugin, dependencies, `schemaDirectory`,
      versioned schema `app/schemas/…/1.json`
- [x] `GOAL-004-T02` `ArticleEntity`, `ArticleDao`, `AppDatabase`, `DatabaseModule`
- [x] `GOAL-004-T03` `ArticleCache`: `save`, `observeArticles`, `clear`,
      `purgeReadOlderThan` — 12 tests on an in-memory database
- [x] `GOAL-004-T04` Wire the writing: every fetched page is deposited in the
      cache, and signing out empties it (SPECS.md §3.5)
- [x] `GOAL-004-T05` `observeCachedArticles()`: the cache is displayed before any
      request (SPECS.md §5.1)
- [x] `GOAL-004-T06` Full offline fallback: discreet banner over the cached
      content, full-frame screen reserved for the case with **no article at all**
- [x] `GOAL-004-T07` **Purge triggered, threshold settled at 7 days** (SPECS.md
      §8, question 3), once per process start.
      > ⚠️ **A serious defect fixed along the way.** The purge only tested
      > "read **and** old enough": on a device offline for longer than the
      > threshold, it erased an article whose marking was still waiting to be
      > transmitted. The queue survived, but **the local memory of "already
      > read" went with the row** — it lives nowhere else. On the next refresh
      > the server described the article as unread, nothing contradicted it any
      > more, and it **reappeared in the feed**.
      > Fixed by an explicit exclusion of pending markings, and SPECS.md §5.3
      > now says literally why.
- [x] `GOAL-004-T08` Pending markings queue (ARCHITECTURE.md §5.1):
      `PendingMarkEntity`, `PendingMarkDao` and `PendingMarkQueue`, database at
      version 2 with its two versioned schemas (`app/schemas/1.json` and
      `2.json`). Covered by `PendingMarkQueueTest`

### Decisions taken

| Decision | Reason |
|---|---|
| The local read state never goes backwards | A marking sent offline is only transmitted when the network returns; until then the server describes the article as unread. Overwriting it would **make what the user has just read reappear** — the most visible regression a cache can produce. "Read" propagates, "unread" does not |
| Purge on age **in the cache**, not on the publication date | Otherwise an old article just opened would vanish within the second, while still on screen |
| Feed title duplicated per row, no feed table | A single reader: the abstraction arrives with its second use (AGENTS.md §2) |

---

## GOAL-005 — Source interleaving

**Status: DONE** — `interleaveBySource` orders the server's and the cache's pages

The heart of the application (SPECS.md §4.2). Settles SPECS.md §8 question 2.

- [x] `GOAL-005-T01` `interleaveBySource(articles, previousTail)` — pure
      function, 14 tests, 100 % coverage
- [x] `GOAL-005-T02` Record in SPECS.md §4.2 the trade-off between rules 1 and
      2, which the specification did not settle
- [x] `GOAL-005-T03` **Interleaving applied**: `loadPage` and `refresh` now
      return the display order. The function is no longer dead code
- [x] `GOAL-005-T04` Refresh case settled: `refresh()` interleaves the first page
      **among its own articles only** — nothing precedes it — and deduplication
      falls to the caller, the only one that knows what is on screen

### Decisions taken

| Decision | Reason |
|---|---|
| Recency wins over source spreading | The two rules are structurally incompatible beyond a certain amplitude, and SPECS.md did not say which one wins |
| A bound of seven positions, expressed in **ranks** and not in duration | A time-based threshold would behave very differently on a feed publishing three articles a day and on one publishing three hundred. The bound in ranks is the same everywhere, and it is the one the user perceives |
| Sliding window rather than fixed blocks | Blocks would let the monotony reappear at every junction |

---

## GOAL-006 — Discover feed — interface

**Status: DONE** — screen, illustrations and loading states delivered

Covers SPECS.md §4.3 and §4.4.

- [x] `GOAL-006-T01` `DiscoverUiState`, `DiscoverPhase`, `DiscoverViewModel`:
      prefetching, page accumulation, idempotent `loadMore`
- [x] `GOAL-006-T02` `DiscoverScreen`: lazy list, stable keys, article card,
      explicit end of feed, article with no link not clickable
- [x] `GOAL-006-T03` Relative date and excerpt shortening — pure functions
      tested, time coming from `Clock`
- [x] `GOAL-006-T04` Plug the screen into the Discover destination
- [x] `GOAL-006-T05` Screen and ViewModel tests, plus ten Roborazzi screenshots
      (feed, empty, loading, error, end) — **looked at**: no visual defect
- [x] `GOAL-006-T06` **Illustrations displayed** (Coil), stable aspect ratio,
      load failure that closes the card back up, and placeholder contrast fixed —
      it was strictly invisible in the light theme (ratio 1.00). Original
      statement: A `TODO(GOAL-006)` remains
      in `DiscoverScreen`: no image-loading library is in the project, the slot
      is reserved but stays grey. Requires a dependency (Coil), hence a change
      to the Gradle files. To be handled at the same time: carry `imageUrl`
      through to `ArticleUiModel`, set a
      `contentDescription` — deliberately absent as long as nothing is shown —
      and **fix the contrast of the placeholder in the light theme**, observed on
      `discover-flux-clair.png`: `surfaceVariant` on a nearly identical card
      container makes it almost invisible, whereas it stands out clearly in
      dark.
- [x] `GOAL-006-T07` Interleaving applied by the repository
- [x] `GOAL-006-T08` Visibility measured and detector fed

### Decisions taken

| Decision | Reason |
|---|---|
| Articles and loading phase **separated** in the state | SPECS.md §4.4 requires that a next-page failure does not empty the display, which would be impossible if the list only lived in the "loaded" case of a sealed type |
| Five distinct phases rather than crossed booleans | Two independent flags would allow the ambiguous state "neither in progress, nor finished, nor in error", that is to say exactly the list that stops growing without saying anything |
| `SessionExpired` displays **nothing** but stops the requests | The screen is about to disappear; without an explicit stop, scrolling would ask for a page on every frame until the switch |
| "Empty feed" distinguished from "end of feed" | "You have read everything" under an empty list explains nothing |

---

## GOAL-007 — Automatic marking as read

**Status: DONE** — the visibility measurement feeds the detector in both modes

Covers SPECS.md §4.5.

- [x] `GOAL-007-T01` `ReadDetector`: dual threshold, area plus continuous
      duration, injected thresholds, `Clock` for time — 18 tests, 100 % coverage
- [x] `GOAL-007-T02` Lift the two ambiguities of SPECS.md §4.5 that the
      implementation revealed
- [x] `GOAL-007-T03` Visibility measurement inside the `LazyColumn` — pure
      function `visibleFraction`, 22 tests
- [x] `GOAL-007-T04` Periodic observation at 200 ms, stopped when not in the
      foreground
- [x] `GOAL-007-T05` Detector connected to optimistic marking: `markAsRead` then
      `flush`, and replay at start-up
- [x] `GOAL-007-T06` `onVisibilityChanged` passed down from `AppNavHost` — the
      measurement actually runs
- [x] `GOAL-007-T07` `ReadDetector` built from the observed settings, and
      rebuilt on every change — without a restart

### Decisions taken

| Decision | Reason |
|---|---|
| The fraction is measured against `min(article height, window height)` | An article taller than the screen would otherwise cap below 60 % and would **never** be marked read |
| Observation cadence at 200 ms | The maximum lag is one period: the one-second threshold fires between 1.0 s and 1.2 s. At 16 ms the coroutine would be woken 60 times a second for a rule whose unit is the second; at 1 s the announced threshold could double |
| Observation tied to `RESUMED`, not `STARTED` | `STARTED` includes the screen behind a dialog: articles would be marked read without being read |
| `onVisibilityChanged` **nullable**, null by default | Arming a periodic loop with no recipient would burn battery and keep rendering tests perpetually busy. `null` says "nobody is listening", which a `{}` cannot express |
| `ReadDetector` built in the ViewModel, not injected | Its state belongs to this list; injected, it would outlive the screen and believe redisplayed articles already reported |

### Decisions taken

| Decision | Reason |
|---|---|
| Both thresholds are **inclusive** | SPECS.md says "at least"; and 0.6 is not exactly representable in binary — an exclusive threshold would make the rule depend on the rounding done by the interface |
| Already-reported articles are retained for the lifetime of the detector | That is the price of the "never reported twice" guarantee. The cost is bounded by what the user has read, not by the number of observations |

---

## GOAL-008 — Read status synchronisation

**Status: DONE** — batches, queue, replay at start-up and forced transmission

- [x] `GOAL-008-T01` `FreshRssApi.modificationToken()` and `markAsRead()` —
      12 tests, including the unsigned identifier
- [x] `GOAL-008-T02` `PendingMarkEntity`, `PendingMarkDao`, `PendingMarkQueue`,
      and a real `AppDatabase` 1 → 2 migration — 11 tests, migration included
- [x] `GOAL-008-T03` **`addMigrations(MIGRATION_1_2)` declared** in
      `DatabaseModule`, with `providePendingMarkDao`. Without it, any device
      already at version 1 would have crashed on first access — invisible to the
      tests, which build the database in memory, hence always at the current
      version
- [x] `GOAL-008-T04` `ReadSyncRepository`: optimistic marking, batches of 100,
      acknowledgement after confirmation, replay at start-up — 30 tests
- [x] `GOAL-008-T05` On a `401`, the modification token is requested again
      **only once**; a second `401` concludes the session is lost **without
      emptying the queue**
- [x] `GOAL-008-T06` Signing out empties the queue, as it already does the cache
- [x] `GOAL-008-T08` Local marking goes back through `ArticleCache` and not
      through the DAO. The agent had had to short-circuit the wrapper, for want
      of write access within its scope; it reported the fact rather than keeping
      quiet about it
- [x] `GOAL-008-T07` Batch size **100**, batching window **5 s at a fixed
      deadline** (SPECS.md §8, question 4) — 17 tests
- [x] `GOAL-008-T09` Force transmission when going into the background:
      `ReadFlushOnBackgroundObserver` calls `flush()` on `ON_STOP` — and not
      `ON_PAUSE`, which fires as soon as another window comes in front. On the
      application scope, so that transmission survives the destruction of the
      screen. Covered by `ReadFlushOnBackgroundObserverTest`

### Decisions taken

| Decision | Reason |
|---|---|
| `OnConflictStrategy.IGNORE` rather than `REPLACE` | Deduplication preserves the original queueing date. With `REPLACE`, a frequently revisited article would see its timestamp pushed back and **might never reach the head of the queue** |
| Sorting on `(date, identifier)` and not on the date alone | Without a second criterion, a partial transmission could loop back onto the same batch |
| `acknowledge` distinct from `pending` | Removing before confirmation would lose the marking on a network failure — precisely what the queue exists to prevent |
| Real migration, no `fallbackToDestructiveMigration` | A destructive migration would empty the cache **and the untransmitted markings** of every existing user |
| The token's length is not validated | A refused token announces itself with a `401`, not with its size |
| Batch of **100** articles | From below: a page holds 40 articles, a smaller batch would make several requests for one page browsed. From above: each article is an `i` field, and PHP by default accepts only 1,000 fields (`max_input_vars`) — beyond that **the extra fields are silently ignored**, and `edit-tag` answers `OK` with no report. The loss would be entirely mute |
| **Fixed** batching window, not sliding | Continuous scrolling produces a batch every 200 ms: a restartable window would **never** close while the user reads, and transmission would only happen on closing |
| A `5xx` on `/token` does not sign out | Only a `401` means "token refused". A server outage would otherwise lose the session on every hiccup |

> ⚠️ **A trap identified in advance.** An article identifier exceeding
> `Long.MAX_VALUE` is kept as bits, hence **negative** in Kotlin. Reformatting
> it with `toString()` would send `-1` to the server: it is
> `java.lang.Long.toUnsignedString` that must be used for `edit-tag`'s `i`
> parameter. Observed while writing the conversion tests (GOAL-003-T04).

Covers SPECS.md §4.5 (batched, optimistic sending, replay). Rests on
`edit-tag` — see docs/freshrss-api.md §4.1, including its batch handling via a
repeated `i`.

Settles SPECS.md §8 question 4.

---

## GOAL-009 — Pull to refresh

**Status: DONE** — validated on a device

Covers SPECS.md §4.6.

- [x] `GOAL-009-T01` `ArticleRepository.refresh()` — returns the day's first
      page, without touching the pagination cursor
- [x] `GOAL-009-T02` Pull gesture and indicator — 31 tests, 3 screenshots
- [x] ~~`GOAL-009-T03` Insert only the unknown ones at the head~~ **Replaced by
      `GOAL-009-T04`**: the specification changed at the author's request
- [x] `GOAL-009-T04` **The pull empties the list, reloads and goes back to the
      top** (SPECS.md §4.6 rewritten). Inserting at the head preserved the
      reading position but made the gesture almost invisible — you pulled, and
      nothing seemed to happen
- [x] `GOAL-009-T05` **The reading position survives closing**
      (SPECS.md §5.3, new section): `ReadingPositionViewModel` and
      `ReadingPositionStore`. It is the exact counterpart of the pull — a
      closing is not a request from the user.
      **Fixed after a trial on a device**: the first version only kept the
      identifier, but the top article is precisely the one that the marking has
      just turned read, and the feed only shows unread ones — the restore could
      almost never succeed. The publication date now travels with the
      identifier, and `ReadingPosition` resumes at the nearest one, which is what
      §5.3 already asked for
- [x] `GOAL-009-T06` **Validated on a device** (Pixel 10 Pro, Android 17): pull
      → the list is emptied, reloaded and scrolled back to the top; six screens
      of scrolling then an `am force-stop` → the application reopens exactly on
      the article that was at the top. It is this trial that revealed the
      restore defect fixed in `GOAL-009-T05`

---

## GOAL-010 — Opening the original article

**Status: DONE**

Covers SPECS.md §4.7.

- [x] `GOAL-010-T01` `ArticleOpener`: custom tab, scheme filtering, absence of a
      browser handled — 17 tests
- [x] `GOAL-010-T02` Opener wired in: `ArticleUiModel` now carries `url`, and
      `isOpenable` is derived from it by default
- [x] `GOAL-010-T03` Article marked read on opening, whatever its past
      visibility
- [x] `GOAL-010-T04` Opening refused offline, with a notice **acknowledged by
      hand** — a message that fades on its own gets missed. The article is not
      marked

### Decisions taken

| Decision | Reason |
|---|---|
| Only `http` and `https` are opened | An RSS feed's link is **third-party content beyond our control**. Letting `intent:`, `javascript:` or `file:` through would amount to letting a remote server decide what the phone does |
| No preconnection, no `warmup`, no bound session | SPECS.md §7.4: opening is an action **of the user's**. A preload would be an outgoing request they did not ask for. Price paid and owned: a slightly slower opening |
| The opener revalidates the URL the screen has already filtered | It does not trust its caller: the guarantee must hold even if some future screen forgets the filter |
| Tab bar in `surface`, not `primary` | The tab extends the screen it covers |

---

## GOAL-011 — Settings screen

**Status: DONE** — sign-out, purge, reading thresholds, display mode and reminder

Covers SPECS.md §6.

- [x] `GOAL-011-T01` `SettingsUiState`, `SettingsViewModel`, `SettingsScreen`,
      `SettingsTestTags` — 18 tests, 4 screenshots **looked at**
- [x] `GOAL-011-T02` Sign-out with confirmation (SPECS.md §3.5): both outcomes
      are tested, cancelling does not call `signOut()`
- [x] `GOAL-011-T03` Screen plugged into the Settings destination, last
      `PlaceholderScreen` removed (lifts `GOAL-001-T15`)
- [x] `GOAL-011-T04` **Thresholds editable and persisted**, notched sliders,
      bounds validated in the domain — 36 tests. The duplication of the defaults
      is gone: the display observes the repository, it no longer copies anything
- [x] `GOAL-011-T05` Cache size displayed and manual purge wired in —
      28 tests. The size is a **number of articles**, not bytes: SQLite does not
      give its pages back to the system, a purge would leave the megabytes
      unchanged and would read as having no effect
- [x] `GOAL-011-T06` **Licence chosen: MIT.** `LICENSE` added at the root, the
      settings screen shows "Licence MIT". The agent had refused to invent one
      and displayed "Non encore déterminée" — that was the right conduct: the
      licence is an author's decision, not a detail to fill in

### Decisions taken

| Decision | Reason |
|---|---|
| The thresholds are displayed but not editable | Making them editable without storage would give a setting that does not survive closing — worse than an absent setting |
| The purge button is disabled, not hidden | Announcing the feature is better than letting it be discovered later; the sentence above it explains why it does not respond |
| The unit conversion is done in the ViewModel | `0.6f → 60 %` and `1000 ms → 1 s` are computations: AGENTS.md §9 forbids them in a Composable |

---

## GOAL-012 — Swipe view, article by article

**Status: DONE** — validated on a device, reading position included.

Covers SPECS.md §4.8, added at the author's request. An alternative
presentation mode: one article full screen, horizontal swipe to move to the
next, like a social network's Stories.

### What is already settled

| Point | Decision |
|---|---|
| "Next feed" designates the next **article** | Not the source nor the category: SPECS.md §1 and §2 exclude any navigation by feed or by folder, and that remains true |
| The two modes **coexist** | The list screen is kept, with its lazy list, its prefetching and its visibility measurement. The mode is chosen in the settings |
| The content is **identical** | Same articles, same interleaving, same rules. Only the presentation changes — switching mode reorders nothing (rule 3 of §4.2) |

### What remains to be designed, and is not trivial

- [x] `GOAL-012-T01` **The visibility measurement changes in nature.** A
      full-screen article is 100 % visible: the area threshold is satisfied from
      the outset, and duration alone decides. `ReadDetector` applies as it
      stands, but its input cannot come from `LazyListState` — an observation
      source specific to this mode is needed.
      **The link is now proven end to end**: four screen tests check that the
      reading starts, that it repeats while nothing moves, that it follows the
      swipe, and that it does not arm itself without a recipient.
      They were validated by mutation — cutting the observation makes three of
      them fail, restoring it makes them pass again. Neither
      `SwipeViewModelTest` nor `SwipeVisibilityTest` could see this link: the
      first assumes it is being spoken to, the second computes without anyone
      calling it
- [x] `GOAL-012-T02` **Prefetching must survive the gesture.** Request the next
      page before reaching the last loaded article, without the swipe stalling
- [x] `GOAL-012-T03` **The end of the feed must be said**, as in List mode: a
      swipe that stops responding is indistinguishable from a breakdown (§4.4)
- [x] `GOAL-012-T04` **Going back does not unmark.** Returning to a read article
      does not put it back to unread — the marking is not reversible by a
      navigation gesture
- [x] `GOAL-012-T05` **Position shared between the two modes.** Swipe mode
      remembers the card under the eyes and resumes where reading stopped,
      through the **same** `ReadingPositionViewModel` as List mode — the
      position belongs to the feed, not to the way of going through it, and two
      separate memories would contradict each other at every switch.
      The obstacle that had deferred it is lifted by the "nearest" restore of
      `GOAL-009-T05`, and not worked around: full screen, the article left
      behind is nearly always the one that the marking has just turned read,
      hence absent from the next feed. `indexIn` keeps the first article that is
      not more recent.
      `settledPage` and not `currentPage`: the second switches as soon as the
      gesture passes half the screen, including when the finger comes back — a
      position never reached would be recorded. 5 screen tests, including the
      vanished article and the entirely more recent feed
- [x] `GOAL-012-T06` Persistent setting for the mode, in the settings screen (§6)
- [x] `GOAL-012-T07` **Accessibility of the swipe gesture — settled, and not
      left hanging.** The author's decision on 2026-08-08: the rule of
      SPECS.md §7.1 bears on the **application**, not on each of its modes.
      List mode — the default one — gives access to the same feed, in the same
      order, entirely by vertical scrolling and ordinary targets; Swipe is a
      preference, never a compulsory passage, and the setting that leaves it is
      reachable without the gesture in question.
      The task's history is worth keeping: two buttons "Previous" / "Next" had
      answered it, then were removed — they cluttered the screen of a mode whose
      whole point is to have no controls. Reopening the task at that moment was
      right; leaving it open indefinitely made a trade-off look like a debt.
      Consequence owned, recorded in SPECS.md §7.1: whoever uses a screen reader
      and finds themselves in Swipe must go through the settings
- [x] `GOAL-012-T08` Roborazzi screenshots of Swipe mode, light and dark —
      **and looked at**: six images recorded. Then a **real run on a device**
      (Pixel 10 Pro, Android 17), which is what counts: List mode had shown that
      three defects out of three were only visible that way. The setting
      switches, the mode is read back at the next start-up, the swipe moves to
      the next article and "Previous" then becomes active

- [x] `GOAL-012-T09` **Card-stack animation**, requested by the author: the card
      leaving tilts and fades out, the one underneath stays centred and grows.
      The geometry is a pure function proven separately
      (`swipeCardTransform`), because no screenshot shows the middle of a
      gesture. Observed on a device, screenshot in support, mid-gesture

- [x] `GOAL-012-T10` **Reload button**, requested by the author, shared by both
      modes: `RefreshButton`. It is necessary in Swipe — there is no list to
      pull, and a vertical pull would compete with the horizontal gesture — and
      taken up in List **in addition** to the gesture, which is not practicable
      for everyone. It turns into an indicator while waiting rather than greying
      out or disappearing.
      **Placed on the title line**, at the author's request: superimposed on the
      feed, it always covered part of it — the corner of the first card in List,
      the illustration in Swipe. The displayed destination therefore publishes it
      to the shell (`FeedRefresh`), which has no reason to know its ViewModel
- [x] `GOAL-012-T11` ~~The "Open the article" button is truncated when the
      excerpt is long~~ **Misattributed, and taken up in `GOAL-014-T12`.**
      Original statement: on an article whose excerpt approaches 1,400
      characters, the button is pushed out of the card, seen on a device on
      2026-08-08. That is true **at rest**, but the card's content scrolls
      (SPECS.md §7.1): without a notice strip, the button comes fully back on
      screen. So there was no defect here. What made one was the staleness
      strip laid over it, which covered the end of the scrollable content — a
      defect of GOAL-014, fixed over there.

### Question settled

The excerpt was limited to 240 characters in List mode (SPECS.md §8,
question 7), calibrated on three card lines. Full screen it goes up to
**1,400**, cut on a word boundary. The figure is not round by accident: the
median summary measures 1,324 characters, so the ordinary article is shown in
full, and the screen holds about as much. Not the whole content for all that —
the maximum measured is 34,777 characters, and an article scrolled vertically
would conflict with the horizontal gesture.

---

## GOAL-013 — Reading reminder by local notification

**Status: DONE** — validated on a device

Covers SPECS.md §4.9, added at the author's request.

A daily notification recalls that there are still articles to read. It goes out
at **the previous day's opening time**, quotes real titles, and varies its
wording from one day to the next.

### What was settled before writing

| Point | Decision | Reason |
|---|---|---|
| SPECS.md §2 excluded notifications | **The specification changes** | It is an author's decision, not a workaround. The exclusion is lifted explicitly rather than silently broken (AGENTS.md §1.2) |
| Source of the quoted articles | The **local cache**, never the network | SPECS.md §2 still excludes background synchronisation, and §7.4 wants no connection to go out without a gesture from the user. A reminder that queried the server would be precisely the background synchronisation set aside |
| Nothing to read | **No notification** | A reminder announcing there is nothing to read is an interruption with nothing in return, and that is what makes people turn an application's notifications off |
| Time kept when the application is opened several times | The **first** opening of the day | That is the moment the user reaches for the application; the last opening would keep a distracted visit |
| Choice of wording | **Deterministic** on the day number | A retry after failure replays the same day; a random draw would give two messages for a single reminder |

### Tasks

- [x] `GOAL-013-T01` **The domain decides**: `DailyMinute`, `nextReminderAt`,
      `ReminderTone`, `reminderPlanFor`. No string, no clock, no time zone read
      — everything is passed in. 17 tests, including the clock change in both
      directions and a device clock earlier than the epoch
- [x] `GOAL-013-T02` **The cache can say what is left**: reading of the unread
      articles, without the network — filter done by SQLite, one-off read rather
      than a `Flow`, source interleaving applied as in the feed. 5 tests on a
      real database
- [x] `GOAL-013-T03` **The opening time is kept**: first launch of the day
      recorded, `DataStore` (`ReminderTimeStore`, 8 tests including two time
      zones and the day change). It is this time that the reminder validated on
      a device actually used
- [x] `GOAL-013-T04` **WorkManager carries the reminder**: `HiltWorker`, unique
      work, the next day rearmed by the worker itself — without which the chain
      stops as soon as the application is not opened
- [x] `GOAL-013-T05` **The notification is built**: channel, wordings in
      resources, application opened on touch
- [x] `GOAL-013-T06` **The permission is requested** (`POST_NOTIFICATIONS`,
      API 33+), and refusing it prevents nothing else from working
- [x] `GOAL-013-T07` **The reminder can be switched off** from the settings (§6):
      below API 33 there is no permission to withdraw, and a reminder you cannot
      turn off is a defect
- [x] `GOAL-013-T08` **One reminder at a time, cleared on opening**: same
      notification identifier from one day to the next — a new reminder replaces
      the previous one instead of stacking up — and removal on returning to the
      application
- [x] `GOAL-013-T09` **Documentation**: SPECS §2 and §4.9, ARCHITECTURE §9 and
      the package map, README (the feature and the fact that it calls nothing),
      TASKS
- [x] `GOAL-013-T10` **Validated on a device** (Pixel 10 Pro, Android 17):
      notification actually received, tone "Un moment pour lire ?", two real
      titles and "119 articles non lus"; cleared on opening, observed at zero
      records.
      **The scheduler was observed separately**: after an opening, the work
      appears in `dumpsys jobscheduler` at `+23h59m`, computed by the real code.
      Forcing that work does not make it go — WorkManager rechecks its own delay
      — hence a **local and uncommitted** variant with a short delay to see the
      notification itself.
      **Still not observed on a device**: the absence of a duplicate. It rests
      on a constant identifier and it is proven in a unit test, but my
      measurement on the device counted `dumpsys` lines and not distinct
      notifications — the phone disconnected before I could redo it properly

### What was fixed while integrating

`AppGraphTest` had had to replace the real scheduler with a double, for want of
a `WorkManager` initialised under `HiltTestApplication`. The hole is closed:
`WorkManagerTestInitHelper` boots the manager before injection, and **all** of
that test's dependencies come back from the real graph — a double there would be
a hole, not a convenience.

### Open debt

The reminder only sees the cache: an article published since the last opening is
not in it, and so will not be announced. That is the owned price of the absence
of background synchronisation.

---

## GOAL-014 — Feed staleness notice

**Status: DONE** — validated on a device

Covers SPECS.md §4.6, added at the author's request.

The feed never synchronises on its own (SPECS.md §2), and the cache is displayed
from launch (§5.1): the screen of a ten-hour-old feed was indistinguishable from
that of a fresh one. Beyond **6 h** without a response from the server, an
actionable strip invites a refresh.

### What was settled before writing

| Point | Decision | Reason |
|---|---|---|
| Staleness threshold | **6 h** | Author's decision. Recorded in SPECS.md §8 |
| Who decides | A pure function of `:domain` | Like `reminderPlanFor` (GOAL-013): no clock, no string, no Android in the rule |
| Who timestamps | The **repository**, on every valid server response, `loadPage` included | Two ViewModels call `refresh()`; timestamping on the presentation side would duplicate the rule and let the two modes diverge. The layer that spoke to the server is the only one that knows it answered |
| Storage medium | DataStore, key `feed.last_refresh_at` | Scalar (ARCHITECTURE.md §5.1: Room carries the collections, DataStore the scalars) |
| Offline | **No strip** | The offline banner already says why the feed is old. Offering "Refresh" where the call will certainly fail is a false door, and would stack two strips in the same place on the screen |
| Acknowledgement | **In memory**, shared by both modes, keyed by the acknowledged timestamp | Local to one ViewModel, it would bring the strip back at every List↔Swipe switch. Comparing timestamps brings it back after a successful refresh and then 6 h, without any extra clock |
| Clearing | **By hand**, never by a timer | The repository has already settled it that way for the offline opening notice: "a message that fades on its own gets missed" |
| The strip's controls | "Refresh" **and** a dismissal | A single action would impose the message on anyone not in a position to refresh |

### Tasks

- [x] `GOAL-014-T01` **The domain decides on staleness**: `FeedFreshness`,
      `STALE_FEED_THRESHOLD_MILLIS`, `FeedFreshnessRepository`. Never stale
      without a reference point; a clock going backwards makes nothing stale.
      15 tests, including the timestamp restored from the future and the
      acknowledgement that time reopens
- [x] `GOAL-014-T02` **The timestamp is persisted**: `FeedFreshnessStore`,
      `DataStore`, acknowledgement in live memory and shared. 7 tests on a real
      DataStore, including the acknowledgement made before any refresh
- [x] `GOAL-014-T03` **The repository records every successful server contact**,
      including a valid but empty page — the server answered. 8 tests, including
      the four failures that must record nothing
- [x] `GOAL-014-T04` **List mode carries the notice**: derived state,
      acknowledgement, and the periodic wake-up without which the threshold
      would never be crossed on screen. The watching is written once, in
      `FeedStalenessWatcher`, so that the two modes do not diverge.
      11 tests, including ageing with no event at all
- [x] `GOAL-014-T05` **Swipe mode carries the same notice**, acknowledgement
      included — acknowledging in one mode silences the other. 6 tests,
      including that one precisely
- [x] `GOAL-014-T06` **The strip is factored out** (`FeedNotice`): it was
      written twice, identically, in the two screens. A pure refactor — the
      screen tests pass unchanged and `verifyRoborazziDebug` sees no pixel move.
      5 tests of the component's own
- [x] `GOAL-014-T07` **The notice is displayed in List mode**, and "Reload"
      there borrows exactly the existing reload. 5 screen tests, including the
      one that observes that a single strip occupies the bottom of the screen
- [x] `GOAL-014-T08` **The notice is displayed in Swipe mode**, without masking
      the article-opening control — measured, and not merely assumed.
      5 screen tests, same strings as in List mode
- [x] `GOAL-014-T09` **Roborazzi screenshots**: the strip on a card and on a
      full-screen illustration are not judged in the same place. Four
      references, **looked at**: the two controls sit side by side without
      wrapping the message, the contrast passes in both themes, and "Open the
      article" is not covered
- [x] `GOAL-014-T10` **Documentation**: SPECS §4.6 and §8 question 9,
      ARCHITECTURE §5.1 and §9.6, README, TASKS
- [x] `GOAL-014-T11` **Observed on a device** (Pixel 10 Pro, Android 17,
      2026-08-08). Protocol: the date written by the repository was set back 7 h
      in the DataStore, and the server address pointed at `192.0.2.1`
      (TEST-NET, with no route) — that is the **only** setup that brings
      together the two conditions of the notice, a stale feed and a device that
      is not offline.
      **What was seen**: the date of the last server contact is indeed written
      by the repository at launch; the strip appears in Swipe over real content,
      then in List; "Later" turns it off; it **stays off after a Swipe → List
      switch**, which is the point the shared acknowledgement was meant to
      guarantee; it **comes back after a process restart**, the acknowledgement
      living only in memory; and a successful server contact turns it off — feed
      reloaded, date reset to now, no strip any more.
      **Two cases could not be observed from the development machine**:
      pressing "Reload" — at launch the page arrives and turns the notice off
      before any press — and being offline, since aeroplane mode cuts `adb`,
      which goes over the same network. Both are covered in tests, and **the
      author confirmed they work correctly on his device** on 2026-08-08.
- [x] `GOAL-014-T13` **Regression fixed: the settings were re-emitting on every
      page.** Found on 2026-08-08 while looking for why the feed seemed to
      behave differently since the update.
      `GOAL-014-T03` makes the server-contact date be written on **every page
      received**, in the shared DataStore. But DataStore emits on every write of
      the **file**, not of the key, and `observeReadingSettings` was not
      filtering: the two feed ViewModels were therefore rebuilding their
      `ReadDetector` on every page, resetting the visibility timers in progress
      — an article looked at during a load was no longer marked read
      (SPECS.md §4.5). At launch, where several pages follow one another, the
      effect repeated.
      `distinctUntilChanged` on every flow derived from the DataStore, settings
      and session. 3 tests in `SettingsStoreTest`, including one that fails if
      the filter is removed — checked in both directions.
      **The rule is furthermore locked in where it shows** (5 tests of
      `DiscoverViewModelTest`), at the author's request: the server's first page
      does not reorder what the cache was displaying, removes nothing from it,
      and a re-emission of the cache does not re-interleave the feed. A feed
      that re-interleaves at launch is a defect, never an acceptable side effect
      (SPECS.md §4.2, rule 3).
      What the regression does **not** do, contrary to what one might have
      feared: it does not recreate the screen and triggers no request. The flows
      that drive navigation and session routing are `StateFlow`s, which do not
      re-emit an equal value.
- [x] `GOAL-014-T12` **The strip stops being an overlay.** The observation on a
      device had been misread: the "Open the article" button pushed out of the
      card by a long excerpt is not a defect — the content scrolls. The defect
      is that the strip **covered the end of that scroll**, hence the button
      where it stops: the only opening control of this mode (SPECS.md §4.7)
      became unreachable.
      Reproduced first by a test — long excerpt, `performScrollTo` on the
      button, measurement of the overlap — then fixed: the notice takes its
      place in the layout, below the feed, in both modes. A notice that lasts
      until acknowledged is not fleeting; only the refused-opening notice stays
      laid over, and it never meets the other. Swipe screenshots re-recorded and
      **looked at**.
      **Re-observed on a device** (Pixel 10 Pro, 2026-08-08): on an article of
      about a thousand characters, the card stops above the strip, and the "Open
      the article" button comes **fully** back on screen once the content is
      scrolled. The defect is furthermore held by a test that failed before the
      fix and passes after.

---

## GOAL-015 — Quiet launch: cache only, no position restore

**Status: DONE** — validated on a device

Changes SPECS.md §4.1, §5.1 and §5.3, settles §8 questions 10 and 11. Author's
decision of 2026-08-08, taken in the face of two defects observed on a device
the same day: the head of the feed differed from one launch to the next (a race
between disk and network, server order ≠ publication order — fixed by
`GOAL-005-T05`), and the remembered position rewrote itself at launch, the
article at the top of the first frames overwriting the true place. Rather than
fix the remembering — the fix was two thirds written — the author removes the
feature and automatic reloading with it: a stable feed that reopens identically
no longer needs a place kept for it.

### What was settled before writing

| Point | Decision | Reason |
|---|---|---|
| Reading restore (§5.3) | **Removed**, code included | Its memory drifted; on a feed that had become stable, it no longer paid for its complexity |
| Request at launch | **None**, except on an empty cache | The disk/network race decided the screen; an empty cache still primes itself on its own, an application with no content would be dead |
| Scrolling to the bottom of the known | **Keeps loading** | Scrolling is an action; only reloading the head requires the button |
| The staleness notice (GOAL-014) | Becomes **the** update reminder | Without automatic reloading, it is what says when the gesture is worth it |

### Tasks

- [x] `GOAL-015-T01` **Position restore disappears**, from the domain to the
      screen: `ReadingPosition`, repository, store, ViewModel, both screens'
      effects, Hilt bindings, the sign-out's `forget()`, and every test that
      proved them — seven files deleted, no dead code left. The `reading.*` keys
      of existing devices become orphans in the DataStore: harmless, never read
      again
- [x] `GOAL-015-T02` **Launch no longer queries the network**: the two
      ViewModels display the cache and stick to it; only an empty cache triggers
      the first load; scrolling paginates as before.
      **Observed on a device**: `feed.last_refresh_at` is strictly unchanged
      after a cold launch — no request goes out any more without a gesture
- [x] `GOAL-015-T05` **Cache interleaving stops depending on read articles.**
      Interleaving chooses each position by looking at its neighbours: applied
      **after** filtering the read ones, every article marked read left the set
      and redistributed all its neighbours. Since marking is automatic and
      continuous (SPECS.md §4.5), the feed reordered itself at every launch —
      three consecutive openings, three different heads, observed on a device.
      Interleaving is now applied **before** filtering: the order of the unread
      is a stable sub-order. 1 test
- [x] `GOAL-015-T04` ~~The purge can still redistribute the order~~ **Moot
      since `T08`**: interleaving bears on the whole cache, read ones included,
      and the purge only removes articles that are read **and** synchronised and
      more than a week old — which are no longer in the displayed window.
      Original statement: It removes
      articles from the set on which the interleaving is computed, and it runs
      once per process start, in a **race** with the first read of the cache. It
      only touches articles read more than a week ago and already synchronised:
      once the backlog is absorbed it finds nothing more, and the order settles.
      The transient regime, though, still moves.
      The test that established it was **removed rather than kept false**: it
      contradicted the marking test, and the trade-off between the two belongs
      to the specification, not to the code
- [x] `GOAL-015-T03` **Documentation**: SPECS §4.1, §4.6, §5.1, §5.3 and §8
      questions 10 and 11; ARCHITECTURE §9.7, which brings the four mechanisms
      together under a single principle; README; TASKS
- [x] `GOAL-015-T06` **The server's pages are brought back to publication
      order.** The server sorts its `reading-list` by **fetch** date: an article
      published two days earlier opened the first page. That order differed from
      the cache's, sorted by publication, and the launch screen depended on who
      answered first. Tie-breaking on equal dates identical to the cache's SQL
      sort. 2 tests, written red. Settled in SPECS §8 question 11
- [x] `GOAL-015-T07` **The cache bound applies after the filter, no longer
      before.** A cache whose two hundred most recent articles had been read
      returned an **empty** list: the screen believed itself empty and triggered
      the fallback load on every opening — the very request `T02` had just
      removed. Observed on a device: 283 articles in cache, 69 unread, zero
      displayed
- [x] `GOAL-015-T08` **Read articles stay in the feed until a reload**
      (SPECS §4.1). This is what closes the subject: interleaving chooses each
      position by looking at its neighbours, so anything entering or leaving the
      set redistributes the rest. Read articles left it at every session — the
      marking consumes them — and the feed seemed to re-interleave on its own.
      2 tests, including the one for the order left unchanged after marking.
      **Observed on a device**: three consecutive cold launches, identical head,
      `feed.last_refresh_at` unchanged. `GOAL-015-T04` and `T05` fall with it —
      the order no longer depends either on the read ones or on the purge
- [x] `GOAL-015-T09` **Flaky test repaired, discovered by CI.** The release of
      `v1.4.0` failed on
      `theStartupPurgeRemovesReadArticlesPastTheThreshold` — green locally, red
      on the runner. Cause: while adapting the tests to `T07`, a read
      **suspended** by the Room flow had been replaced by a synchronous SQL
      query, which can get ahead of a purge launched in the background. The
      flow, for its part, waits for Room's invalidation.
      The workaround had in any case become pointless: since `T08` the cache's
      flow returns read articles. The three tests concerned go back to it, and
      the production code loses the query that only the tests called. Replayed
      three times in a row from scratch

---

## GOAL-016 — Small illustrations stop being stretched

**Status: DONE** — validated on a device

Covers SPECS.md §4.3, added at the author's request. An illustration smaller
than the slot is today **enlarged** to fill it, and the result is blurry or
pixelated. The remedy asked for is the one used by certain social networks: the
same image as a background, blurred and cropped, and the image at its real size
on top.

### What the analysis establishes

| Observation | Where |
|---|---|
| The slot is fixed at **16/9**, never deduced from the image — otherwise the list would jump as each image arrived | `ILLUSTRATION_ASPECT_RATIO`, both screens |
| `ContentScale.Crop` always fills the slot: an image 200 px wide on a 1080 screen is **enlarged 5 times** | `ArticleIllustration`, both screens |
| The component is **written twice**, identically, as `FeedNotice` was before `GOAL-014-T06` | `DiscoverScreen`, `SwipeScreen` |
| Coil 3.4.0 gives the source size in `AsyncImagePainter.State.Success`: the threshold is measurable without an extra request | — |
| `Modifier.blur` requires **API 31**; the project goes down to **26** | `android-minSdk = "26"` |
| The test loader returns a **400 px square** image, flat: enough to prove the case without a network | `FakeIllustrations.kt` |

### What was settled before writing

| Point | Decision | Reason |
|---|---|---|
| When an image is "too small" | **When it would have to be enlarged**: source width < slot width | That is the exact definition of the defect. A numeric threshold would be arbitrary and would have to be defended; this one is measured |
| The background | The **same image**, cropped and blurred, over the **whole** slot; the sharp image centred on top, at its size | The slot stays full, with no empty band or frame. It is the process the author asked for, used by several social networks |
| Below API 31 | **Nothing changes**: today's stretching | `Modifier.blur` does nothing there, and a sharp duplicated background would be worse than the defect being fixed. A second mechanism — dominant tint — would cost its writing and its tests for a minority of devices |
| Scope | **Both** modes, after bringing the component together | It is written twice: fixing without merging means fixing twice and then diverging once |

### Tasks

- [x] `GOAL-016-T01` **The illustration becomes a single component**, in
      `presentation/feed`: it is today written identically in the two screens.
      A pure refactor — screenshots unchanged, screen tests unchanged
- [x] `GOAL-016-T02` **The component knows whether the image would be enlarged**:
      source size read from Coil's state, compared with the measured width of the
      slot. A pure decision, provable without rendering — 6 tests, including the
      three cases where nothing must be done
- [x] `GOAL-016-T03` **The blurred background**, under the image at its real
      size, and only when the enlargement would happen. Below API 31, today's
      stretching, untouched.
      The blurred copy slightly overflows the slot: `blur` fades all the way to
      the edges, and without that overflow the frame being chased would reappear
      at the periphery.
      **`Inside` and not `Fit`**, fixed after a first trial on a device: `Fit`
      fills the slot's smallest dimension, hence enlarges again — the foreground
      image stayed blurry, exactly the defect we claimed to be fixing. `Inside`
      shrinks what overflows but never grows beyond the native size: it is the
      only scale that invents no pixel
- [x] `GOAL-016-T04` **Roborazzi screenshots**: a small image and a large one,
      in both modes, light and dark. Four references, **looked at**.
      > **Two harness traps, fixed rather than worked around.** The first
      > screenshot used a flat solid colour: blurred or sharp, cropped or
      > fitted, it renders exactly the same pixels — it would have validated
      > anything.
      > The tiny illustration has therefore become **two-tone**, a light disc on
      > a dark background, where the sharp subject, the faded background and the
      > full slot can be told apart.
      > The "ordinary" fake image, moreover, measured 400 px: it too fell below
      > the width of a slot, hence under the blurred background. Every
      > screenshot in the repository would have illustrated the special case
      > while believing it showed the general one. Raised to 1,600 px, it becomes
      > the ordinary case again — and the existing references are **unchanged to
      > the pixel**
- [x] `GOAL-016-T05` **Observed on a device**, on a real article with a small
      illustration — validated by the author. The first trial in fact revealed
      the `Fit` defect there, which no screenshot had shown: the harness's fake
      image is square, a real thumbnail is not
- [x] `GOAL-016-T06` **Documentation**: SPECS §4.3 and §8 question 12,
      ARCHITECTURE §9.8, README, TASKS
- [x] `GOAL-016-T07` **Audit of the day's code**, requested by the author. No
      breach of the prohibitions of AGENTS.md §2 — no Android import in
      `:domain`, no `Dispatchers.` nor `System.currentTimeMillis()` outside
      their module, no hard-coded string in a Composable, no orphan `TODO`, no
      dead code.
      Two **convention** deviations fixed: `needsUpscaling` and
      `FeedStalenessWatcher` were public whereas the repository reserves
      `public` for shared composables and entry points, and `internal` for
      everything else.
      Three passages of ARCHITECTURE.md had become false with the removal of the
      reading position, including a whole section — see `GOAL-016-T03`

---

## GOAL-017 — An already-read article shows it

**Status: DONE** — validated on a device

Covers SPECS.md §4.1 and §4.5. **This Goal repairs a consequence of
`GOAL-015-T08`**, found while analysing the drifts since v1.2.0 at the author's
request.

Read articles now stay displayed until a reload — that is what makes the feed
stable at launch. But nothing distinguishes them: `isRead` exists in the display
model, the ViewModel keeps it up to date, and **no screen renders it**. As long
as the article disappeared at the next launch, its disappearance was the signal;
it now stays, indistinguishable from a fresh article, and one can reread without
knowing it.

### What was settled before writing

| Point | Decision | Reason |
|---|---|---|
| The mark | A **flag**, at the top of the card, over the illustration | The author's choice. It is spotted while scanning the screen, where a tick in the feed line asks to be read |
| Its position | **Always the same**: at the top of the card, whether the article has an illustration or not | That is what avoids the second rendering, and the second set of screenshots, that a flag placed in the corner of the image would have imposed |
| The clash of meaning | **Owned**: the bookmark usually says "favourite" | Reported to the author, who decides. To be reopened if FreshRSS's followed articles are one day added — the two would fight over the symbol |
| Card opacity | **Not retained** | It would also dim the title, and the AA contrast of SPECS.md §7.1 would have to be remeasured on every state |

### Tasks

- [x] `GOAL-017-T01` **The flag**, a shared component in `presentation/feed`:
      shape, contrast over any image, description for the screen reader
- [x] `GOAL-017-T02` **Placed in both modes**, at the top of the card, with or
      without an illustration. The container takes the full width: without that
      it sized itself to the flag alone when the article had no image, and the
      right alignment had nothing to lean on — the flag appeared stuck to the
      left, seen on a screenshot
- [x] `GOAL-017-T03` **Roborazzi screenshots**: read and unread, with and
      without illustration, light and dark — two references, **looked at**, and
      it is the first one that revealed the alignment defect
- [x] `GOAL-017-T04` **Observed on a device**: screenshot taken two seconds
      after a cold launch, the flags are already there. It is this observation
      that revealed the projection defect — the author had reported "a small
      delay before the flag appears", and it was in fact a false state
- [x] `GOAL-017-T07` **The flag stops shifting the card, and appears with a
      fade.** Reported by the author: on an article **without** an
      illustration, the flag took up height in the vertical flow and pushed the
      content down. It now floats over the whole card, out of the flow — and out
      of the scroll in Swipe mode, where it would otherwise have slid with the
      text although it qualifies the whole article.
      It appears with a fade: the read state establishes itself during reading,
      and a flag that pops up on the card being read draws the eye to itself
      when all it does is record a fact
- [x] `GOAL-017-T06` **The flag is dimmed**, at the author's request: at full
      opacity it drew the eye to the least interesting thing in the feed. The
      opacity applies to the whole surface, tick included — dimming only the
      background would have left the tick at full intensity, that is, the
      opposite of the result sought
- [x] `GOAL-017-T05` **Documentation**: SPECS §4.5, ARCHITECTURE §9.9 — which
      keeps the lesson of the tests, not just the fix — README, TASKS

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

## GOAL-019 — Automatic marking becomes optional

**Status: DONE** — five tasks, five commits, on the branch
`worktree-agent-a1efdfaeef67c4f8e`. Verification passed and observed before
every commit, `:app:verifyRoborazziDebug` included at `T04`, where the settings
screenshots were re-recorded **and looked at** in light and dark.

No observation on a device: the phone was not available (see the notice under
"Current phase"). The guarantee rests on the unit tests, the screen tests and
the screenshots.

Requested by the author. SPECS.md §1 lays down "reading is scrolling" as a
principle, and §4.5 makes it a mechanism with no escape: whoever goes through
their feed without reading it consumes their articles without meaning to, and
the reload carries them away. An **On / Off** switch makes the rule optional.

### What is settled before writing

| Point | Decision | Reason |
|---|---|---|
| Where the setting lives | In `ReadingSettings`, alongside the two thresholds | Same reader — the read detector — and same moment of reading. A separate flow, like the reminder's, would make anyone applying only one of them observe two sources |
| Default value | **On** | It is today's behaviour, and the one SPECS.md §1 describes. An existing installation must see nothing change |
| What switching off stops | Detection by visibility, and **that alone** | Opening an article still marks it read (SPECS.md §4.7): that is a deliberate gesture, not automatic marking. The two would be conflated if the switch also carried opening away |
| The two thresholds, once switched off | **Displayed, greyed out** | Hiding them would make two settings vanish without saying why; leaving them active would offer to adjust what no longer applies |
| The pending markings queue | Unchanged | What is already marked remains to be transmitted. Switching marking off does not cancel past readings |

### Tasks

- [x] `GOAL-019-T01` `ReadingSettings.autoMarkAsReadEnabled`, on by default, and
      its passage through `coerced` — `:domain` tests
- [x] `GOAL-019-T02` Persistence: DataStore key, `observeReadingSettings`
      returning it, `SettingsRepository.setAutoMarkAsReadEnabled` — store tests
- [x] `GOAL-019-T03` **The two feed ViewModels stop feeding the detector** when
      the setting is off, and take it up again without a restart when it is
      switched back on. Opening an article still marks
- [x] `GOAL-019-T04` Switch in the settings screen, thresholds greyed out
      underneath — screen tests and Roborazzi screenshots **looked at**
- [x] `GOAL-019-T05` Documentation: SPECS.md §4.5 and §6, TASKS.md

### What the implementation taught

| Point | Observation |
|---|---|
| The detector switched off | It becomes `null` rather than being paired with a boolean: the absence of a detector cannot be forgotten, a flag beside it can. The screens keep sampling visibility — to no effect, and that is what makes switching back on immediate |
| The timer in progress | Switching off throws it away with the detector. Letting it complete would mark an article after the user asked for that to stop: a test observes it |
| `DiscoverViewModelTest` | Adding the setting's cases there pushed it past Detekt's `LargeClass` threshold. They live in `DiscoverAutomaticMarkingTest`, which has its own starting state — no rule relaxed, no test given up |
| The thresholds' texts | They dim along with the slider (`0.38`, the opacity Material 3 does not expose). A figure left bright above a greyed-out track would read as a setting still being applied |

### Loose ends

- The "Next task" line of "Current phase" still cites `GOAL-019-T01`. It has not
  been touched here: GOAL-020 and GOAL-021 are being carried out in parallel on
  other branches, and three agents rewriting the same sentence would produce a
  merge conflict. To be taken up once the three Goals are merged.
- **ARCHITECTURE.md has not been updated**: it fell outside the scope entrusted
  to this branch. Nothing architectural changed — no package, no dependency, no
  splitting: a field was added to `ReadingSettings` and a key to the DataStore.
  §5.1 would nonetheless gain from mentioning `reading.auto_mark_as_read`, to be
  done in a pass where the file is free.

---

## GOAL-020 — The card can be shared, the flag goes, the swipe opens on a tap

**Status: DONE**

> ⚠️ **Nothing has been observed on a device**, none being available
> (see the warning in Current phase). What the tests cannot say, and what will
> have to be looked at on the first real run: **the system share sheet** —
> that it opens, what it offers, and the look of the "title then link" text
> once pasted into a messaging app.
> `ArticleShareIntentTest` establishes the content of the intent, not what
> Android does with it.

Three requests from the author on the same surface — the article card — hence a
single Goal: they cross in `DiscoverScreen` and `SwipeScreen`, and handling them
separately would amount to fixing the same layout twice.

### What is settled before writing

| Point | Decision | Reason |
|---|---|---|
| The read-article flag | **Removed**, with its component, its tests and its screenshots | The author's request: GOAL-017 had put it there so that one would not reread without knowing, and use has shown that it draws the eye to the least interesting thing in the feed. The dimming of `GOAL-017-T06` was already going that way; the author goes all the way |
| What the removal does not touch | `ArticleUiModel.isRead` and its projection | The read state remains what decides the marking and the purge (SPECS.md §5.4). Only its **representation** disappears. Removing the field would bring the rule down with the decoration |
| Sharing | The **native chooser** (`ACTION_SEND` via `createChooser`), in both modes | What the author asks for, and the only form that engages no third-party service (SPECS.md §7.4): the application does not choose the destination, it hands over to the system |
| What is shared | The title then the original URL | A bare URL does not say what is being sent. The excerpt, for its part, is shortened by us: passing it on would share our truncation as content |
| An article with no link | Cannot be shared, and the button does not appear on it | Same rule as opening (SPECS.md §4.7). Sharing a title alone would send a message with no object |
| The "Open the article" button in Swipe | **Removed**: the whole card opens the article | The author's request. `OpenAction`'s KDoc argued the opposite — a tap taken for an opening during a hesitant swipe. Compose tells `tap` from `drag`: the horizontal gesture is not consumed by the click, and that is what a test must observe |
| An article with no link in Swipe | The card is not clickable, and says so | What the "no link" mention already does. It stays |
| SPECS.md §2 | Sharing **leaves** the out-of-scope list | It figured there under "social sharing, comments, annotations". A system chooser is none of the three, but the exclusion was written broadly enough to cover it: lifting it explicitly is better than interpreting it |

### Tasks

- [x] `GOAL-020-T01` **The flag is removed**: `ReadFlag`, its calls in the two
      screens, its tests, its strings and its two screenshots.
      `FeedTestTags` goes with it — it carried nothing but `READ_FLAG`.
      SPECS.md §4.5 and ARCHITECTURE.md §9.9 are handled **here** and not in
      `T06`: they describe the flag, and leaving them one task longer would have
      made the documentation lie about code already deleted. The lesson of §9.9
      — "testing the screen does not test what feeds it" — is kept in
      ARCHITECTURE.md §8.3, where it holds for any field of `ArticleUiModel` and
      not for the flag alone.
      `verifyRoborazziDebug` passes without re-recording: no remaining
      screenshot carried a read article
- [x] `GOAL-020-T02` `ArticleSharer` in `presentation/browser`: the decision —
      what gets shared, what stays quiet — provable on the JVM, the launching of
      the intent isolated behind a functional interface, like
      `CustomTabLauncher`.
      Two departures from the model, both deliberate: `ArticleShareOutcome` has
      only two values — no equivalent of `NoBrowser`, the chooser being provided
      by the system and saying itself that no application can receive — and
      `isSupportedWebLink` goes from `private` to `internal` rather than being
      copied, so that the two scheme rules cannot diverge. The template of the
      shared text is a resource,
      given to the sharer by `rememberArticleSharer`: the composition stays
      provable on the JVM, the wording stays translatable
- [x] `GOAL-020-T03` **Share button on the card**, in both modes, a 48 dp target
      and a description for the screen reader — screen tests.
      `ArticleShareButton` lives in `feed/`, like `RefreshButton`: the same
      action on both sides. Placed **below** the card's texts and not on the
      line with the feed and the date — up there, a screen reader would announce
      the control before the title of the article it shares.
      `onArticleShare` has **no default value** on either screen: an implicit
      `{}` would leave a button visible and inert.
      Roborazzi references re-recorded and **looked at** in this same
      increment, rather than deferred to `T05`: in between, the visual
      verification would have been red without that meaning anything
- [x] `GOAL-020-T04` **In Swipe, the whole card opens the article** and the
      button disappears. A test observes that the swipe still works —
      `swipingLeftStillWorksWithAClickableCard`: the page changes **and** the
      opening is not triggered, which is exactly what `OpenAction`'s KDoc
      feared. `swipe_open_article` survives as an `onClickLabel`: a touch
      surface announces nothing by itself. The two tests of the staleness strip
      now target the share button, which has become the only control of this
      mode
- [x] `GOAL-020-T05` Roborazzi screenshots of both modes, light and dark,
      **looked at**. The re-recording happened in `T03` and `T04`, along with
      the change it records; what remained here was the state no image showed —
      **an article with no link in Swipe**,
      `balayage-article-sans-lien`, where one can see that there is no share
      button and that the mention survives the removal of the opening button.
      Two screenshots deleted by `T01`
      (`discover-articles-lus`), 26 modified, 2 added: the share icon stands out
      in both themes, on a card as on an illustration
- [x] `GOAL-020-T06` Documentation: SPECS.md §2, §4.3, §4.7, §4.8,
      ARCHITECTURE.md §9.9 and §9, TASKS.md. Two pieces were handled earlier,
      where the code they described was disappearing: ARCHITECTURE.md §9.9 in
      `T01` and the role of the `browser/` package (§9) in `T02`. SPECS.md §4.5
      was added to the list, which described the flag and was listed nowhere.
      §7.4 has not moved and had no reason to: a system chooser opens no
      connection from the application

---

## GOAL-021 — The documentation switches to English, the interface becomes bilingual

**Status: DONE**

Requested by the author: the repository's documentation is **replaced** by its
English translation — there are not two versions left, which would diverge at
the first commit. The interface, for its part, becomes **bilingual**: English by
default, French kept.

### What is settled before writing

| Point | Decision | Reason |
|---|---|---|
| Scope of the translation | **All** the `.md` files of the repository, `docs/` included | The author's answer. Half-translated documentation forces you to guess where to look |
| Is French kept? | **No** | "Replace", literally. Two versions of AGENTS.md would diverge with nothing to signal it |
| PROMPT.md | Translated, and stays frozen | It preserves the initial intent: its language changes, not its content |
| The interface | **Bilingual**: `values/` in English, `values-fr/` in French | The author's answer. English by default, because `values/` is what any device whose language is not provided for receives |
| The Roborazzi screenshots | **Unchanged** | The harness already pins `@Config(qualifiers = "fr-rFR…")`: the references stay French, and moving the strings to `values-fr/` does not touch them. This is not a workaround — it is what makes the language change verifiable without re-recording 58 images |
| How `values/` is proven | One screen test in `en-rUS` | Without it, a string forgotten in the translation would only show up at run time on an English-speaking device. The screenshots, for their part, only look at French |
| KDoc and commit messages | **Stay in French** | AGENTS.md §9 requires it, and the author only asked for the documentation. To be reopened if he wishes |

### Tasks

- [x] `GOAL-021-T01` Translate `README.md`, `AGENTS.md`, `CONTRIBUTING.md`,
      `CLAUDE.md`, `PROMPT.md` and `docs/freshrss-api.md`. Those six are touched
      neither by GOAL-019 nor by GOAL-020: they can be translated in parallel
- [x] `GOAL-021-T02` **Bilingual interface**: the six string files moved to
      `values-fr/`, `values/` received English, and `EnglishStringsTest`
      observes in `en-rUS` that nothing is missing there.
      > **The move broke three screen test classes, and that was the useful
      > part.** `DiscoverScreenTest`, `LoginScreenTest` and `SwipeScreenTest`
      > assert **literal** labels without pinning a language: they had always
      > run on the default locale, which happened to be French. It no longer
      > is. They are now pinned to `fr-rFR`, like the screenshot harness and
      > like the three test classes that already were.
      > The guard was mutation-checked, and the **first** mutation was the
      > wrong one: deleting an English string breaks compilation, since the
      > main code references `R.string`. The defect that can actually happen is
      > a string **left in French** in `values/` — mutated that way, the test
      > fails as it should
- [x] `GOAL-021-T03` Translate `SPECS.md`, `ARCHITECTURE.md` and `TASKS.md`.
      **After** GOAL-019 and GOAL-020, which modify them — translating first
      would have meant translating twice. Three agents in parallel, one file
      each, each checked mechanically against its original: headings, table
      rows, checkbox counts of every kind, blockquote blocks and `GOAL-0XX`
      occurrences all match
- [x] `GOAL-021-T04` SPECS.md §7.3 rewritten: the interface is no longer "in
      French" but bilingual, English by default. The section also records the
      consequence that is not obvious — the screenshots being pinned to
      `fr-rFR`, they verify **French**, and a separate `en-rUS` test is what
      keeps `values/` complete

---

## GOAL-022 — A local test stack, and the defects it revealed

**Status: DONE** — four defects found, the share sheet validated

The phone not being available (see "Current phase"), the author asked for a test
stack to be set up on the machine: an Android emulator and a FreshRSS instance
in a container. It was set up, the application was installed on it, and **the
login failed on the first attempt** — on a defect that none of the repository's
tests could see.

### The defect

```
java.net.UnknownServiceException: CLEARTEXT communication to 10.0.2.2
not permitted by network security policy
```

SPECS.md §3.1 says the `http://` scheme **stays accepted**, "self-hosted
instances on a local network are a real case", and the login screen even goes as
far as displaying the warning "this connection is not encrypted". The manifest,
for its part, allows cleartext traffic nowhere: since `targetSdk 28`, Android
refuses it by default. **No `http://` instance was reachable**, and the
application displayed "the server is not responding" — a false diagnosis, which
would have sent the user looking for the fault at their end.

That is exactly the pattern of `GOAL-001-T22`: three defects that 487 tests and
30 screenshots had not seen, and that a single real run showed. The promise was
written in SPECS.md, tested nowhere, and false.

### Tasks

- [x] `GOAL-022-T01` **Cleartext traffic is allowed**, as SPECS.md §3.1
      promises, and a test observes it.
      > **The first safeguard written guarded nothing.** The fix first went
      > through a `network_security_config.xml`, and the test meant to hold it
      > read `NetworkSecurityPolicy.isCleartextTrafficPermitted`. It passed —
      > but it **also** passed after the configuration was deleted: Robolectric
      > allows cleartext whatever the manifest says. Checked by mutation before
      > believing it, exactly what AGENTS.md §8 demands of a tool that never
      > reports anything.
      > The authorisation therefore goes through `android:usesCleartextTraffic`,
      > whose flag reaches `ApplicationInfo.flags` and **fails** when it is
      > removed. Mutation observation: removed → `FAILED`, put back →
      > `SUCCESSFUL`.
      > Then observed on the emulator: successful login against
      > `http://10.0.2.2:8088`, real feed of 134 articles from 8 sources
- [x] `GOAL-022-T02` **`envTest/` directory**, requested by the author: the
      `test-stack.sh` script and the configuration of each element — `config.env`
      (ports, credentials, AVD definition) and `feeds.opml` (the feeds).
      Three commands: `init` builds, `run` restarts, **`stop` shuts down**.
      The rule "shut down at the end of every Goal" is recorded in
      AGENTS.md §5.3, which shifts the old §5.3 to §5.4.
      > **The script had a defect that only running it showed.** `init` did all
      > its work — AVD, container, user, feeds, API checked, application
      > installed — then **never gave the hand back**: the emulator stayed a
      > child of the script, which waited for it on exit. A subshell's `&` is
      > not enough; `setsid` and `disown` were needed.
      > The same pitfall lay in wait for `run`'s message:
      > `actualize-user.php` announces "failed!" when it had **nothing** to
      > refresh, the TTL not having elapsed, which is the common case of two
      > `run`s close together. Relayed as is, it would have had a breakdown read
      > into every restart.
      > `init`, `run` and `stop` were executed, and their output observed
- [x] `GOAL-022-T03` **The whole journey observed on the emulator**, once
      GOAL-019 and GOAL-020 were merged: login, feed, sharing, clickable card in
      Swipe, marking switch.
      > **An alignment defect found, and it is the third one this stack
      > reports.** On a card **with no illustration and with short text**, the
      > share button did not sit at the edge of the card but at the edge of the
      > **text**: the inner column hugged its content, and the `align(End)` had
      > only that width to lean on. With an illustration, the illustration
      > imposes the full width and the defect disappears — which explains why
      > none of the 26 screenshots redone by GOAL-020 showed it: their articles
      > all have either an image or a long enough text.
      > **The read-article flag had been caught through the same door in
      > `GOAL-017-T02`.** Twice the same trap: a dedicated screenshot
      > (`discover-article-court`) now guards it, and it has been checked by
      > mutation — removing the `fillMaxWidth` makes it fail.
      > Fixed in both modes. `ArticleText` was extracted from
      > `ArticlePage`, which the comment was pushing past its allowed length.
      > **Observed afterwards, all the way through**: login, feed interleaved
      > over eight sources, illustrations on a blurred background, no flag left,
      > share button on every card, marking switch that greys out its
      > thresholds, switch to Swipe, swipe moving to the next article, and a tap
      > on the card that does open the custom tab.
      > **A second defect of the script along the way**: the author's phone
      > appeared on the network in the middle of the validation, and `adb`
      > answered "more than one device" on every call. The script now targets
      > the emulator **by name** — which also closes the most serious door, that
      > of a test build installed on somebody's phone.
      > **Then on the author's device** (Pixel 10 Pro, Android 17), at his
      > request, against his real FreshRSS instance: session kept across the
      > reinstallation, dark theme and dynamic colour, no flag left, share
      > button tucked to the edge of every card.
- [x] `GOAL-022-T04` **The system share sheet**, the one piece no test could
      reach — `ArticleShareIntentTest` establishes the content of the intent,
      not what Android does with it. **Validated by the author on his device**
      on 2026-08-09. The first attempt had been interrupted rather than carried
      through: he was using his phone at the same moment, and fighting him for
      the screen would have proved nothing

---

## GOAL-023 — The card tightens up: source and date in the footer, discreet sharing

**Status: DONE** — validated on the emulator, not on a device (the author's request)

Requested by the author after seeing `GOAL-020` on a device. Two adjustments to
the List mode card, and they go together: the share button today takes up **a
line of its own** at the bottom of the card, while the source and the date take
up another at the top. Brought together into a single card footer, they give a
line back to the content.

### What is settled before writing

| Point | Decision | Reason |
|---|---|---|
| The source and the date | Move down into the **card footer**, on the same line as sharing | The author's request. The title becomes the first thing read, which a card's hierarchy wants anyway; the provenance stays present, it stops being announced before the subject |
| What a screen reader sees | The order follows the layout: title, excerpt, then provenance and control | It is a gain, not a loss: the control was already last, and the source stops preceding the title |
| The size of the sharing | The **drawing** of the icon goes down to the size of the text; the **touch target** stays at 48 dp | That is the compromise, and it has to be said: "more compact" cannot mean "smaller to touch", SPECS.md §7.1 sets 48 dp. What is gained is the whole line the button took up, not the pixels of its sensitive surface |
| Scope | The card footer in **List** mode; the reduced icon in **both** modes | The share component is shared (GOAL-020-T03) and shrinking it on one side only would make it diverge. The move, for its part, only makes sense in List: full screen the card scrolls, and a provenance placed under a 1,400-character excerpt would be below the fold |

### Tasks

- [x] `GOAL-023-T01` **The share icon at the size of the text** — 18 dp, the
      height of a `labelMedium` capital — the touch target staying at 48 dp.
      The two measurements are now two distinct constants, because they do not
      measure the same thing: what the eye sees and what the finger reaches.
      Conflating them would have forced a choice between an icon that crushes
      the card footer and a target too small for SPECS.md §7.1
- [x] `GOAL-023-T02` **Source and date in the card footer**, on the sharing
      line, in List mode. `weight(1f)` on the text and not a spacer: it is up to
      the feed's name to shorten itself when it is long, not up to the control to
      be pushed out of the card.
      > **A detail seen on the screenshot, and fixed.** The footer of an article
      > **with no link** has no button, so it shrank to the height of its text
      > while the others held the 48 dp of the touch target.
      > In a list, that gap reads as a template defect rather than as the
      > absence of a control: `heightIn` gives the footer the same height
      > everywhere
- [x] `GOAL-023-T03` **Roborazzi screenshots looked at**, light and dark, in
      both modes — 30 references modified. Then **observed on the emulator**:
      the title opens the card, the provenance and the sharing hold a single
      line in the footer, and the card is visibly shorter.
      Not on the author's device, at his request
- [x] `GOAL-023-T04` **The card tightens up further**, the author having found
      too much air under the icon and under the title once the previous version
      was installed on his device. Three removals:
      the footer's `heightIn` — **laid down the day before** to give the same
      height to the footer of an article with no button, and removed because the
      48 dp target centred on a 16-point line left an empty band; between a
      regular template and a tightened card, it is the tightening that was
      asked for.
      The card's bottom margin, brought from 16 down to 4 dp — the button
      already brings its own emptiness underneath it. And the spacing between
      the title and the excerpt, removed: their line spacing already separates
      them, and the footer takes that gap over since it changes in nature, not
      in paragraph
- [x] `GOAL-023-T05` **The icon at 16 dp, and in Swipe on the title line**,
      requested by the author after a trial on his device. The drawing therefore
      goes 24 → 18 → 16 dp in three steps: it is the subject of neither card,
      and each step still weighed too much.
      In Swipe it left the bottom of the card, where it fell after an excerpt
      that can run to 1,400 characters — hence below the fold on one screen in
      two, whereas it is the only visible control of this mode.
      `Alignment.Top` and not a centring: on a three-line title, a centred
      button would sit at a height that depends on the length of the text
- [x] `GOAL-023-T06` **Sharing joins the source line in Swipe**, and the bottom
      margin of List mode falls to zero. Two fixes from one and the same
      exchange with the author.
      In Swipe, the control has occupied three places in three attempts: the
      bottom of the card, the title line, then the source line — the same
      association as in List mode, except that here that line opens the card
      instead of closing it.
      In List, the bottom margin goes from 4 dp to **nothing**: the 48 dp touch
      target surrounds a 16 dp drawing, so it already leaves sixteen points
      below the stroke, exactly the margin of the other three sides. **That is
      the floor without giving anything up** — going lower would mean shrinking
      the target below the 48 dp of SPECS.md §7.1. The card **with no link**
      keeps its margin: with no button to carry it, its last line would touch
      the edge.
      `Spacing.none` is added to the scale so that this zero reads as a decision
      and not as an oversight

---

## GOAL-024 — Refreshing twice was needed to see new articles

**Status: DONE** — reproduced by measurement, fixed, and observed fixed

Reported by the author on his own device: new articles only appeared after
**two** refreshes. The defect was real, it had been there since GOAL-008, and no
test could see it.

### What the measurement established

Refreshing does not tell the server what has just been read before asking it for
the feed again. Read marks are batched for up to **five seconds** before being
transmitted (SPECS.md §8, question 4), so a refresh made inside that window
queries a server that still believes those articles unread. It returns them,
they take their places back in the page of forty, and the genuinely new articles
do not appear. The second refresh — the batch having gone by then — shows the
new state.

Measured on the emulator, against a real FreshRSS instance:

| Moment | Unread, server-side |
|---|---|
| Before reading | 162 |
| **At the instant of the refresh** | **162** — the server had not been told |
| Twelve seconds later | 158 |

And after the fix, on the same course: 158 before reading, **156 immediately
after the refresh** — the marks left *with* the refresh instead of trailing it.

### Why no test caught it

The two ViewModels each called `flush()` **once**, at construction — the startup
replay of SPECS.md §4.5 — and `refresh()` called `articleRepository.refresh()`
straight away. Every existing case counted calls, and a count says that a thing
happened, never that it happened **before** another: `flushCallCount == 1` and
`refreshCallCount == 1` are both true in the faulty order.

`FakeArticleRepository.onRefresh` is what closes that hole — a hook that
observes the state of the world **at the moment** the refresh leaves. Both
modes have their case, because the same rule lives in two ViewModels and a fix
applied to one side only would make them diverge, which has happened often
enough in this repository (ARCHITECTURE.md §9.6).

### Tasks

- [x] `GOAL-024-T01` **Refreshing transmits pending marks first**, in both
      modes, and waits for them. This is the only place in the code where
      `flush` is **awaited** before anything else: elsewhere it leaves without
      its outcome being watched, marking being optimistic. Here its result
      decides whether what the server returns is right. A failure still blocks
      nothing — the queue keeps what it holds, and the refresh happens anyway
- [x] `GOAL-024-T02` **Two cases that fail without the fix**, mutation-checked:
      remove the `flush()` and both go red, put it back and both go green

---

## GOAL-025 — An empty feed stops being a dead end

**Status: DONE**

Reported by the author, in two sentences that describe the same corner: *"if no
article is in focus, then fetch the server"*, and *"allow pull to refresh even
with no article"*.

### What the screen does today, and why it is a dead end

One reaches an empty screen by reloading once everything has been read: the
reload replaces what is displayed (SPECS.md §4.6) and the server has no unread
article left to give. From there, the feed offers **no way out**. The pull is armed on the list
alone (`ArticleList`), so a screen with no list has no gesture; the refresh
button on the title row is still there, which is why this is a discomfort and
not a lockup. And nothing asks the server again by itself: the "empty cache"
exception of SPECS.md §5.1 is evaluated **once**, on the first sample of the
cache (`hasDecidedBootstrap`), and never again. Read everything, come back —
and the screen says "you are up to date" until you find the button.

### The two decisions this Goal takes

**A request leaves when the screen is shown with nothing on it.** SPECS.md §5.1
already carries the rule — *no request leaves as long as there is something to
show* — and its converse was only ever applied at launch. It is now applied
every time the feed comes to the foreground.

It is attached to a **discrete fact**, the screen coming to the foreground, and
not to the state of being empty: a server with nothing to give leaves the screen
empty, which would ask again, and again. Arriving on the feed, coming back from
Settings, waking from sleep — each is worth one attempt, never two.

`refresh()` and not `load()`: the cursor of a finished feed leads nowhere, and
`refresh()` alone transmits the pending marks before querying (GOAL-024) —
which is precisely the situation, since one gets to an empty screen by having
just read everything.

### Tasks

- [x] `GOAL-025-T01` **An empty screen asks the server when it is shown**, in
      both modes: the two ViewModels gain the entry point, the routes call it
      from the lifecycle. Guarded on the phase — a load already in flight, a
      failure with its own "Retry", a refresh under way ask nothing more
- [x] `GOAL-025-T02` **The pull works on a screen with no article** (List mode).
      The gesture needs something that dispatches scroll to be detected: a
      `LazyColumn` of a single item sized with `fillParentMaxSize`, since a
      plain `Box` would have made the gesture **inert** — worse than absent.
      `fillParentMaxSize` also keeps the centring, so the 58 captures did not
      move
- [x] `GOAL-025-T03` **Recorded**: SPECS.md §4.6 (the pull no longer stops with
      the list), §5.1 (the exception is read at every foregrounding, not only at
      launch) and §5.3 (a feed left empty has no place to find again);
      ARCHITECTURE.md §9.9

### What was observed on the emulator

The route wiring is the one thing **no case covers**: a `LifecycleResumeEffect`
connected to nothing would leave all six green. It was therefore run against the
local stack ([envTest/](./envTest/README.md)), on a real FreshRSS instance whose
204 articles were marked read through the API.

| Step | What the screen did |
|---|---|
| Reload on a fully-read feed | The display empties: "Nothing to read right now" |
| 6 articles made unread server-side, then Settings and back | The feed comes back with them — **the foregrounding fetched** |
| Same again, then a pull on the empty screen, with no foregrounding | The articles appear — **the gesture alone did it** |

The second and third steps isolate each mechanism: nothing else could have
triggered the request. Swipe mode was not run through the same course — it goes
through the *same* helper, `AskTheServerWhenShownEmpty`, and its own case
guards the ViewModel side.

### What the mutations established

Each guard was removed and put back, since a guard that cannot fail is
decoration (AGENTS.md §8):

| Mutation | Cases that went red |
|---|---|
| Guards removed from `onScreenShown` | the three refusals — something to show, first load in flight, failure with its own "Retry" |
| `refresh()` removed from `onScreenShown` | list mode, and the "one attempt per foregrounding" case |
| Same, Swipe side | swipe mode alone — which is the point of having both |
| `PullToRefreshBox` removed from `PullableMessage` | the two pull cases, the tagged node still being there |

The Detekt threshold `TooManyFunctions` went from 11 to 12 along the way, with
its reason in `config/detekt/detekt.yml`: a screen's ViewModel is a **collector
of gestures**, its method count tracks the commands the screen offers, and
splitting it in two to satisfy a counter would make two halves of one state.

---

## GOAL-026 — Killing the app resurrected the feed one had just emptied

**Status: IN PROGRESS**

Reported by the author immediately after GOAL-025, and revealed by it: *"if I
empty the feed I get the no-more-articles message, but if I kill and relaunch I
get the last set of articles back."*

### What the reading of the code established

**The reload empties the display, never the cache.**

| Where | What the code does |
|---|---|
| `DefaultArticleRepository.kt:195` | `cache.save(page.articles)` — an upsert. Nothing ever deletes |
| `DefaultArticleRepository.kt:113` | The cache is re-read **read articles included** (GOAL-015, ARCHITECTURE.md §9.7) |
| `DiscoverViewModel.kt:161` | On launch, a non-empty cache is displayed and nothing is asked |

So: reload, the server has nothing unread left, the screen empties. But the
forty read articles are still in the database. Kill, relaunch — the database
answers, the screen brings them back, and the rule of GOAL-025 stays silent
since there **is** something to show.

Two things made it worse. Since GOAL-020 there is no read flag any more, so
those resurrected articles are **indistinguishable** from genuinely unread ones.
And the doc comment on `observeCachedArticles` has been promising since
GOAL-015 that the list holds "until the next requested reload, **which alone
renews it**". The promise was written; the cache never implemented it.

### The decision

A **successful** reload purges the articles that are read *and* synchronised,
by way of the existing `purgeAllRead()`. That query spares exactly the two
things that must never disappear (SPECS.md §5.4): what is unread, and what is
still **waiting to be transmitted** — those rows carry the local memory of
"already read", and dropping them would make what one has just read come back
as new (`ArticleDao.kt:103`).

The purge runs **after** the save, not before: `upsertPreservingLocalReadState`
reads the existing read state from those very rows, and purging first would make
a returned article lose its memory and come back unread.

The owned price: after a reload, one can no longer scroll back to something read
before it. That is what SPECS.md §4.6 has always said — the reload replaces what
is displayed — but it is not what the application did.

**Loading the next page purges nothing.** Tying the purge to pagination would
erase the feed under the reader as they scroll; a case guards that.

### Tasks

- [x] `GOAL-026-T01` **A successful reload renews the cache**, with its cases:
      what is read goes, what is unread stays, a pending mark is spared, and
      pagination purges nothing
- [-] `GOAL-026-T02` **Record it**: SPECS.md §4.6 and §5.1, ARCHITECTURE.md §9.7

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
