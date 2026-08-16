# FreshRSS Discover

An Android client for a personal [FreshRSS](https://freshrss.org/) server that
presents articles the way **Google Discover** does: a single vertical feed,
interleaved, with no visible end.

No list of feeds to work through, no unread counter to bring down. You scroll;
whatever has been seen long enough becomes read.

Two ways to go through it, as you prefer: the vertical **list**, or **swipe** —
one article full screen, set aside with a horizontal gesture like a card off a
deck.

<p align="center">
  <img src="docs/demo.jpg"
       alt="The Discover feed on an Android phone: two articles as cards, each with its illustration, the name of its source feed, its relative age and an excerpt. At the bottom, navigation between Discover and Settings."
       width="320">
</p>

<p align="center"><em>The Discover feed, fed by a real FreshRSS instance.</em></p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=fr.vbrosseau.freshrssdiscover">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
         alt="Get it on Google Play" height="80">
  </a>
</p>

> **Status: published on the Play Store, and proven on a device.** Connection to
> the server, feed and pagination, source interleaving, local cache and purge,
> read detection and the marking queue, reloading, opening articles, the
> settings screen and both presentation modes are in place.
>
> What remains open is written down as such: Swipe mode has **no alternative to
> its gesture**, a trade-off accepted and settled in
> [SPECS.md §7.1](./SPECS.md) — List mode, the default one, gives access to the
> same feed without it. **Reading position is not preserved**, and that is
> deliberate: the feed reopens exactly as it was left, so there is no place left
> to find again (§5.3). [TASKS.md](./TASKS.md) gives progress task by task,
> [ARCHITECTURE.md §9](./ARCHITECTURE.md) the actual state of the repository.

---

## What the application does

- connects to a FreshRSS server through its Google Reader compatible API;
- a single feed, all subscriptions interleaved, pagination with no visible end;
- **two presentation modes** to choose from, list or swipe, over the same feed
  and in the same order;
- automatic marking as read based on an article's actual visibility, with
  adjustable thresholds;
- read status synchronisation with the server, including after an outage, and
  forced transmission when moving to the background;
- feed reloading: pull in List mode, a button in both modes;
- **an invitation to reload** when the displayed feed is more than six hours
  old, dismissed with a gesture and absent offline, where it would have nothing
  to offer;
- **a quiet launch**: the feed reopens exactly as it was left, without querying
  the server — it is reloading, when asked for, that renews it;
- **a flag on articles already read**, which stay in the feed until the next
  reload;
- illustrations **never enlarged**: a thumbnail that is too narrow is shown at
  its own size, over a blurred background drawn from itself, rather than
  stretched and fuzzy;
- opening the original article in the browser;
- a local cache readable offline, with automatic and manual purge;
- **a daily reading reminder**, at the hour you usually read — learned from
  your reading habits, or fixed by hand — quoting real titles, and which
  **calls nothing**: it reads the cache, never the server. A statistics screen
  in the settings shows the hour histogram behind it;
- a Material 3 interface, light and dark themes.

The full specification is in [SPECS.md](./SPECS.md).

## What it will not do

Subscription management, multiple accounts, widgets, social sharing,
**background synchronisation**. See [SPECS.md §2](./SPECS.md).

The reading reminder is a **local** notification: it opens no connection, and
that is what sets it apart from background synchronisation, which stays
excluded.

---

## Requirements

- A **FreshRSS** server with its **API enabled**
  (*Administration → Authentication → Allow API access*).
- An **API password**, distinct from the login password
  (*Profile → API password*). It is the main cause of failed connections.
- **Android 8.0** (API 26) or above.

---

## Building

The project builds with the JDK bundled with Android Studio.

```bash
export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
```

Full verification, to be passed before any commit:

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

Visual rendering (outside CI, several minutes):

```bash
./gradlew :app:verifyRoborazziDebug   # compare against the references
./gradlew :app:recordRoborazziDebug   # re-record an intended change
```

Install on a connected device:

```bash
./gradlew :app:installDebug
```

### Trying it by hand

Tests and screenshots have never been enough: what lives below the transport
layer — the manifest, the network policy, the platform — only shows itself when
the application actually runs. [`envTest/`](./envTest/README.md) exists for
that, and holds an emulator plus a **real** FreshRSS instance.

```bash
./envTest/test-stack.sh init       # once: AVD, container, user, feeds, install
./envTest/test-stack.sh run        # afterwards: restart, refresh, reinstall
./envTest/test-stack.sh emulator   # the emulator alone, with its window
./envTest/test-stack.sh stop       # at the end of every Goal
```

`emulator` starts nothing else — no container, no build, no install — and is
the one to use to try the application by hand, on the local stack or on the
demonstration server of [`store/demo-server/`](./store/demo-server/README.md),
which needs no FreshRSS at all. What is already installed on the AVD stays
there, open session included.

The window is the default, not a constraint. `WITH_WINDOW=0` starts the same
emulator headless — for automated checks, an SSH session, or a machine with no
display server:

```bash
WITH_WINDOW=0 ./envTest/test-stack.sh emulator
```

`init` and `run` are unchanged: still `-no-window`, still software rendering.

Once it is up:

```bash
adb shell am start -n fr.vbrosseau.freshrssdiscover/.MainActivity
adb shell pm clear fr.vbrosseau.freshrssdiscover   # back to the sign-in screen
adb exec-out screencap -p > screen.png
adb logcat -s FreshRssApi ReadSync                 # API calls, read sync
```

**Shut it down at the end of every Goal.** Stopping destroys nothing — the AVD,
the container, the user, the feeds and the accumulated read state all survive,
and `run` finds them again.

### Version

It is **not written in the repository**: `versionName` and `versionCode` are
both derived from the Git tag, so that they cannot diverge.

| What is built | `versionName` | `versionCode` |
|---|---|---|
| the `v1.2.13` tag | `1.2.13` | `1002013` |
| three commits after it | `1.2.13-3-gabc1234` | `1002013` |
| with no tag and no `git` | `0.0.0-inconnue` | `1` |

The name therefore says by itself whether a build is publishable, which a
screenshot in a bug report is enough to read. The `RELEASE_VERSION` variable
takes precedence over `git describe` — that is how CI passes the tag along, its
`checkout` not fetching the history.

**Publishing a version is therefore a matter of placing a tag**: nothing else to
change.

```bash
git tag -a v1.1.0 -m "…" && git push origin v1.1.0
```

### Production build

`assembleRelease` produces a **signed** artifact if four environment variables
describe a keystore — `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` — and an **unsigned** artifact
otherwise, without failing: anyone building the project without them must
succeed. None of this is written in the repository.

The `release.yml` workflow does the same in CI. It is **never** triggered by a
`push`: only by hand, or by a `v*` tag.

### Publishing to the Play Store

> Groundwork **proven**: this is what the application was published with.

Everything to upload — the texts of both listings, screenshots, icon, feature
graphic, answers to the console's questionnaires and the privacy policy — is
gathered in **[`store/`](./store)**, together with the order in which to enter
it.

The privacy policy publishes itself: `pages.yml` regenerates it on GitHub Pages
from `store/privacy-policy-*.md` whenever those files change on `main`, so the
text declared to Google cannot drift from the one held here.

1. Enable GitHub Pages once, in "build by workflow" mode — the command is in
   [`store/README.md`](./store/README.md).
2. Keep the demonstration server alive: the app is a client, and without a
   server the reviewer sees nothing but the sign-in screen. A Cloudflare Worker
   plays that part — [`store/demo-server/`](./store/demo-server/README.md).
3. Create a release keystore, **outside the repository**, and put it in the
   repository secrets (see *Production build* above).
4. Place a `v*` tag: the workflow builds and signs.

---

## Structure

```
:domain   Pure Kotlin/JVM — the decisions. The Android SDK is not in it.
:app      Android — display, storage, network calls.
```

The details are in [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## Documentation

| File | Contents |
|---|---|
| [SPECS.md](./SPECS.md) | What the application must do |
| [LICENSE](./LICENSE) | MIT licence |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | How it is designed |
| [TASKS.md](./TASKS.md) | What is done, in progress, and to be done |
| [AGENTS.md](./AGENTS.md) | The development rules |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | How to contribute |
| [docs/freshrss-api.md](./docs/freshrss-api.md) | A survey of the FreshRSS API |
| [store/README.md](./store/README.md) | The Play Store submission file |
| [store/demo-server/README.md](./store/demo-server/README.md) | The demonstration server given to the reviewer |
| [envTest/README.md](./envTest/README.md) | The local test stack: emulator and real FreshRSS |
| [PROMPT.md](./PROMPT.md) | The initial intent, frozen |

---

## Assisted development

The repository is driven by a **Harness**: work is organised into *Goals* broken
down into tasks, recorded in [TASKS.md](./TASKS.md). Four Claude Code commands
operate it:

| Command | Role |
|---|---|
| `/status` | Where the project stands, and what is wrong |
| `/goal <objective>` | Break an objective down into tasks, then carry them out |
| `/task [GOAL-00X-TYY]` | Carry out a specific task, or the next one |
| `/verify` | Build, test, and confront TASKS.md with reality |

An agent arriving on the repository runs `/status`, then `/goal` or `/task`. It
does not need to be given the context again: the context is in the files.

---

## Privacy

The application communicates only with **the user's FreshRSS server**. No
telemetry, no third-party service, no advertising. The only other outgoing
connections are the loading of article images and the opening of a link in the
browser, both at the user's initiative.

---

## Licence

[MIT](./LICENSE) — © 2026 Valentin Brosseau.

---

## Origin

The technical skeleton — architecture, Gradle configuration, quality tooling,
conventions — comes from
[`c4software/tailscale-auto-rules`](https://github.com/c4software/tailscale-auto-rules),
whose business logic has been removed.
