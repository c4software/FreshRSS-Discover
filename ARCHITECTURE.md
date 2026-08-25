# ARCHITECTURE.md — Technical architecture

The **technical** source of truth: how the application is designed.

The *what* is in [SPECS.md](./SPECS.md), the *order* in [TASKS.md](./TASKS.md),
the *working rules* in [AGENTS.md](./AGENTS.md). The reference for the remote API
is in [docs/freshrss-api.md](./docs/freshrss-api.md).

> This document describes the **intended** state, and explicitly flags what does
> not exist yet. §9 records what is actually in the repository: that is the
> section to be updated at every step, and a divergence between the two is an
> inconsistency to be dealt with, not ignored.

---

## 1. Module split

```
:domain   Pure Kotlin/JVM — decides
:app      Android — displays, stores, calls
```

### 1.1 `:domain` does not have the Android SDK on its classpath

This is a compilation constraint, not a convention: the module is a
`kotlin("jvm")`, the Android SDK is not there. An Android dependency therefore
becomes a **compilation error**, and not a review remark one may forget to make.

What this guarantees:

- the domain is tested in pure JVM, without Robolectric or an emulator — tests
  measured in milliseconds run on every save;
- no business rule can depend on a `Context`, a `Cursor` or a
  `SharedPreferences`;
- the interleaving algorithm (SPECS.md §4.2), which is the heart of the
  application, remains a pure function that can be tested exhaustively.

`kotlinx-coroutines-core` is `:domain`'s only dependency. `Flow` and `suspend`
are part of the domain's vocabulary; that is not an Android dependency.

### 1.2 Why two modules and not three

A separate `:data` module would be defensible. It would bring no constraint here
that `:domain` does not already carry: it is `:domain` that defines the
interfaces, and the direction of the dependencies is therefore already imposed.
A third module would cost Gradle configuration time on every build for a
guarantee we already have.

This choice is reconsidered if `:app` becomes hard to navigate.

---

## 2. The flow of a piece of data

```
UI (Compose)
 ↓ immutable state
ViewModel
 ↓ suspending call
Use Case                    :domain
 ↓ interface
Repository (interface)      :domain
 ↓ implementation
Repository (implementation) :app/data
 ↓
FreshRssApi  ·  Room  ·  DataStore
 ↓
HTTP (Ktor)  ·  SQLite  ·  file
```

The dependencies **all point towards `:domain`**. No `:domain` class knows Ktor,
Room, DataStore or Compose.

> **One tier of this diagram is empty, and that is deliberate.** The repository
> today has **no use case class**: the ViewModels call the repository interfaces
> directly. A use case that merely relayed a call would be the anticipation that
> AGENTS.md §2 forbids. The decisions that would have justified that tier already
> live in `:domain` as pure functions — `interleaveBySource`, `ReadDetector`,
> `ReadTransmissionScheduler` — called by whoever needs them. The tier stays in
> the diagram because it is the place reserved for the first rule that will
> coordinate several repositories.

### 2.1 What must stay confined to the FreshRSS layer

None of the following must leak above `FreshRssApi` and its repository. A
`ViewModel` handling a `continuation` would be an architectural defect, not a
shortcut.

- `ClientLogin`, the `Auth` token, the `GoogleLogin` header;
- the `T` modification token;
- the endpoint paths and the `/api/greader.php` prefix;
- the shape of the JSON responses, including `categories` as the carrier of the
  read state;
- the `continuation` token and its base (decimal) against article identifiers
  (hexadecimal);
- the API's three time units (seconds, microseconds, nanoseconds);
- the peculiar HTTP codes (`501` on `output`, `503` on a disabled API).

The domain knows only an `Article`, a `Feed`, an opaque `PageCursor` and a
business error type.

---

## 3. Dependency injection — Hilt

Hilt, inherited from the template and kept: the graph is checked at compile time,
which is the most useful property for a project run in autonomous steps — a
forgotten module does not compile, it does not crash at runtime.

Modules, all in `app/src/main/kotlin/…/di/`:

| Module | Provides |
|---|---|
| `DispatcherModule` | The three qualified `CoroutineDispatcher`s |
| `CoroutineScopeModule` | The `@ApplicationScope` scope |
| `DataStoreModule` | The settings' `DataStore<Preferences>` |
| `TimeModule` | The `Clock` implementation |
| `DatabaseModule` | The Room database and its DAOs |
| `NetworkModule` | The Ktor `HttpClient` |
| `SecurityModule` | The `SecretCipher` implementation |
| `SettingsModule` | The `SettingsRepository` implementation |
| `RepositoryModule` | The `:domain` interface → `data` implementation bindings |

### 3.1 Dispatchers injected, never referenced

`kotlinx.coroutines.Dispatchers` is mentioned in a single place in the project:
`DispatcherModule`. Everywhere else, a qualified `CoroutineDispatcher` is
injected (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`).

Without that, no test can control the scheduling nor advance time virtually —
and a test that really waits is a test one ends up disabling.

`DispatcherModuleTest` checks that no qualifier has been swapped: the compiler
cannot see it, all three having the same type.

### 3.2 A single source of time

`System.currentTimeMillis()` is only called in `TimeModule`. Everything else
receives a `Clock` (`:domain`). The tests use `FakeClock`, which only advances on
command.

That is what makes the visibility duration of automatic marking (SPECS.md §4.5)
testable without waiting a second per test.

---

## 4. Network access — Ktor

The choice made, and the reasons:

- **OkHttp engine** — it is Android's proven HTTP client: retries, connection
  pooling and TLS are already solved;
- **`kotlinx.serialization`** — serialisation generated at compile time, without
  reflection, therefore compatible with R8 with no keep rules to maintain;
- **`MockEngine`** (`ktor-client-mock`) — the API layer's tests describe literal
  HTTP responses, malformed ones included. It is the only way to test the reading
  of real JSON without a server.

Constraints specific to FreshRSS, to be respected in the implementation:

- **error responses are in plain text**, never JSON. Systematic deserialisation
  would hide the real HTTP code — `ContentNegotiation` must only apply to `2xx`
  responses;
- **`ClientLogin` answers in plain text**, as `key=value` pairs: this endpoint is
  not read like the others;
- **`output=json` is mandatory** on `subscription/list`, `tag/list` and
  `unread-count` — omitting it answers `501`;
- **article identifiers change base** depending on the field: hexadecimal in
  `items[].id`, decimal in `continuation` and in the `i` parameter. The
  conversion belongs to the API layer and to it alone.

### 4.1 Two probes before any connection

`FreshRssApi` exposes two checks that nothing compels one to make, and that must
nonetheless be made — each avoids a false diagnosis:

- **`probe()`** looks for the `OK` body of a bare `GET` on the endpoint. Without
  it, a typo in the address would send the API password to a server that is not
  the user's, and would produce a `401` they would blame on their credentials.
- **`checkAuthorizationForwarding()`** observes that the web server does forward
  the `Authorization` header. It only makes sense **after** the token has been
  obtained: `ClientLogin` requires no header, and paying for it earlier would
  cost a round trip on every attempt, including those doomed to fail on the
  credentials. Paying for it later would keep a session doomed to loop on `401`s.

Their peculiarities — status always `200`, bogus header required, query string
forbidden — are documented in docs/freshrss-api.md §1.

### 4.2 Pagination

> ⚠️ **The most dangerous trap of this API, and it is confirmed by experience.**
> An invalid cursor — empty, non-numeric — produces **no error**: the server
> silently brings it back to the start of the stream and returns the first page
> again, with the same `continuation`. An error in serialising the cursor
> therefore shows up as a silent infinite loop. That is why the `c` parameter is
> **never** emitted with an empty value, and why `PageCursor` is a dedicated type
> rather than a bare `String`.


FreshRSS's cursor is **relative**, not positional: the response carries a
`continuation` equal to the identifier of the last article returned, and the next
request passes it back as `c`. See
[docs/freshrss-api.md §3.5](./docs/freshrss-api.md).

Two consequences the code must reflect:

- the **absence** of `continuation` means "end of stream" — it is the only end
  signal, there is no total counter;
- an invalid cursor is silently treated as "start of stream" by the server. A
  serialisation error therefore shows up as a **repetition of the first page**,
  never as an error. A test must cover this case.

---

## 5. Persistence

### 5.1 Two stores, without overlap

| Store | Contents |
|---|---|
| **Room** | The collections: cached articles, pending markings |
| **DataStore** | The scalars: server address, username, token, thresholds, date of the last server contact |

The rule is strict: a piece of data lives in one **or** the other, never in both.
A duplicated setting always ends up diverging.

A **single** DataStore file, shared by `SessionStore`, `SettingsStore` and
`FeedFreshnessStore`, each on its prefixed keys. Encryption there is not global:
it is the **tokens** that go through `SecretCipher` (§5.2), not the server
address nor the thresholds. Encrypting what is not a secret would cost the same
price without protecting anything, and would make the storage unreadable at the
precise moment when reading it helps diagnose.

An accepted exception, and it is in `FeedFreshnessStore`: the **date** of the
last server contact is persisted, but the **acknowledgement** of the staleness
notice (SPECS.md §4.6) is not — it lives in an in-memory flow. Persisting it
would add a key for a situation that does not arise: on reopening, either a
request succeeds and the date is updated, or it fails and it is the offline
banner that speaks. That is also what forces this store to be `@Singleton` — the
acknowledgement must survive the switch between the two presentation modes, which
destroys one ViewModel and builds another.

### 5.2 The API password is never recorded

Since the FreshRSS token does not expire, keeping it is enough to reopen the
application without logging in again. Keeping the password as well would bring
nothing and would double the exposed surface (SPECS.md §3.4).

Encryption goes through **AES/GCM on `AndroidKeyStore`**, written by hand:
`androidx.security:security-crypto` would have done the same job, but the library
is deprecated and AGENTS.md §2 forbids it.

**Two seams, and they do not serve the same purpose.** `SecretCipher` makes it
possible to test what surrounds encryption — persistence, wiping on sign-out —
without a keystore, which Robolectric does not simulate. `SecretKeySource` makes
it possible to test the encryption **itself**: the format, the initialisation
vector, GCM authentication, and the behaviour when faced with an unreadable text.
Without that second seam, any `KeystoreSecretCipher` stayed out of reach for the
sole reason that it manufactured its own key.

What remains uncovered therefore comes down to `AndroidKeyStoreKeySource` — some
twenty lines that only call the platform. Retried on 2026-08-08: the
`AndroidKeyStore` provider still throws `NoSuchAlgorithmException` under
Robolectric.

### 5.3 A refused token and a sign-out are two different things

| Operation | Tokens | Address and username |
|---|---|---|
| `invalidateSession()` — the server refuses the token | wiped | **kept** |
| `signOut()` — a deliberate gesture by the user | wiped | wiped |

The input reminder (`SignInHint`) contains no secret: that is what allows it to
be kept. A user whose token is refused probably only has an API password to
renew; making them retype their server address would be gratuitous
(SPECS.md §3.4).

### 5.4 Room, and what the cache does not make go backwards

Room carries the collections, DataStore the scalars. The schemas are versioned in
`app/schemas/`: that is what allows Room to check migrations automatically, and a
review to see a database change in the diff rather than deduce it from the
entities' code.

**The local read state never goes backwards.** An article recorded as read stays
read, even if the server still describes it as unread. This is not a convenience:
a marking made offline is only transmitted when the network comes back (SPECS.md
§5.2), and until then the server knows nothing. Overwriting the local state with
its own would **make what the user has just read reappear in the feed** — the
most visible regression a cache can produce. In the other direction, an article
read elsewhere arrives read and becomes read here: "read" propagates, "unread"
does not.

**Purging relies on age in the cache**, never on the publication date. Purging on
publication would make an old article the user has just opened, and which is
still on screen, vanish within the second.

`ArticleCache` is the only boundary between the domain model and Room: the
entities do not cross it, otherwise a persistence annotation would end up
constraining the shape of `Article`.

---

## 6. Presentation

### 6.1 One immutable state per screen

Each screen has a `data class ...UiState` produced by its `ViewModel` and
consumed by a **stateless** Composable.

- The Composable **displays** the state, it does not **derive** it. No
  computation in a `@Composable`.
- Each screen has a private `@Preview` that works **without injection** — if a
  preview requires a Hilt graph, the screen is too coupled.
- A ViewModel that **observes a source** publishes in `WhileSubscribed(5 s)`
  (`UiStateSharing`): with no subscriber, the observation stops. The five seconds
  of grace cover a rotation without re-registering everything. That is the case
  of `SettingsViewModel`, which follows the settings and the state of the cache.
  A ViewModel that merely **accumulates the result of its own calls** —
  `DiscoverViewModel`, `LoginViewModel` — carries a `MutableStateFlow`: there is
  no observation to interrupt, and the sharing policy would have nothing to
  arbitrate. `SessionGate` is an exception in the other direction and starts
  `Eagerly`: the root switch is observed for the whole life of the application,
  and letting it fall back to `Unknown` would make the login screen flicker on
  every return from the background.

### 6.2 Navigation

`AppDestination` gathers route, labels and icon. The navigation bar is **derived
from the enumeration**: adding a destination consists of adding an entry, and
nothing else. `AppNavigationBarTest` observes this derivation, which therefore
holds for any destination added later.

### 6.3 Root switch

`SessionGate` decides between the login screen and the application, based on the
mere presence of a session. No screen therefore has to handle a redirection: a
refused token makes the session disappear, and the root switches by itself.

The `Unknown` state is not decorative: the session lives on disk, and reading it
the first time is not instantaneous. Starting from "signed out" would make the
login screen appear for a moment on every launch, including for a user who is
already signed in.

### 6.4 The cache is never dressed up as a page

A question the assembly raised, and whose answer structures everything else:
**how do you render a page coming from the cache without passing it off as the
end of the feed?**

`ArticlePage.nextCursor == null` means "end of feed", and nothing else. A cache
page has no cursor: rendering it as an `ArticlePage` would therefore display
"you have read everything" to a user who is merely without a network.

The cache is therefore a **parallel and permanent source** —
`observeCachedArticles()`, a flow that re-emits on every write — while
`loadPage()` goes on honestly reporting `FeedError.NoNetwork`. The caller thus
has the **content** and the **cause** separately, which lets it report the state
without alarming, and above all without lying.

The same flow serves the immediate display at launch (SPECS.md §5.1) and offline
reading (§5.2): these are two uses of a single mechanism.

### 6.5 Two domain decisions that the interface merely applies

Interleaving and read detection are **pure functions of `:domain`**. This is not
elegance: these are the two places where a regression would be invisible to the
eye, and only exhaustive tests hold them.

**`interleaveBySource`** spreads the sources out without lying about freshness.
The first two rules of SPECS.md §4.2 are structurally incompatible beyond a
certain amplitude; the trade-off chosen — recency wins, with a bound of seven
positions — is recorded in SPECS.md because it is visible to the user. The bound
is expressed in **ranks and not in duration**: a time threshold would behave very
differently on a feed publishing three articles a day and on one publishing three
hundred.

**`ReadDetector`** decides when an article becomes read, from a double threshold
of surface and continuous duration. It measures nothing itself and owns no
coroutine: it receives observations and answers. Two consequences the caller must
own, and which SPECS.md §4.5 now records:

- the fraction is that of the **visible part of the screen**, not of the
  article's own height — otherwise an article taller than the screen could never
  be marked read;
- the caller must **observe even when nothing moves**. The rule bears on a
  duration, and duration does not elapse on its own: without periodic
  observation, an article motionless for ten seconds would never be marked read.

### 6.6 The Discover feed

Constraints already established by SPECS.md, and which will weigh on the design:

- **lazy list**: the feed is potentially long, composing it all would be
  untenable;
- **each item's visibility must be measurable** — displayed proportion and
  continuous duration (SPECS.md §4.5). This is the trickiest technical point of
  the application, and it largely determines the structure of the list;
- **the reading position must survive closing the application** (SPECS.md §5.3),
  and it is an **article** that is remembered, never a rank: the feed lengthens
  between two openings. The list's items therefore carry a stable key, which
  serves both to find that article again and to avoid recomposing what has not
  changed. Pull-to-refresh, for its part, preserves nothing: it goes back to the
  top, and says so (SPECS.md §4.6);
- **the order must be deterministic**: interleaving is computed in `:domain`,
  from a reproducible seed, and not drawn at display time.

---

## 7. Errors

An error goes through three forms, and only one is visible to the user:

```
Technical exception (Ktor, SQLite)     data layer
        ↓ translated
Domain error (sealed type)             :domain
        ↓ translated
Displayable message                    :app/presentation
```

No technical exception comes up above the `data` layer. No string intended for
the user is produced below the presentation layer: the messages are resources,
which makes them translatable and verifiable.

SPECS.md §3.3 requires a **distinct message per cause** of login failure. The
domain's error type must therefore distinguish these cases — a single error type
would make the specification inapplicable.

---

## 8. Tests

| Scope | Tool | What is tested |
|---|---|---|
| `:domain` | JUnit, pure JVM | Interleaving, decisions, transformations |
| API layer | Ktor `MockEngine` | Reading literal HTTP responses |
| Repositories | In-memory Room, temporary DataStore | Persistence and replay |
| ViewModels | `kotlinx-coroutines-test` | State transitions |
| Screens | Compose UI Test + Robolectric | What is **displayed** |
| Rendering | Roborazzi | What it **looks like** |

The doubles are **versioned Fakes** (`domain/src/testFixtures/`), not generated
mocks: a Fake can be read, debugged, and documents the contract better than a
string of `when(...).thenReturn(...)`.

### 8.0 A safeguard that was empty

`ktlintCheck` was checking **no Kotlin source of `:app`**: the ktlint-gradle
plugin does not discover AGP 9's Android source sets, and only registered a task
there for `.kts` files. The verification command of AGENTS.md §5 was therefore
partly empty from the repository's very beginning.

Style rules now go through **`detekt-formatting`**, which embeds them in Detekt —
which, for its part, does see the module. On the day it was put in place, it
reported 22 violations, including four dead imports left by an earlier refactor.

The lesson goes beyond this case: a verification tool that never reports anything
deserves to have **what it looks at** checked, not merely that it passes.

### 8.1 Coverage of `:domain`

`koverVerify` requires 98 % on `:domain`. The threshold records something already
achieved rather than setting a target.

**Lifted.** The safeguard has really been measuring since the first
authentication models: it failed straight away at 86.2 %, then at 94.2 %, before
being satisfied. It was not decorative.

### 8.2 Visual rendering

Interface tests check *what is displayed*; screenshots check *what it looks
like*. A regression in contrast or in the dark theme breaks no textual assertion.

Every screen is captured in light **and** in dark, with dynamic colour disabled
and the screen format pinned — without which the reference would depend on the
user's wallpaper or on Robolectric's default configuration.

The screenshot harness renders the content in a `Surface`, and not a `Box`. A
`Box` does not provide `LocalContentColor`: text that does not set its colour
fell back to black, invisible in the dark theme. The screenshot therefore showed
a defect that the application, which renders its screens in a `Scaffold`, does
not have. Observed in Phase 0.

### 8.3 Testing the screen does not test what feeds it

Screen tests build the display state **by hand**: they prove that the screen
renders what it is given, never that it is given the right thing.

The case that established this: `Article.toUiModel` was failing to propagate
`isRead`. Four screen tests nonetheless covered the display of that state, and
none could catch the defect — they all passed `isRead` themselves, without ever
going through the projection. It was only seen on a device, an article read the
day before arriving from the cache as new.

The rule holds for **every** field of `ArticleUiModel`, and all the more for
those that no longer display anything: `isRead` has had no representation since
GOAL-020, but it still decides marking and purging (SPECS.md §5.4). What the
screen does not show, only a projection test guards — `ArticleUiModelTest`.

---

## 9. Map of the repository

**Packages and their role, not a list of files.** A tree copied out by hand is
wrong by the next commit: the one that used to be here lied about a dozen files,
and maintaining it cost more than it returned. What follows only changes with the
architecture, not with every addition — and can be checked with a `find`.

```
domain/                       Pure Kotlin/JVM — decides, knows neither HTTP nor disk
├── auth/                     session, credentials, server address, failure causes
├── core/                     Outcome<value, error>
├── feed/                     article, page, cursor, repository contracts
├── read/                     read detection, marking queue, scheduling
├── recap/                    on-device digest: availability, generator port, prompt
├── reminder/                 time, content and learned hour of the reading reminder
├── settings/                 reading settings, cache
├── shuffle/                  source interleaving
└── time/                     Clock

app/
├── data/
│   ├── api/                  FreshRSS: client, endpoints, DTOs, conversions
│   ├── local/                DataStore (scalars) and room/ (collections)
│   ├── network/              connectivity
│   ├── recap/                Gemini Nano through ML Kit, behind the domain port
│   ├── repository/           implementations of the domain contracts
│   └── security/             encryption of secrets at rest
├── di/                       one Hilt module per family of dependencies
├── reminder/                 reading reminder: contracts, worker, notification
└── presentation/
    ├── browser/              what leaves the application: opening the article, sharing the link
    ├── discover/             feed as a list
    ├── feed/                 the feed engine and everything the two modes share
    ├── lifecycle/            what reacts to going into the background
    ├── login/                sign-in
    ├── navigation/           destinations, graph, presentation mode
    ├── permission/           the permission to notify, asked for at the right moment
    ├── recap/                digest of the unread: title-bar action, sheet, states
    ├── settings/             settings
    ├── stats/                reading statistics: the histogram behind the reminder hour
    ├── immersive/            feed as full-screen pages, flicked vertically
    └── theme/                colours, spacings
```

**Two packages for one and the same feed, and it is intentional.** `discover/`
and `immersive/` present the same articles according to SPECS.md §4.8, but
their layout is not common: a lazy list and a pager have neither the same state
nor the same visibility measurement. Everything else **is** common, and lives in
`feed/` since GOAL-029: the engine (`FeedSessionViewModel` — pagination,
reload, bootstrap, marking, notices), the shared state (`FeedUiState`) and its
transitions, the terminal composables (offline banner, stale notice, failure
block, empty feed), the word-boundary truncation, the staleness watcher, the
illustration slot. Each mode keeps only its wiring: a `@HiltViewModel`
subclass that names its excerpt projection, screens that bind their strings
and test tags. The displayed article model and the feed's phases stay in
`discover/`, inherited.

`feed/` has a history that repeats itself: `FeedNotice`, then
`ArticleIllustration`, then the whole engine started out written **twice**,
identically, until a fix had to be applied in both places — twice, the
divergence arrived before the fix (see §9.10 and the GOAL-029 review). What
touches both modes is brought together before being fixed, not after.

The tests follow the same structure, plus `startup/` for what belongs to no
layer — building the graph, database migration, startup.

What this map deliberately **does not say**: the number of tests, the number of
screenshots, the state of progress. Those figures age within one commit, and
[TASKS.md](./TASKS.md) already carries them.

### 9.1 Where each piece of the domain is consumed

This section long recorded pieces **written and tested but not yet wired up**.
The distinction had a precise meaning: as long as the assembly is not done, that
code is dead in the sense of AGENTS.md §2, whatever the number of tests
surrounding it.

**That divergence is closed.** What remains useful, and what this table now
gives, is the **point of consumption** of each piece — that is what a review must
be able to find, and that is what would become wrong first if a regression
detached a domain decision from its caller.

| Piece of `:domain` | Consumed by |
|---|---|
| `interleaveBySource` (14 tests) | `DefaultArticleRepository` — server page and cache flow |
| `ReadDetector` (18 tests) | `DiscoverViewModel`, fed by `ArticleVisibility` from the list; `ImmersiveViewModel`, fed by `pagerVisibility` from the pager |
| `ReadTransmissionScheduler` | `DefaultReadSyncRepository` — batch grouping |
| `ReadSyncRepository` | `DiscoverViewModel` and `ImmersiveViewModel` (marking, replay at startup), `ReadFlushOnBackgroundObserver` (going into the background) and `DefaultAuthRepository` (sign-out) |
| `FeedPresentation` | `FeedPresentationViewModel`, which routes the Discover destination to one of the two modes |
| `FeedFreshness` (15 tests) | `FeedStalenessWatcher`, which both feed ViewModels build on their scope |
| `FeedFreshnessRepository` | `DefaultArticleRepository` in **writing** (every valid server response) and `FeedStalenessWatcher` in **reading** |
| `CacheRepository` | `SettingsViewModel` — cache state and manual purge |
| `SettingsRepository` | `SettingsViewModel`, both feed ViewModels for the thresholds, and `FeedPresentationViewModel` for the presentation mode |
| `RecapGenerator`, `RecapPrompt` | `RecapViewModel` — availability makes the title-bar button exist, the prompt and the streamed generation feed the sheet (SPECS.md §4.10). **The prompt's wording lives in one place**: `domain/recap/RecapPrompt.kt`, pure and JVM-tested — tuning the brief's tone or format is an edit there and nowhere else, with `parseRecapBrief` (`RecapSegment.kt`) as its contract for the `{words}[N]` markers |

On the `:app` side, the mechanisms the section used to flag as absent are in
place and covered: the cache feeds the first display (SPECS.md §5.1) and offline
use (§5.2), reloading is wired through to `ArticleRepository.refresh()` from both
modes (§4.6), opening an article marks it read (§4.7), and the age purge is
triggered once per process start by `CacheMaintenance` (§5.4).

### 9.3 Reloading crosses the shell's boundary

The reload button sits on the title bar (SPECS.md §4.6), which belongs to
`MainActivity` — above the navigation graph. The action, for its part, belongs to
the ViewModel of the displayed destination, which the shell has no reason to
know.

It is therefore the **action** that comes up, in the form of a `FeedRefresh` that
the destination publishes and the bar consumes. The reverse — pushing the bar
down into each screen — would force each one to redraw a title and a navigation
bar, and would make three bars exist where one is needed.

Publishing is done through `DisposableEffect`, and **removal** counts as much as
placement there: without it, leaving the feed for the settings would leave behind
a button wired to a ViewModel nobody is looking at any more.

The remaining work is no longer assembly to be caught up on: it is described task
by task in [TASKS.md](./TASKS.md), which is the only document up to date on this
point.

### 9.4 The reading reminder does not cross the network layer

The daily reminder (SPECS.md §4.9) reads `ArticleRepository.unreadFromCache`, and
that contract carries the prohibition in its very signature: it does not return a
`FeedResult`, because it has no network failure to report.

This is not a convenience but the line that separates a **local notification**
from a **background synchronisation** — SPECS.md §2 welcomes the first and still
excludes the second, and §7.4 requires that no connection go out without a
gesture from the user. An implementation that went off to fetch a page "to get
fresher titles" would tip the application from one side of that line to the other
with nothing to signal it.

The consequence is accepted and visible: an article published since the last
opening is not in the cache, and will therefore not be announced.

Three refusals precede any notification, and their **order** matters: no session
— the user is no longer signed in, there is nothing to remind them of; setting
switched off; then empty cache. The first two do not arm the next day's reminder,
the third does — tomorrow there may be something to read.

**The hour it aims at is learned where reading is recorded.** The reading-hour
histogram (`ReadingHistogram`, SPECS.md §4.9) is fed by
`DefaultReadSyncRepository.markAsRead` — the single point both presentation
modes and the open-article marking already pass through — and persisted by
`ReadingHistogramStore` under the `reminder.` DataStore prefix, which a logout
leaves in place: the habit belongs to the person, not the account. The
scheduler resolves its target through the pure `reminderTargetMinute`: fixed
hour if set, dominant hour if the histogram suffices, else the opening minute.
The statistics screen (`presentation/stats`) reads the same histogram and
publishes the same `dominantHour`, not a recomputation: it exists to show the
reminder's reasoning, and two computations would let the two diverge.

### 9.5 The version is not typed in

`versionName` and `versionCode` are derived from the same Git tag, in
`app/build.gradle.kts`. Two sources of truth for one and the same version are a
scheduled divergence: it is the one you discover on the day you publish a 1.1
still carrying the code of the 1.0.

The code is `major × 1 000 000 + minor × 1 000 + patch`, strictly increasing with
the version and bounded far below the maximum Google Play accepts. It has a floor
of 1, because Android refuses a zero code and the `0.0.0-…` fallback produced
one.

`providers.exec` rather than a direct call to `ProcessBuilder`: the repository
uses Gradle's configuration cache, which an undeclared call would invalidate on
every build.

### 9.8 An image is never enlarged

The illustration slot is fixed (16:9) and the image fills it: that is what stops
the list from jumping as each image arrives, and it is also what was stretching
thumbnails that were too narrow.

`ArticleIllustration` therefore compares the **source** width, which Coil exposes
in its success state, with the **measured** width of the slot. The decision is a
pure function — `needsUpscaling` — rather than a condition buried in a `Box`: it
can be tested without rendering, where a screenshot would be needed to check the
other.

Two scaling choices, and the second one cost an attempt on a device:

- the background uses `Crop` on a copy **overflowing** the slot slightly — `blur`
  fades out all the way to the edges, and without that overflow the frame would
  reappear around the perimeter;
- the foreground image uses `Inside`, and not `Fit`. `Fit` fills the smallest
  dimension, and therefore enlarges again: the first attempt delivered an image
  that was still blurry over a correct background. `Inside` never grows beyond
  the native size — the only scale that invents no pixel.

`Modifier.blur` requires API 31 while the project goes down to 26: below that,
nothing changes (SPECS.md §8, question 12).

### 9.7 The launch talks to nobody, and its order depends on nothing

The feed at launch must reopen **identically** (SPECS.md §5.1). Four mechanisms
were defeating that, each found after the previous one, all observed on a device
on 2026-08-08 — they are noted here because they form a whole, and because fixing
only one was not enough.

| Mechanism | What it produced |
|---|---|
| Automatic request at launch | Put the disk and the network in a race; the outcome decided the screen |
| Server order ≠ cache order | The server sorts by fetch date, the cache by publication. Pages are now brought back to publication order (`DefaultArticleRepository.interleaved`) |
| Cache bound applied **before** the read filter | A cache whose 200 most recent articles were read returned an empty list: the screen thought it was empty and started the fallback load. 283 articles, 69 unread, zero displayed |
| Read articles removed from the feed | The set to interleave changed on every session, and so did the order |

The principle that unites them: **interleaving must bear on a set that does not
move.** `interleaveBySource` chooses each position by looking at its neighbours;
anything entering or leaving the set redistributes the rest. The cache therefore
returns its articles **read ones included**, and only a requested reload renews
the list.

That last clause was, for a long time, a promise the cache did not keep: nothing
deleted anything on a reload, so emptying the feed then killing the application
brought the previous set straight back (GOAL-026). A successful reload now makes
the cache **equal to what the server returned** — `ArticleCache.retainOnly` —
sparing only rows whose mark has not left yet.

**The criterion is the returned page, not the local read state**, and GOAL-026
got that wrong before GOAL-027 corrected it. Measured on the author's device,
database in hand: after a reload displaying "nothing to read", 31 rows were
still there, **unread locally**, which the server no longer returned. They had
been read from the web interface, and `upsertPreservingLocalReadState` only
propagates "read" for articles the server *returns* — absence said nothing. Yet
absence is the only sign this application ever receives that something was read
elsewhere.

It runs **after** the save, since `upsertPreservingLocalReadState` reads the
previous read state from the very rows concerned. Pagination renews nothing: a
following page never contains what precedes it, so the rule would erase the feed
under the reader.

The reading reminder is the only reading of the cache that still filters out read
articles (`unreadFromCache`): it is not answering the same question.

### 9.9 An empty screen is a state nothing was watching

Symmetrically to §9.7, where the launch had to stop talking: a feed with nothing
in it had to start again. Two mechanisms held it silent, and only fixing both
made the screen escapable (GOAL-025).

| Mechanism | What it produced |
|---|---|
| The "empty cache" exception read **once** | `hasDecidedBootstrap` settles it on the first sample of the cache. Everything read, one reload, and the emptied screen never asked again |
| The pull armed on the list alone | A screen with no list had no gesture. Only the title-row button was left, and one had to know it |

The request is now triggered by `onScreenShown()`, called by the routes from a
`LifecycleResumeEffect`. **A discrete fact, never a state**: a server with
nothing to give leaves the screen empty, and a rule reacting to emptiness would
loop on it. The lifecycle stays in the presentation layer — a ViewModel that
observed it would hold onto something that outlives it badly — and the ViewModel
keeps the decision: the screen says what happens, never what to do about it.

The rule was long written **twice**, once per ViewModel — defended at the time
as four lines of guard not worth sharing. GOAL-029 closed the question the
other way: the guard was never the unit to share, the whole engine was, and it
now lives once in `FeedSessionViewModel`. The two cases of
`EmptyFeedAsksServerWhenShownTest` remain, one per mode, exercising the same
engine through both wirings.

The pull needed one more thing to exist on a screen with no article: something
that dispatches scroll. `PullableMessage` is a `LazyColumn` of a single item
sized with `fillParentMaxSize` — a plain `Box` emits no nested scroll, so the
gesture would have been inert, which is worse than absent.

### 9.10 A reload disowns the pages still in flight

`refresh()` never looks at the loading lock — by design, a reload must not
queue behind a slow page — so a page requested before the pull can land after
it. Until GOAL-028 it was then applied as if nothing had happened: appended
under the refreshed list, and its cursor **overwrote the reload's**, silently
resuming the abandoned course. GOAL-027 widened the blast radius: its cache
write re-inserted rows `retainOnly` had just removed. Found by analysis at the
author's request, not observed on a device — the trigger is merely a slow
network and the title-row button pressed during a `LoadingMore`.

The fix is **cancellation**: the engine keeps the in-flight load's `Job`, and
the reload cancels it. Cancellation propagates through the repository and
Ktor — the request is abandoned, not merely its result, and the disowned
page's cache write with it. State, cursor and phase stay untouched, failure
included, since reporting the failure of a disowned request would paint as
broken a feed that was just replaced. A lock or an await on `isLoading` was
rejected then and remains rejected: cancellation is immediate, the gesture
never waits.

**This supersedes GOAL-028's generation counter** (GOAL-029). The counter had
two defects the review exposed: it lived once per ViewModel — the very
divergence pattern §9.6 warns about — and it only discarded the page **on
arrival**, so the request ran to completion and its `cache.save()` still
executed if it landed after the reload's `retainOnly`. The counter covered the
display; cancellation covers the display *and* the cache, in one place, since
the engine itself is now shared (§9.6). A repository test pins the wider
guarantee: a cancelled page in flight writes nothing to the cache.

Swipe mode (today Immersive) had a second door to the same race: its `loadMore` did not check
`isRefreshing` — List mode's did — so the pager could *start* a page during
the reload. That divergence died with the shared engine: the guard is written
once.

The staging of this race in tests required splitting the fake's lock in two —
`pendingLoad` gates `loadPage`, `pendingRefresh` gates `refresh` — because a
single deferred suspending both calls makes the arrival order, which is the
whole point, unobservable.

### 9.6 The feed's staleness is measured where the server answers

The date used to say that a feed is stale (SPECS.md §4.6) is written by
`DefaultArticleRepository`, in its success branch, next to the cache write — and
not by the ViewModels that ask for the pages.

Two reasons, and the second decides. The layer that talked to the server is the
only one to **know** that it answered: a successful `loadPage` counts as much as
an explicit reload, and a valid but empty page counts too. Above all, at the
time two ViewModels asked for pages: the rule written in them would have lived
twice, and the two presentation modes would have diverged on the first fix
applied on one side only — the very pattern that GOAL-029 ended by sharing the
whole engine (§9.10). The timestamp stays where it is: the repository remains
the only layer that knows the server answered.

Symmetrically, the **decision** — six hours, bound included, a clock going
backwards makes nothing stale — is a pure function of `:domain`, to which the
current instant is passed. What remains with the presentation is what it alone
knows: that it is offline, that it is already refreshing, that it has nothing to
show.

### 9.2 What is inherited from the template, deliberately

The repository comes from `c4software/tailscale-auto-rules`, whose business logic
has been removed.

`MainDispatcherRule` found its use with the first ViewModel. `UiStateCollector`
was for a long time the only accepted exception to the "no dead code"
prohibition (AGENTS.md §2): it serves ViewModels publishing in
`WhileSubscribed`, which none did at the time. **The exception is lifted** — its
`keepCollecting` is used by `SettingsViewModelTest`, whose state would stay frozen
on its initial value with no subscriber.

The repository therefore no longer has a waiver against this prohibition, and
will only reopen one by recording it here.
