# SPECS.md — Functional specification

The **functional** source of truth: what the application must do.

The *how* is in [ARCHITECTURE.md](./ARCHITECTURE.md), the *order* in
[TASKS.md](./TASKS.md), the *working rules* in [AGENTS.md](./AGENTS.md).

---

## 1. Intent

**FreshRSS Discover** is an Android client for a personal
[FreshRSS](https://freshrss.org/) server.

It does not set out to reproduce a classic RSS reader — a list of feeds, an
inbox, unread counters to bring down to zero. It offers the opposite usage, that
of **Google Discover**: a single vertical feed, interleaved, with no visible
end, which you go through without having to decide which feed to start with.

```
FreshRSS
   ↓
Articles from the various feeds
   ↓
Source interleaving
   ↓
Continuous vertical feed
   ↓
The user scrolls
   ↓
Articles visible long enough become read
   ↓
New articles are loaded
```

What that implies, and what structures everything that follows:

- **No organisation imposed on the user.** No navigation by feed or by folder in
  the main journey.
- **No gesture to mark as read.** Reading is scrolling.
- **No end.** The feed goes on as long as articles remain.
- **No guilt-inducing counter.** The number of unread articles is not put
  forward.

## 2. Out of scope

Explicitly excluded, so that no Goal introduces them by drift:

- subscription management (adding, removing, sorting) — that is done in
  FreshRSS;
- offline reading of the full content of articles;
- multiple accounts;
- widgets, quick tiles;
- comments, annotations;
- **background synchronisation** — the application only fetches articles while
  it is open, and no connection leaves without a user gesture (§7.4).

These points are not refused forever; they are not part of the first version,
and introducing them would require updating this document.

> **Notifications have left this list**, at the author's request: see §4.9. They
> do not eat into the neighbouring exclusion — the reminder reads the **local
> cache** and connects to nothing. That is what distinguishes a local
> notification from a background synchronisation, and what makes only one of the
> two present here.
>
> **Sharing too**, at the author's request: see §4.3. It appeared under "social
> sharing, comments, annotations", and is none of the three — but the exclusion
> was written broadly enough to cover it, and lifting it explicitly is better
> than interpreting it. What is added is the **system picker**: the application
> has no destination of its own, it hands over. No third-party service is
> engaged, no connection leaves from us — §7.4 remains true word for word.
> Comments and annotations, for their part, remain excluded: they would call for
> an account, storage and moderation.

---

## 3. Connecting to the server

### 3.1 What the user enters

| Field | Contents |
|---|---|
| Server address | URL of the FreshRSS instance |
| Login | FreshRSS user name |
| API password | **Distinct** from the login password |

The address is entered in its natural form (`https://rss.example.org`). The
application derives the endpoint (`…/api/greader.php`) itself: asking the user to
know that path would be making them carry an implementation detail.

An address with no scheme is completed to `https://`. The `http://` scheme
remains accepted — self-hosted instances on a local network are a real case —
but the application then states that the connection is not encrypted.

From Android 17 on, reaching such an instance requires the local network
permission, asked for at launch alongside the notification one. A refusal leaves
the whole application working; only an instance on the local network becomes
unreachable, and it is then reported like any server that does not answer (§7).

### 3.2 What the application must explain

The API password is the main cause of failed connections, and its existence is
not obvious. The login screen must therefore state where to find it in FreshRSS
(*Profile → API password*), and not merely report a failure.

### 3.3 Diagnosing failures

Each cause has its own message. A generic "connection failed" is a defect.

| Cause | What the user must read |
|---|---|
| Address unreachable | The server is not responding |
| Address reachable, but not a FreshRSS instance | This address does not appear to be a FreshRSS server |
| API disabled on the server | The API is disabled: enable it in the FreshRSS administration |
| Login or API password refused | Check the login and the **API password** |
| No network | No network connection |
| `Authorization` header not passed on by the web server | The credentials are right, but the server does not pass the authorisation on: fix the reverse-proxy configuration |

The last case deserves its own message, rare though it is. Without it, the
connection would succeed and then **everything** afterwards would fail with
"credentials refused": the user would change their password in vain, when the
fix lies in their server's configuration.

The application cannot distinguish "unknown login" from "incorrect password":
FreshRSS answers the same thing to both, and that is deliberate —
distinguishing them would allow accounts to be enumerated. The message therefore
covers both hypotheses.

### 3.4 Session persistence

The session is kept between two launches: the user logs in only once.

**The API password is never stored.** The token issued by FreshRSS does not
expire: keeping it is enough to reopen the application without logging in again.
Keeping the password as well would bring nothing and would double the exposed
surface.

The token is a secret: it is stored **encrypted**, backed by the device's
*keystore*, never logged, never included in an error report. The server address
and the login, which are not secrets, remain readable — masking them would
complicate diagnosis without protecting anything.

If the secret becomes unreadable — the *keystore* key is lost when the user
changes their screen lock or restores a backup onto another device — the
application behaves as if there were no session, and takes the user back to the
login screen. It does not crash.

If the server refuses the token — a real case when the user changes their API
password — the application returns to the login screen explaining why, without
losing the address or the login already entered.

### 3.5 Logging out

A log-out action erases the token, the credentials and the local cache. It is
confirmed, because it is destructive.

---

## 4. The Discover feed

### 4.1 Contents

The feed presents the **unread** articles of every subscription, all categories
together. That is what the server returns on each reload.

**What has been read does not thereby vanish from sight.** An article marked as
read stays in its place — during the session (§4.5), and also from one opening
to the next, since launching redisplays the cache as it stands (§5.1). It only
leaves the list on a **requested reload** (§4.6), which renews it.

This is not a tolerance but the condition of the feed's stability. Interleaving
(§4.2) chooses each position by looking at the neighbours: removing read
articles between two launches would change the set to be interleaved — marking
consumes some of it on every read — and would return a different order. The feed
then appeared to reinterleave itself, without any request having left. Observed
on device on 2026-08-08, three consecutive launches, three different tops.

### 4.2 Source interleaving

This is the heart of the application, and its only genuinely subtle rule.

Sorting by date alone is not enough: a very prolific feed would occupy whole
screens in a row, and the less active feeds would become invisible. Interleaving
must therefore **spread the sources out**, without presenting an old article as
recent.

Rules, in order of priority:

1. **No source monotony.** Two consecutive articles from the same feed are
   avoided as long as another source is available.
2. **Recency respected.** The overall order stays broadly reverse
   chronological: interleaving reorders locally, it does not lift a month-old
   article above an article from today.
3. **Determinism.** Two successive displays of the same set of articles produce
   the same order. A feed that reorders itself when you come back to the screen
   gives the feeling of having lost something.
4. **Continuity between pages.** Rule 1 also applies at the junction between one
   page and the next.

**Trade-off between rules 1 and 2.** They are structurally incompatible beyond a
certain amplitude: spreading the sources out perfectly would sometimes require
lifting an old article very high. **Recency wins.** Concretely, an article is
never presented more than seven positions before its chronological rank; past
that bound, source monotony is accepted rather than lying about freshness.

That bound is expressed in **positions**, not in duration. A time threshold
would behave very differently on a feed publishing three articles a day and on
one publishing three hundred — the bound in positions is the same everywhere,
and it is the one the user perceives while scrolling.

It is the only trade-off in this section visible to the user: a more aggressive
interleaving is obtained by loosening that bound.

### 4.3 Presenting an article

Each article exposes:

- its **title**;
- the **name of its source feed**, without which interleaving would be
  disconcerting;
- its **publication date**, in relative form ("2 h ago");
- its **illustration image**, where one exists;
- an **excerpt** of its content, shortened by the application: the server sends
  the full summary, which reaches several tens of thousands of characters on
  some feeds (§8, question 7).

An article without an image stays readable: the absence of an illustration must
not produce an empty space, nor a generic placeholder image.

**Every article can be shared**, in both presentation modes (§4.8). A control on
the card opens the **system picker**: it is the picker that offers the
destinations, and the application knows none of them. What leaves is the
**title then the original link** — a bare URL does not say what you are sending,
and the excerpt, shortened by us (§8, question 7), would pass our truncation off
as content.

An article with no usable link **cannot be shared**, and the control is not
there: the same rule as for opening (§4.7), because sending a title on its own
would be a message with no object.

**An illustration is never enlarged.** Many feeds publish thumbnails narrower
than the slot; stretching them makes them fuzzy, and a fuzzy image does a
disservice to the article it illustrates. It is therefore displayed at its own
size, centred over a cropped and **blurred** copy of itself which fills the rest
of the slot. The background comes from the image, so it always matches it; the
slot stays full, with no empty band and no frame.

The process applies **only** in that case: an image wide enough is cropped as
before, with no background and no treatment. And it requires Android 12, failing
which the stretching remains — a plain degradation, preferred over a sharp,
duplicated background that would be worse than the defect (§8, question 12).

### 4.4 Infinite scrolling

A new page is requested **before** the user reaches the bottom, so that
scrolling is not interrupted.

- The loading in progress is visible at the bottom of the feed.
- A loading failure displays a message and a "Retry" action, **without emptying
  what is already displayed**.
- When the device is online but no OK answer comes back — the server does not
  answer, answers an error, or answers something unreadable — a toast says so
  as well. It complements the failure block, which may sit below the fold; it
  does not replace it, and being offline is not this case: the offline banner
  owns that regime (§5.2).
- When there are no articles left, the feed ends with an explicit message. A
  feed that simply stops getting longer is indistinguishable from a breakdown.

### 4.5 Automatic marking as read

An article is considered read once it has been **visible enough**: at least
**60 % of its height** displayed for at least **200 continuous milliseconds**.

The duration was **1 second until it was measured on a device**, and the
measurement is the reason it is no longer. Sampling raw visibility at 5 Hz
during a continuous scroll in List mode: **63 articles crossed the screen, 1
was marked as read**. Of the 62 lost, **54 had reached the surface threshold**
— most filled the viewport entirely — and failed on duration alone, each being
fully visible for a single 200 ms sample. The setting could not compensate,
since 1 second was also the lowest value it offered.

Those articles stayed unread on the server, so the next reload legitimately
returned them: "I saw it, I reloaded, it is back at the top" was the reported
symptom, and it was the threshold, never the marking request, which reached the
server correctly whenever detection fired.

200 ms is **one sampling period**: the shortest duration that still requires
two consecutive observations, hence a genuine presence on screen rather than a
single sample caught in flight. It is the floor of the adjustable range too
(§6), 150 ms, that follows this reasoning — below one period the duration would
be satisfied by the first observation and the double threshold would collapse
into a single one.

This mechanism is **optional**. A switch in the settings (§6) turns it off and
back on, and it is **on by default** — that is the principle of §1, "reading is
scrolling", and an existing installation must see nothing change. Three
clarifications, because they decide the rest:

- **Turning it off only stops visibility-based detection.** Opening an article
  still marks it as read (§4.7): that is a deliberate gesture, not an effect of
  scrolling, and conflating them would leave Immersive mode unable to consume
  anything at all.
- **Both thresholds stay displayed, greyed out.** Hiding them would make two
  settings disappear without saying why; leaving them active would offer to
  adjust what no longer applies. They are kept as they are for switching back
  on.
- **What is already marked stays marked.** The queue of pending markings is not
  emptied: turning marking off does not undo past reads.

Switching back on applies **without a restart**, like the presentation mode
(§4.8): the displayed feed starts marking again from the instant of the switch.

This double threshold is deliberate: surface alone would mark as read the
articles crossed by a fast scroll; duration alone would mark an article barely
brushed at the edge of the screen.

Those two values are **named parameters**, not constants scattered about: they
will be adjusted with use. Both thresholds are **inclusive** — "at least" reads
literally. This is not a detail: 0.6 is not exactly representable in binary, and
an exclusive threshold would make the rule depend on the rounding of the
computation done by the interface.

Two clarifications that the implementation made necessary:

- **"60 % of its height" is measured on the visible portion of the screen, not
  on the article's own height.** Taken literally, an article taller than the
  screen could never reach 60 % of itself, and would therefore **never** become
  read. It is the caller that bounds the fraction accordingly.
- **Visibility must be observed even when nothing moves.** The rule bears on a
  duration, and duration does not elapse by itself: without periodic observation
  while the list is still, an article left ten seconds on screen would never be
  marked as read. That is this feature's most likely integration trap.

Associated behaviour:

- An article marked as read **is not distinguished on screen**. A flag signalled
  it for a while, at the author's request, then was removed at their request as
  well: use showed that it drew the eye to the least interesting thing in the
  feed. The read state is nonetheless still **held** — it is what decides the
  marking sent to the server and the purge (§5.4); only its representation has
  gone.
- An article marked as read **stays displayed** and in its place. Making it
  disappear under the finger would shift the content being read.
- Marking is **sent to the server in batches**, not one call per article:
  markings are grouped for a few seconds before being transmitted (§8,
  question 4). Nothing is lost during that delay — the queue survives closing —
  but the read is then known only to the device.
- Marking is **optimistic**: the local state changes immediately, the
  synchronisation follows. A network failure must not be visible while reading.
- An untransmitted marking is **kept** and replayed at the next opportunity,
  including after the application is restarted.

### 4.6 Refreshing

Reloading starts from scratch: it **empties what is displayed**, reloads the
beginning of the feed, and **automatically returns to the first article**.

Three commands trigger it, and they do exactly the same thing:

| Command | Available in | Why |
|---|---|---|
| **Pull to refresh** | List mode, **with or without articles** | The conventional gesture on a vertical feed |
| **Button, on the title row** | both modes | In full screen there is no list to pull; and a pull is not practicable for everyone (§7.1) |
| **Re-tapping the Discover tab**, already selected | both modes | The bottom-bar convention: tapping the tab you are on means "bring me back to the start". In List mode the list first scrolls back to the top, **then** the reload fires — the return is shown, not skipped; in Immersive mode the reload returns to the first page on its own. **At the top of the list the tap does nothing**: there is nowhere to bring the reader back to, and a reload would empty a feed the tap never asked to lose — reloading from the top stays with the pull and the button, which are deliberate |

The gesture long stopped where the list stopped: a screen with no article had
no list, therefore no pull. The reasoning was that those screens already had
their way out — "Retry" — and that a scroll gesture is not discovered where
nothing scrolls. The first half was wrong. A reader who has read everything has
no error to retry, only an empty screen, and the pull is the first thing they
try on it. So it is armed there too; the button and "Retry" stay where they
were.

The button is therefore not a duplicate of the gesture: it is the only command
**on the content surface** of Immersive mode — superimposing a vertical pull on
a pager that already snaps vertically would give two gestures on one axis — and
it is the
alternative to the gesture in List mode, where nothing replaced it. The tab
re-tap duplicates neither: it lives on the navigation bar, where a tap on the
current tab would otherwise do nothing at all.

- What is displayed is replaced, not added to. What was there disappears —
  **and it disappears from the cache too**, so that killing the application does
  not bring it back (§5.1). **What the server returned is what remains**: the
  criterion is belonging to the page just served, not the local read state,
  which knows nothing of what was read elsewhere. The one exception is anything
  whose marking has not reached the server yet — that is a truth the server
  cannot know, so it cannot return it.
- The owned consequence: after a reload you can no longer scroll back to what
  came before it, and the offline reserve falls back to the head page until
  scrolling refills it.
- The reading position is **not** preserved: reloading brings you back to the
  beginning, and that is what it announces.
- Pagination starts again from the beginning: the previous cursor is abandoned.
- While waiting, the command **shows that it is working** rather than greying
  out or disappearing: greyed out it would say "unavailable" and not "in
  progress"; gone, the press would seem lost.

**This choice was made against the opposite option**, and it is worth
explaining. Inserting the new articles at the top without moving the user
preserves their reading, but lets the feed grow indefinitely and makes the
gesture almost invisible — you pull, and nothing seems to happen. A full reload
gives the gesture an immediate, legible effect, at the cost of the scroll
position; that is the convention of applications where the feed is the main
content, and it is the one that was retained.

An owned consequence: a user who reloads by reflex loses the place where they
were reading. The gesture must therefore stay deliberate — it is only triggered
by a decisive pull, never by a mere upward scroll.

**This only holds for a requested reload.** Closing the application is not a
request: the next launch reopens the same feed, unchanged (§5.3).

#### When the displayed feed is stale

Nothing synchronises in the background (§2) and the cache is displayed as soon
as the application launches (§5.1): the screen of a ten-hour-old feed would
otherwise be indistinguishable from that of a fresh one.

Beyond **six hours** without a response from the server (§8, question 9), a
strip says so and offers to reload. It carries two commands — **reload**, which
is nothing other than the reload described above, and **later**, for anyone not
in a position to do it now.

- It is dismissed **by hand**, never by a timer: a message that clears itself
  gets missed, and this one explains something you did not see coming.
- It appears in **both modes** (§4.8), and silencing it there holds for both:
  it is the same feed.
- It **does not appear offline**: the banner of §5.2 already says why the feed is
  old, and offering to reload would open a door leading nowhere.
- Nor does it appear during a reload, or on a screen with no article — there is
  then no old feed, but an empty screen, which has its own message.
- **Having silenced it only holds for the state of the moment.** A successful
  reload, then six more hours, and the invitation comes back.

### 4.7 Opening an article

Touching an article opens the **original link** in the browser, through a custom
tab (*Custom Tab*): the user keeps the application's context and finds their
browser session and settings again.

Opening an article marks it as read, whatever its past visibility.

An article with no usable link is not clickable, and shows it.

**It is the whole card that opens, in both modes** — the whole page, in
Immersive. An explicit button did it for a while there, out of fear that a
press caught during a hesitant flick would send the user off into the browser.
The fear was unfounded: the platform distinguishes a press from a drag, the
gesture is not consumed by the click, and a test observes it. The button cost
a command row for a guarantee the system was giving already.

### 4.8 Two presentation modes

The feed can be gone through in two ways, at the user's choice. **The content is
the same**: same articles, same interleaving, same reading and loading rules.
Only the presentation changes.

| Mode | Gesture | What it shows |
|---|---|---|
| **List** (default) | vertical scrolling | several articles on screen, as cards |
| **Immersive** | vertical flick, one page at a time | **one** article filling the screen |

Immersive mode takes up the gesture of short-video applications: the article
fills the screen, a flick upward brings the next one, which snaps into place,
and a flick downward brings the previous one back. This is **not** navigation
between feeds or between categories — §1 and §2 exclude those, and that stays
true here. It is the same interleaved feed, presented article by article.

It **replaced** a horizontal card-stack mode ("Swipe", GOAL-012) on
2026-08-25, at the author's request, without carrying its stored setting
over: an installation left in Swipe reopens in the List.

What this mode implies, and which is not neutral:

- **A full-screen article is entirely visible.** The rule of §4.5 applies as it
  stands: it becomes read after the required continuous duration. The surface
  threshold, for its part, is satisfied straight away — so it is duration alone
  that decides, and here it takes on its full meaning.
- **Going back does not "unread".** Returning to an already-read article does not
  set it back to unread: marking is not reversible by a navigation gesture.
- **Anticipated loading remains** (§4.4): the next page is requested before
  reaching the last loaded article, and the end of the feed states itself
  explicitly, on a page of its own, rather than blocking the flick.
- **The illustration is the page.** It fills the screen behind the text, under
  a scrim that fades into the background colour of the theme over the lower
  part, where the source, the title and the excerpt sit. An article without
  illustration stands on a **tint that belongs to its source** — a hue
  derived from the feed's name, so a source keeps its colour from one
  session to the next — with the source's initial as a watermark, rather
  than on an empty page (§4.3). A picture narrower than the page is treated
  as on the card: blurred copy behind, sharp original at its own size
  (§8, question 12). The picture lags slightly behind the text during the flick — a
  parallax that says the scene is behind the words, not glued to them.
- **The page does not scroll inside itself.** On a vertical pager, that
  gesture belongs to the pager. The excerpt is cut at a fixed number of lines
  with an ellipsis; what the page cannot show is in the article, one tap away
  (§4.7). Increasing the system font shows fewer lines, never a hidden end.
- **The page runs under the title bar.** The bar keeps its title and its
  actions but loses its background over this mode; a light scrim at the top
  of each page keeps them legible. A bar with a background would cut a band
  off every picture. The bottom navigation bar keeps its room.
- **A single command remains, on a rail at the right edge**: sharing. Opening
  has passed to the whole page (§4.7), and the mode thereby recovers what
  makes it interesting — an article, and almost nothing around it.
- **The mode is a persistent setting** (§6): the application reopens in the mode
  the user left.
- **The gesture is usable with a screen reader.** A vertical flick is the very
  gesture screen readers move on with; the reserve that §7.1 recorded for the
  horizontal Swipe has gone with it.

The choice of mode **never** changes the order of the articles: a user who
switches from one to the other finds the feed at the same place, in the same
order (determinism rule of §4.2).

### 4.9 Reading reminder

A daily notification recalls that articles are left to read.

**It goes out at the hour the user actually reads.** Not at a time chosen by
the developer: a notification at 9 a.m. for someone who reads in the evening is
an interruption, not a reminder. The application keeps a **24-bin histogram of
reading sessions** — one bin per hour of day, fed each time articles are marked
read, at most one session per day and per hour so a forty-article catch-up
evening cannot outweigh two weeks of habit — and the reminder aims at the
**start of the densest bin**. The dominant bin, not an average: for someone who
reads in the morning **and** in the evening, an average lands mid-afternoon, an
hour they never read at. Bins decay day by day, so a habit that moved wins
within days and weeks of not reading return the reminder to the fallback below.

**Before the histogram has seen enough** — three weighted sessions — the
reminder falls back on the previous day's **first opening time**: the moment
the user reached for the application, the best signal available on day one and
the rule this feature originally shipped with.

**A learned hour never lands in the night.** A dominant bin or an opening
fallback aiming between 22:00 and 07:00 is moved to the **nearest edge** of
the day — 21:59 or 07:00, whichever is closer (author's decision, 2026-08-18:
evening reading spilling past midnight had made 00:00 the dominant hour on a
real device, and a notification nobody is awake for is not a reminder). The
hour the user fixes themselves is exempt: it is applied as is, as promised
below.

**The user can fix the hour instead.** A setting (§6) replaces the learned hour
with one they choose; the learned hour can be wrong for reasons no histogram
sees, and a **user**-chosen hour is not the developer-chosen hour this section
refuses. The histogram keeps learning meanwhile, and is shown on a statistics
screen reached from the settings — the reminder's reasoning made visible.

**It does not go out if there is nothing to read.** A reminder announcing that
the pile is empty is an interruption with nothing in return, and that is what
makes people turn an application's notifications off.

**It quotes real titles**, taken from the feed, and announces the number of
remaining articles. A reminder that does not say what is waiting is
indistinguishable from an advert for the application itself.

**Its wording changes from one day to the next.** An identical daily message
stops being read after three days: the eye learns its shape and sweeps past it
without seeing it. The variation is **deterministic** — two runs on the same
day, after a failure or a restart, give the same message.

**What it does not do:** no network request. It reads the local cache (§5.4),
and nothing else. An article published since the last opening is therefore not
in it and will not be announced; that is the owned price of §2, which still
excludes background synchronisation.

**There are never two of them.** A new reminder **replaces** the previous one
instead of stacking up beside it: a stack of daily reminders says nothing more
than a single one, and gets swept away with one gesture without being read.

**Opening the application clears it.** The reminder has done its job by the time
the user arrives; leaving it in the shade would make it a leftover.

**It can be turned off** from the settings (§6). Under Android 13, there is no
notification permission to withdraw, and a reminder you cannot turn off is a
defect.

### 4.10 On-device recap of the feed

A button on the title row, next to the refresh button, produces a **narrative
brief of the feed** in a bottom sheet: one flowing paragraph telling what
happened across five articles — and what connects them, when several cover
related stories. The passages drawn from one article are **underlined and
tappable** to the original. Prose and not one summary per article (author's
decision, 2026-08-16): per-article summaries only paraphrased the excerpts
the list already shows, while connecting stories is what a list cannot do. A
sheet and not a screen: the brief is transient reading, regenerated at every
request, and a screen would promise a way back to a text that no longer
exists.

**Generation is entirely on the device.** The model is Gemini Nano, served by
AICore through ML Kit's Prompt API: the feed's text is never sent anywhere,
which is what keeps §7.4 true word for word. The consequence is accepted
rather than hidden: quality is that of a small local model, and the feature
costs a one-time model download.

**The button only exists where the model does.** The application asks the
platform at each arrival on the feed; on a device AICore cannot serve, the
button never appears — invisible, not greyed out, because no gesture of the
user can make an unsupported chip supported. When the model is merely **not
downloaded yet**, the button shows and the first tap offers the download,
with its progress, then generates without another gesture.

**The digest speaks the device's language.** Whatever it is: the output
language is passed to the model at each request, with no allow-list — an
allow-list would be the developer deciding which languages deserve the
feature.

**It summarizes the list as displayed** — read articles included, in the
screen's exact order, then the remaining unread beyond it (author's decision,
2026-08-16: two read articles sitting above the first summary read as a
broken order, and the recap's job is to mirror the screen). The order starts
at the **first article visible on screen**, not at the top of the list
(author's decision, 2026-08-18): what was scrolled past is behind the reader,
and a recap opening on it would retell a part of the feed already left —
those articles, if still unread, come back with the remaining unread. Matter is title
and excerpt from the cache (five per batch, the excerpts bounded), because
the full content is deliberately not stored (§4.7) and no request leaves
without a user gesture (§2). An empty pile says so instead of inventing; a
generation failure says so instead of showing half a digest as a whole one.

**A summarized article is a read article.** Each batch marks its articles
read the moment its summaries are shown, through the same optimistic marking
as the list (§4.5) — reading the summary is the recap's way of reading the
article. Already-read ones are not re-marked.

**The text streams in as it is generated.** On-device inference takes
seconds; a paragraph that builds up on screen — its tail shimmering while it
is written — is the difference between working and frozen. The model marks
each statement with its article's number; a marker the model drops degrades
that passage to plain, untappable prose, never to a blank sheet. The
instructions given to the model live in `domain/recap/RecapPrompt.kt` and
nowhere else: the brief's tone is a domain decision, versioned and tested. Dismissing
the sheet cancels the work — nobody reads behind a closed sheet, and the
chip is better released.

---

## 5. Network behaviour

### 5.1 Local cache

Retrieved articles are kept locally. On launch, the feed displays the contents
of the cache **immediately** — read articles included (§4.1), so exactly what
was on screen the previous time — and stops there: **no request leaves as long
as there is something to show** (§8, question 10). The launch feed is the one
you left, stable and identical from one opening to the next; updating it is a
gesture — the reload of §4.6, which the staleness notice (§4.6, "when the
displayed feed is stale") comes to recall at the right moment. Scrolling, for
its part, remains a gesture like any other: reaching the bottom of the known
loads what follows (§4.4).

An **empty** cache is the sole exception: first opening, return after logging
out — there is nothing to show, and the first load leaves on its own. An empty
screen during a network request would give the impression of an application with
no content; an empty screen with no request would be worse, a dead application.

What the cache holds is what the last reload left there (§4.6): a feed emptied
on purpose stays empty across a kill and a relaunch, instead of coming back to
life with articles one has already read — which, nothing marking them out on
screen any more, would be indistinguishable from new ones.

That exception is not read only at launch. **Every time the feed comes to the
foreground with nothing on it, a request leaves**: arriving on the feed, coming
back from Settings, waking from sleep. One gets to an empty screen by reloading
once everything has been read — the reload replaces what is displayed (§4.6),
and the server has no unread article left to give. That screen then asked
nothing more, and one got out of it only by finding the button on the title row.

It is attached to a discrete fact — the screen coming to the foreground — and
never to the state of being empty. A server with nothing to give leaves the
screen empty, and a rule that reacted to emptiness would ask again, and again.
Each foregrounding is worth **one** attempt. A first load already in flight, a
failure carrying its own "Retry", and a reload under way ask nothing more: the
first would double the request of every launch, and the second would hammer a
missing network at each return.

What leaves is the **reload** of §4.6, not the next page: the cursor of a
finished feed leads nowhere, and the reload is the only path that tells the
server what has just been read before questioning it — which is precisely the
situation, since one arrives at an empty screen by having just read everything.

### 5.2 Offline

With no network:

- the feed remains consultable from the cache;
- the state is signalled without being alarming;
- markings as read are recorded locally and transmitted when the network comes
  back;
- opening an article fails with an explicit message.

### 5.3 Launching reopens at the top of a stable feed

**The reading position is not kept.** Reopening the application brings you back
to the top of the feed — the same feed, in the same order, as the one you left
(§5.1).

This section long specified the opposite: a "nearest" resumption, remembered on
closing. It was **withdrawn by an author's decision** on 2026-08-08, and it must
be said why, because the reason is not "it was too hard". The position memory
rewrote itself on every launch — the article at the top of the screen during the
first frames overwrote the real place — and every opening restored what the
happenstance of the previous one had left. Observed on device: a launch without
a single gesture shifted the remembered position. The fix existed, but the
trade-off lies elsewhere: resumption protected against a feed that moved under
your feet, and that feed no longer moves — launching no longer queries the
network (§5.1). On a stable feed that reopens identically, finding your place
again is done by scrolling through what you recognise; the remembering machinery
no longer paid for its complexity.

What remains guaranteed, and matters more: the top of the feed at launch is
**exactly** that of the closing, new articles excluded since there are none
without a gesture — unless the feed you left was empty, in which case there is
no place to find again and a request leaves (§5.1).

### 5.4 Purge

The cache is bounded. Articles **read and synchronised** are deleted beyond an
age threshold (§8, question 3); unread articles are never purged.

This concerns the purge by age, and it alone. A **reload** renews the cache on
another criterion entirely — what the server just returned (§4.6) — and it does
carry away articles that are unread locally, since the server no longer offering
them is the only sign the application ever gets that they were read elsewhere.

"**And synchronised**" reads literally: an article whose marking is still
waiting to be transmitted is **never** deleted, even past the threshold. This is
not an abstract precaution. The local memory of "already read" lives in the
cache and nowhere else: erasing the row before the server knows about the
marking would have the article described as unread again on the next refresh,
and it **would reappear in the feed as never read**. The case occurs as soon as
a device stays offline longer than the threshold.

The manual purge, for its part, does **not** ask for confirmation: it carries
away only what is at once read, transmitted and re-downloadable. Logging out
asks for one because it erases the token, the unread articles and the pending
markings — none of that comes back without a network and a password. Confirming
both would level the difference, and would teach the user to dismiss the dialogue
that matters.

---

## 6. Settings

The settings screen stays minimal:

- connected server address and login (read only);
- **feed presentation mode**: List or Immersive (§4.8);
- **reading reminder**: on or off, and its hour — automatic (learned, §4.9) or
  fixed at a chosen time;
- **reading statistics**: the hour histogram behind the reminder (§4.9), on a
  screen of its own reached from here;
- **automatic marking**: on or off, then its two thresholds (§4.5) — the
  thresholds stay displayed, greyed out, when it is off;
- cache size and manual purge action;
- logging out;
- application version and licence.

---

## 7. Cross-cutting requirements

### 7.1 Accessibility

- Every image carries a description, or is explicitly decorative.
- Touch targets are at least 48 dp.
- The application stays usable with an increased system font size.
- Contrast meets level **AA** in light **and** dark theme.
- **No function depends on a single gesture.** A screen reader reserves the
  horizontal swipe for its own exploration, and not everyone has the precision or
  the mobility a pull demands. Reloading satisfies this rule as of §4.6.
- **Both modes move on with a vertical gesture** (§4.8). The former Swipe mode
  required a horizontal one, which screen readers reserve for their own
  exploration; the reserve recorded here for it went with the mode on
  2026-08-25. Immersive mode's flick is the gesture screen readers themselves
  move on with, and the setting that leads out of it is reachable with
  ordinary targets.

### 7.2 Interface

- Material 3, dynamic colour where the platform provides it.
- Light theme and dark theme, both verified by screenshot (AGENTS.md §4).
- Edge to edge, with no content hidden by the system bars.

### 7.3 Language

The interface is **bilingual**: **English by default** (`values/`), with
**French** kept (`values-fr/`). English is the default because `values/` is what
any device whose language is not provided for receives. Article contents are
displayed as published.

A non-obvious consequence: the Roborazzi screenshots are pinned to `fr-rFR`
(`@Config(qualifiers)`), so they verify the **French**. A separate screen test
in `en-rUS` is what keeps `values/` complete.

### 7.4 Privacy

The application communicates **only with the user's FreshRSS server**. No
telemetry, no third-party service, no advertising. The only other outgoing
connections are the loading of article images and the opening of a link in the
browser, both at the user's initiative.

The recap (§4.10) does not eat into this: generation runs **on the device**
through a system service, and the feed's text leaves for no server. The one
download it causes — the model, once, at the user's explicit request — comes
from the platform, not from us, and carries nothing of the user's.

---

## 8. What is left to settle

Deliberately deferred decisions. Each one must be settled by the Goal that meets
it, then **written down here** — not left implicit in the code.

### Settled

| # | Question | Answer, and what decided it |
|---|---|---|
| 1 | API page size (`n`) | **40 articles.** Measured on a real feed: median summary of 1,324 characters, 90th percentile at 4,379. A page of 40 therefore weighs about 55 kB, which stays reasonable on a mobile network while leaving enough lead for scrolling not to be interrupted (§4.4). The server accepts far higher values — `n=100000` returned 4,645 articles without flinching — but asking for everything at once would only serve to delay the first display. |
| 2 | Exact formulation of the interleaving algorithm | **Recency wins over spreading the sources out**, with a hard bound of seven positions, expressed in ranks and not in duration (§4.2). The two rules are structurally incompatible beyond a certain amplitude, and it had to be said which one wins. |
| 3 | Age threshold for purging the cache | **7 days.** Beyond that, a read article no longer has a reader; below it, it has two. **Scrolling back** first: the feed is continuous and without landmarks, going back up it is the only way to find again what you skimmed over the day before — at 24 h the past would vanish between two launches. A week covers the real rhythm: you come back on Monday, you find your Friday feed again. The **memory of "already read"** next, carried by the cache itself. 30 days would quadruple the cache for content already consumed. |
| 4 | Batch size and grouping delay for markings | **100 articles, 5-second window at fixed expiry.** The floor of the delay used to be the continuous second of visibility of §4.5, on the reasoning that at most one article could become read per second. That reasoning fell with the threshold: at 200 ms a scroll can produce a read article at every sample, and grouping now matters **more**, not less — a window shorter than the second it already was would multiply the requests the batching exists to avoid. The ceiling is the gesture of leaving the application: during the window, the read is known only to the device. At 5 s that remains the exception; at 30 s it would be the common case. A **fixed and not sliding** window: with a continuous scroll producing a batch every 200 ms, a restartable window would never close as long as the user is reading. |
| 6 | Origin of the illustration image | **`enclosure` first, then the first `<img>` tag in the content.** The order is that of reliability: an `enclosure` is a declared illustration, an `<img>` may be a tracking pixel or a logo. But sticking to `enclosure` would cover **33 %** of articles, against **73 %** with the fallback — measured on 60 real articles. Depriving two thirds of the feed of an illustration would impoverish exactly what makes a Discover feed. |
| 7 | Length of the displayed excerpt | **240 characters, cut on a word boundary.** Three lines of `bodyMedium` over 411 dp hold about 180 characters, 210 at the smallest system font size; 240 leaves the margin for the visible cut to be the ellipsis and not text stopping dead. A word cut in half reads as a defect, hence the cut at the preceding space. Without that, each card would have up to 34,777 characters measured on every recomposition. |
| 12 | What to do with an illustration smaller than its slot? | **It is not enlarged**: displayed at its own size, over a blurred background drawn from itself (§4.3). The threshold is not a figure but **measured** — only what would have to be enlarged is treated, which stays right at any screen density. Under Android 12, where the system blur does not exist, the stretching remains: a second mechanism would have cost its writing and its tests for a minority of devices, and a sharp, duplicated background would have been worse than the defect. |
| 11 | Does the feed order come from the server? | **No: it is recomputed on the publication date.** The server sorts its `reading-list` by **retrieval** date — observed on a real instance, an article published two days earlier opened the first page. That order differs from the cache's, sorted by publication: the launch screen then depended on which of the disk or the network answered first. Each page is therefore brought back to publication order before interleaving, which expects it anyway (§4.2, rule 2). |
| 10 | Does launching reload the feed? | **No.** Author's decision (2026-08-08): launching shows the cache, stable, and no request leaves without a gesture — apart from an empty cache, where there is nothing to show. The automatic request on launch created a race between the disk and the network, whose outcome decided the screen; and a feed that moves when you open it reads as a feed that reinterleaves itself. Reloading is a gesture (§4.6), recalled by the staleness notice beyond six hours. |
| 13 | Parameters of the learned reminder hour (§4.9) | **24 hour-bins, daily decay 0.9, sufficiency at 3 weighted sessions, target at the start of the dominant hour, at most one session per day and per hour.** One-hour bins because the reminder's precision is the hour, not the minute. A 0.9 decay halves a habit in about a week: a new routine wins within days, one unusual evening barely dents two weeks of habit. Three sessions because below that a single recording would decide the hour on its own — the one-sample fragility the histogram replaces. The start of the hour so the reminder arrives before the habit, not after it. Ties break on the earliest hour, deterministically, for the same reason the wording rotation refuses a random draw. |
| 14 | Which on-device API for the recap (§4.10)? | **ML Kit's GenAI Prompt API**, not the dedicated Summarization API. The latter would have cost less integration, but it only outputs English, Japanese and Korean — and §4.10 requires the device's language, whatever it is. The Prompt API takes free-form instructions, so the output language is a parameter; AICore picks the best Gemini Nano the device owns behind the same code, which is why no per-device branch exists. The API is beta with no deprecation policy: a breakage lands on one adapter class, accepted. |
| 8 | Excerpt length in Immersive mode (§4.8) | **A fixed number of lines on screen, ellipsized; 1,400 characters as the measuring bound.** The page does not scroll: on a vertical pager that gesture is the pager's, so what the excerpt shows is decided by the screen, not by a character count. The bound stays at the figure GOAL-012 calibrated on the median summary (1,324 characters, question 7): the ordinary article reaches the layout whole, and Compose never measures the 34,777-character maximum for a page dismissed with one flick. |
| 9 | Threshold beyond which the displayed feed is "old" (§4.6) | **6 hours.** Nothing synchronises in the background (§2), so the screen shows the cache until the user asks for something else: with no landmark, yesterday's feed is indistinguishable from a fresh one. A short threshold — one or two hours — would turn the invitation into a daily reflex, and an invitation you learn to ignore no longer says anything. Six hours clearly separates the session resumed within the hour, where the feed is still the one you left, from the reopening the next morning. |

### Still open

| # | Question | When to settle it |
|---|---|---|
| 5 | Behaviour if a feed contains only read articles | At the feed Goal |
