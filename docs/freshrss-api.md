# FreshRSS API — working reference

A reference for FreshRSS's **Google Reader compatible API**, as used by this
project.

## Provenance and level of confidence

Four sources, of unequal value — the first two are read, the last two are
queried:

| Source | What it establishes |
|---|---|
| [Official "Mobile access" documentation](https://freshrss.github.io/FreshRSS/fr/users/06_Mobile_access.html) | The base URL, `ClientLogin`, the authorization header, the list of main endpoints, the API password |
| [`p/api/greader.php`](https://github.com/FreshRSS/FreshRSS/blob/edge/p/api/greader.php) and [`app/Models/Entry.php`](https://github.com/FreshRSS/FreshRSS/blob/edge/app/Models/Entry.php), `edge` branch | The **exact parameter names**, their default values, the shape of the responses, the pagination semantics |
| **`https://demo.freshrss.org/`**, queried on 2026-08-07 | The **codes and bodies actually returned** on unauthenticated paths |
| A **personal instance**, queried on 2026-08-07 | Everything that requires an account: the successful `ClientLogin` response, the modification token, the real shape of articles, the effective behaviour of pagination |

The personal instance is not named here, and must not be: it served only as a
test bench. The observations it allowed are reproducible on any FreshRSS
installation that has an API password.

The official documentation details neither the pagination parameters nor the
shape of the JSON returned. Everything below, unless stated otherwise, was
therefore **read in the source code**, not guessed.

Anything marked **"observed"** has been checked against a real server — the demo
server for the open paths, a personal instance for the authenticated ones. That
also **corrected a misreading**: see §2.1, where the boundary between `400` and
`401` is not the one the source suggested. The points that remain uncertain are
gathered at the end of the document.

> ⚠️ This page is a survey, not a contract. FreshRSS may change it. A Goal that
> touches the API re-reads the source before implementing (AGENTS.md §3).

---

## 1. Base URL

```
https://<freshrss-server>/api/greader.php
```

The `/api/greader.php` prefix is stripped from the path by the router, which
also tolerates its absence. The paths below are given in full.

### 1.1 Recognising a FreshRSS instance — *observed*

```
GET /api/greader.php
```

answers **`OK`** — exactly two bytes, with no line break — with a `200` status.
It is the cheapest way to validate an address entered by the user, and the only
reliable discriminator for
[`AuthError.NotAFreshRssServer`](../domain/src/main/kotlin/fr/vbrosseau/freshrssdiscover/domain/auth/AuthError.kt).

Two traps, both observed:

- **the `Content-Type` is `text/html`**, not `text/plain`. Relying on the MIME
  type to decide would not work;
- **the slightest query string breaks the detection.** `GET …/greader.php?x=1`
  answers `400 Bad Request`: the shortcut that returns `OK` requires both an
  empty path **and** an empty query string. The probe must therefore be a bare
  `GET`.

A reachable host that is not a FreshRSS instance answers whatever it likes —
`404` on `example.com`, observed. No code characterises that case: only the `OK`
body characterises the favourable one.

> ⚠️ **This probe says nothing about the state of the API.** It answers `OK`
> with a `200` status **even when API access is disabled** on the server —
> observed. The shortcut that returns `OK` is placed before the `api_enabled`
> check in the router, and therefore takes no account of it.
>
> Direct consequence on the order of the calls: recognising the instance is not
> enough, it is `ClientLogin` that reveals the `503`. An implementation that
> concluded "valid server" after that probe alone would then display a false
> diagnosis.

### 1.2 Checking the web server's configuration — *observed*

```
GET /api/greader.php/check/compatibility
```

Attests that the web server **does forward the `Authorization` header**. Some
reverse proxies strip it; without this probe, any authentication would then fail
with a `401`, with a message wrongly blaming the user's credentials.

Two peculiarities, without which the probe is useless:

- **the status is `200` in both cases.** The verdict is in the **body**: `PASS`,
  or `FAIL <reason>`. Testing the HTTP code checks nothing;
- **the request must itself carry an `Authorization` header**, even a bogus one.
  The probe observes the presence of the header it receives — called without a
  header, it always answers `FAIL get HTTP Authorization header!
  Wrong Web server configuration.`, including on a perfectly configured server.
  Observed on the demo server: `FAIL` without a header, `PASS` with
  `Authorization: GoogleLogin auth=x/y`.

---

## 2. Authentication

### 2.1 ClientLogin

```
POST /api/greader.php/accounts/ClientLogin
Content-Type: application/x-www-form-urlencoded

Email=<user>&Passwd=<API password>
```

The server also accepts these parameters in the query string, but **logs a
deprecation warning**: the password would appear in the logs. Always use `POST`.

The expected password is the **API password**, distinct from the login password.
It is set in FreshRSS under *Profile → API password*. An account with no API
password configured cannot log in: the corresponding field is empty on the
server side and the comparison always fails.

**Successful response** — *observed against a personal instance, on 2026-08-07*:
status `200`, `text/plain`, **exactly three lines, in this order**:

```
SID=<user>/<digest>
LSID=null
Auth=<user>/<digest>
```

`SID` and `Auth` do carry the **same** value — the source suggested as much,
experience confirms it. `LSID` is literally the string `null`, not an absent
value: a parser treating all three lines the same way would get back the string
`"null"`, not an empty one.

Only `Auth` is of interest to us. The order being stable, it is nonetheless
safer to look for the line by its `Auth=` prefix than by its rank: nothing in
the source contractually guarantees that order.

The token is a deterministic digest of (server salt + user + digest of the API
password). Practical consequences:

- **it does not expire** — it stays valid as long as the API password and the
  server salt do not change;
- it is therefore **storable** and reusable between two launches;
- it **becomes invalid without notice** if the user changes their API password.
  A `401` response on an authenticated request must therefore bring the user
  back to the login screen, not display a network error.

**Failures** — *observed, and different from what the source suggested*:

| Situation | Response | Body |
|---|---|---|
| **Syntactically invalid** username — empty, spaces, `!`, `../` | `400` | `Bad Request!` |
| **Well-formed** name but unknown to the server | `401` | `Unauthorized!` |
| Wrong API password | `401` | `Unauthorized!` |
| Account with no API password configured | `401` | `Unauthorized!` |
| API disabled on the server | `503` | `Service Unavailable!` |

> ⚠️ **Correction of a misreading.** The source calls `checkUsername()` first of
> all, which gave the impression that an unknown user produced `400`. That is
> wrong: this function only validates the **syntax** of the name. A well-formed
> but non-existent name carries on, finds an empty configuration, and fails with
> `401` just like a wrong password.
>
> **Direct consequence on SPECS.md §3.3**: "unknown user" and "password refused"
> are **indistinguishable**, and that is the desirable behaviour —
> distinguishing the two would allow accounts to be enumerated. The message
> displayed must therefore cover both hypotheses at once, which it already does
> ("Check the username and the **API password**").
>
> A `400` is therefore **never** the user's fault regarding their password: it
> is an input anomaly on the username alone, or a programming one.

The error bodies are `text/plain; charset=UTF-8`, with
`X-Content-Type-Options: nosniff` — observed.

The API must be enabled globally in FreshRSS (*Administration → Authentication →
Allow API access*).

**What the server then answers — observed**, with the API actually disabled:

| Path | Response |
|---|---|
| `/api/greader.php` (recognition probe) | **`OK`, status `200`** — unchanged |
| `/accounts/ClientLogin` | `503`, `Service Unavailable!` |
| `/check/compatibility` | `503`, `Service Unavailable!` |
| `/reader/api/0/…` | `503`, `Service Unavailable!` |

The first line is counter-intuitive and it matters: the recognition probe is
blind to the state of the API. Saying "every endpoint answers `503`" would be
wrong, and would lead to a detection that does not work.

### 2.2 Header of authenticated requests

```
Authorization: GoogleLogin auth=<user>/<token>
```

Every request outside `/accounts/*` requires it. Without it, or with an invalid
token: `401`.

### 2.3 Modification token

```
GET /api/greader.php/reader/api/0/token
```

Returns, in plain text, a **57-character** string (a digest padded with `Z`),
followed by a line break. The length was *observed against a personal instance,
on 2026-08-07*: it is exactly 57, which reading the source led one to expect.

That length must nonetheless not be hard-coded as a validity criterion: it is
guaranteed by no contract, and a refused token announces itself with a `401`
anyway, not by its size.

This token is passed in the **`T`** field of the `POST` body of any modifying
operation (`edit-tag`, `mark-all-as-read`, `rename-tag`, `disable-tag`).

An observation from reading the code, to be known but **not to be exploited**:
the check also accepts an empty `T` or `T=x`, for compatibility with existing
clients. This project always sends the real token — depending on an undocumented
leniency would be fragile.

The token being deterministic too, it can be obtained once and then reused. A
`401` on a modifying operation means the token is no longer valid: ask for it
again once, and if the failure persists, treat it as a lost session.

### 2.4 Identity of the logged-in account — *observed*

```
GET /api/greader.php/reader/api/0/user-info?output=json
```

*Observed against a personal instance, on 2026-08-07*:

```json
{
  "userId": "…",
  "userName": "…",
  "userProfileId": "…",
  "userEmail": ""
}
```

One point to remember: **`userEmail` can be empty**. FreshRSS does not require
an email address on its accounts, and the field is then present but reduced to
the empty string — it is not omitted. A client using it to display the user must
therefore fall back on `userName`, never on `userEmail`.

This endpoint is the lightest way to check that a stored token is still valid at
startup: it returns no article and simply answers `401` if the token has been
invalidated.

---

## 3. Reading

### 3.1 List of subscriptions

```
GET /api/greader.php/reader/api/0/subscription/list?output=json
```

`output=json` is **mandatory**: any other value answers `501 Not Implemented`.
This constraint also applies to `tag/list` and `unread-count`.

```json
{
  "subscriptions": [
    {
      "id": "feed/12",
      "title": "Feed title",
      "categories": [{ "id": "user/-/label/Tech", "label": "Tech" }],
      "url": "https://exemple.org/rss",
      "htmlUrl": "https://exemple.org/",
      "iconUrl": "https://serveur/f.php?…",
      "frss:priority": "…"
    }
  ]
}
```

Feeds with the "hidden" priority are absent from the list.

### 3.2 List of tags and folders

```
GET /api/greader.php/reader/api/0/tag/list?output=json
```

Returns the system states (`user/-/state/com.google/starred`,
`…/reading-list`, `user/-/state/org.freshrss/main`, `…/important`), then one
entry per category (`type: "folder"`) and per tag.

### 3.3 Unread counts

```
GET /api/greader.php/reader/api/0/unread-count?output=json
```

```json
{
  "max": 128,
  "unreadcounts": [
    { "id": "feed/12", "count": 7, "newestItemTimestampUsec": "1700000000000000" },
    { "id": "user/-/label/Tech", "count": 31, "newestItemTimestampUsec": "…" },
    { "id": "user/-/state/com.google/reading-list", "count": 128, "newestItemTimestampUsec": "…" }
  ]
}
```

`newestItemTimestampUsec` is in **microseconds**, sent as a string.

### 3.4 Contents of a stream — the central endpoint

```
GET /api/greader.php/reader/api/0/stream/contents/reading-list
```

The path can designate other streams:

| Path | Contents |
|---|---|
| `…/stream/contents/reading-list` | All articles, hidden feeds excluded |
| `…/stream/contents/user/-/state/com.google/reading-list` | Identical (long form) |
| `…/stream/contents/user/-/state/com.google/starred` | Favourites |
| `…/stream/contents/user/-/state/org.freshrss/main` | Feeds with the "main" priority |
| `…/stream/contents/user/-/state/org.freshrss/important` | Feeds with the "important" priority |
| `…/stream/contents/feed/<id or url>` | A specific feed |
| `…/stream/contents/user/-/label/<name>` | A category or a tag |

With no stream segment, `reading-list` applies by default.

#### Parameters

| Parameter | Type | Default | Effect |
|---|---|---|---|
| `n` | integer | **20** | Maximum number of articles returned |
| `c` | integer (string) | — | Continuation token (see §3.5) |
| `r` | `d` \| `n` \| `o` | `d` | Order: `d`/`n` = descending date, `o` = ascending |
| `xt` | state identifier | — | **Exclude** articles carrying this state |
| `it` | state identifier | — | **Keep only** articles carrying this state |
| `ot` | Unix timestamp (s) | — | Articles later than this date |
| `nt` | Unix timestamp (s) | — | Articles earlier than this date |
| `ck` | Unix timestamp (s) | — | Cache buster. Accepted, with no functional effect |
| `output` | `json` | — | **No effect here**: the response is always JSON |

Accepted values for `xt` and `it`:

```
user/-/state/com.google/read
user/-/state/com.google/unread
user/-/state/com.google/starred
```

For a Discover feed, the useful call is therefore:

```
GET …/stream/contents/reading-list?n=40&xt=user/-/state/com.google/read
```

`ot` and `nt` filter on the article's publication **or** modification date, not
on the date the server fetched it — contrary to what Google Reader's historical
documentation suggests.

#### Response

```json
{
  "id": "user/-/state/com.google/reading-list",
  "updated": 1700000000,
  "items": [ … ],
  "continuation": "45219"
}
```

The `id` field is **always** `user/-/state/com.google/reading-list`, whatever
stream was requested: it cannot be used to identify the request.

*Observed against a personal instance, on 2026-08-07*: the root of the response
carries **only** these four keys — `id`, `updated`, `items`, `continuation` —
and `continuation` disappears at the end of the stream (see §3.5). No additional
metadata key is to be expected.

Shape of an article (`compat` mode, the one of this endpoint):

```json
{
  "id": "tag:google.com,2005:reader/item/00000000000b0b1f",
  "crawlTimeMsec": "1700000000000",
  "timestampUsec": "1700000000000000",
  "published": 1699999000,
  "title": "Article title",
  "canonical": [{ "href": "https://exemple.org/article" }],
  "alternate": [{ "href": "https://exemple.org/article" }],
  "categories": [
    "user/-/state/com.google/reading-list",
    "user/-/label/Tech",
    "user/-/state/org.freshrss/main",
    "user/-/state/com.google/read"
  ],
  "origin": {
    "streamId": "feed/12",
    "htmlUrl": "https://exemple.org/",
    "title": "Feed title"
  },
  "summary": { "content": "<p>…</p>" },
  "author": "Author name",
  "enclosure": [{ "href": "https://…/image.jpg", "type": "image/jpeg", "length": 12345 }]
}
```

*Observed against a personal instance, on 2026-08-07*, on real articles: the
keys actually present are `id`, `crawlTimeMsec`, `timestampUsec`, `published`,
`title`, `canonical`, `alternate`, `categories`, `origin`, `summary` and
`author` — `origin` itself carrying `streamId`, `htmlUrl` and `title`. Two
absences deserve to be noted:

- **`content` is absent**, as announced below: only `summary` exists in this
  mode. The observation confirms the reading of the source;
- **`enclosure` is absent** from every article observed. This is not a
  peculiarity of the instance: many RSS feeds simply emit no attachment at all.
  The consequence is direct for this project — a client that looked for an
  article's illustration only in `enclosure` would almost never find one. **It
  must fall back on the `<img>` tags of the HTML content** of `summary.content`,
  and treat `enclosure` only as a bonus when it is there.

Points to remember, all checked in `Entry.php`:

- **`id` is hexadecimal**, prefixed with `tag:google.com,2005:reader/item/`. The
  `continuation` token and `edit-tag`'s `i` parameter, on the other hand, are
  **decimal**. These are two representations of the same integer — the
  conversion is the client's responsibility, and it is a classic source of
  error.
- The content is in **`summary.content`**, and **truncated** by the server in
  this mode. The `content` field (untruncated) only appears in other modes,
  which are unreachable from this endpoint.
- **There is no "read" field**: the state is read from `categories`, through the
  presence of `user/-/state/com.google/read`. Its absence means "unread" —
  `…/unread` is never emitted in this mode.
- `author`, `enclosure` and `origin.htmlUrl` are **optional**.
- `published` is in seconds; `timestampUsec` in microseconds; `crawlTimeMsec` in
  milliseconds. Three different units in the same object.

#### `categories` mixes three heterogeneous forms — *observed*

This is the least visible trap of this response. *Observed against a personal
instance, on 2026-08-07*: one and the same `categories` array can contain, side
by side, three kinds of entry that share no naming convention.

| Kind | Form | Observed examples |
|---|---|---|
| System state | prefixed with `user/-/state/…` | `user/-/state/com.google/reading-list`, `user/-/state/org.freshrss/main`, `user/-/state/com.google/read` |
| Category (folder) | prefixed with `user/-/label/…` | `user/-/label/Sans catégorie` |
| **User tag** | **bare text, with no prefix at all** | `AirPods Ultra`, `iPhone Ultra`, `MacBook Ultra` |

Two consequences, one immediate, the other more insidious.

**One cannot assume that every entry is prefixed.** A client that split each
entry on `user/-/label/` to extract a readable name would drop the user tags,
which are nevertheless the ones most likely to interest the reader. The "no
known prefix" case must be treated as a nominal case, not as corrupt data.

**The membership test must be an exact equality, never a `startsWith` nor a
`contains`.** Since user tags are free text, nothing in theory prevents a user
from naming one literally `user/-/state/com.google/read`. An approximate test
would then mark as read every article tagged that way — and, symmetrically, a
tag containing the word `read` would be enough to mislead a `contains`. An
article's read state is too structuring a piece of information to be inferred
from a partial match: the only safe rule is full string equality with
`user/-/state/com.google/read`.

### 3.5 Pagination

The mechanism is **not** a simple offset. It works as follows, as read in
`streamContents()`:

1. The response carries a `continuation` field **only** if the number of
   articles returned reaches `n` — in other words, if something may remain. Its
   absence means "end of stream".
2. The value is the **decimal** identifier of the last article returned.
3. The next request repeats the same parameters, adding `c=<value>`.
4. The server then asks for `n + 1` articles starting from that identifier
   inclusive, then **discards the first one** — the one already sent.

#### What experience confirms — *observed*

*Observed against a personal instance, on 2026-08-07.* The three statements
above are no longer deductions from reading: they have been tested, and they
hold.

**1. The `continuation` is indeed the decimal identifier of the last article
returned.** A page of three articles whose hexadecimal `id`s end respectively in
`dde5`, `dde4` and `dde3` came with a `continuation` of `1786131047833059` —
that is, exactly the decimal value of identifier `…dde3`, the last one on the
page. The hexadecimal ↔ decimal conversion mentioned above is therefore not
theoretical: it is the key to any reasoning about pagination.

**2. The next page contains no duplicate.** Called again with
`c=1786131047833059`, the request returned `dde2`, `dde1`, `dde0` — and **not**
`dde3`. The article serving as the cursor is not sent again: the discarding of
the first element described in point 4 works as announced. A client therefore
has no deduplication to do on its side.

**3. An invalid cursor silently repeats the first page.** This is the most
serious point, and it deserves to be set apart.

> ⚠️ **The most dangerous trap of this API.** Queried with `c=anythingatall`,
> the server returns **no error**: no `400`, no message, no field reporting the
> anomaly. It answers `200`, with **the first page** of the stream and **the
> same `continuation`** as a call with no `c` at all.
>
> Observed. The consequence is severe: an error in serialising the cursor — an
> integer passed in a form PHP cannot read, a hexadecimal value sent where
> decimal is expected, an empty string, a textual `null` — **never** produces a
> failure, but an **infinite loop on the first page**. The client believes it is
> paginating, receives the same articles indefinitely, and nothing in the
> response tells it so.
>
> Two protections are called for on the client side: serialise the cursor in
> decimal in a checked way, and **detect repetition** — if a page returns a
> `continuation` identical to the previous one, or identifiers already seen, the
> loop must be stopped and treated as an anomaly, never continued.

#### End of stream — *observed*

The absence of `continuation` is indeed the **only** end signal, and it is
reliable. *Observed against a personal instance, on 2026-08-07*: called with
`n=100000`, the request returned 4645 articles — the whole stream — and the
response carried **no `continuation` field**. There is therefore no other marker
to look for: a client stops when the key is absent, full stop.

Incidentally, `n=100000` was **accepted without error**: no upper bound was met
at that value. One must not conclude that there is none — only that none was
reached here. And above all, asking for the whole stream in a single call
remains **inadvisable**: the response is materialised in memory in its entirety,
on the server side as on the client side, and latency grows with the number of
articles. Pagination exists to be used.

#### Consequences for the client

- Pagination is **relative to a cursor**, not to a rank: inserting an article at
  the top of the stream between two pages causes neither a duplicate nor a skip.
- A non-numeric `c` is silently brought back to `0`, that is, to the start of
  the stream. An error in serialising the cursor therefore shows up as a
  **repetition of the first page**, never as an error — observed.
- The cursor is only valid for one and the same set of parameters (`n`
  included): keeping it while changing filter makes no sense.

### 3.6 Identifiers alone

```
GET /api/greader.php/reader/api/0/stream/items/ids?s=<streamId>&n=…&c=…&xt=…
```

The same filtering and pagination parameters as `stream/contents`, but returns
the identifiers only. Useful for reconciling a local cache without downloading
the contents again.

Here, the stream is designated by the **`s`** parameter, and not by the path.

```
POST /api/greader.php/reader/api/0/stream/items/contents
i=<id>&i=<id>&…
```

Retrieves several articles by identifier. Repeated `i` parameter, in `POST`.

---

## 4. Writing

### 4.1 Marking as read / unread

```
POST /api/greader.php/reader/api/0/edit-tag
Content-Type: application/x-www-form-urlencoded

T=<token>&a=user/-/state/com.google/read&i=<id>&i=<id>
```

| Field | Role |
|---|---|
| `T` | Modification token (§2.3) |
| `a` | State to **add**. Repeatable |
| `r` | State to **remove**. Repeatable |
| `i` | Article identifier. **Repeatable** |

- Mark as read: `a=user/-/state/com.google/read`
- Mark as unread: `r=user/-/state/com.google/read`
- Favourite: `a=` / `r=user/-/state/com.google/starred`

The `i` field accepts both forms: decimal, or prefixed hexadecimal
(`tag:google.com,2005:reader/item/…`). The server detects the form and converts.

**Processing is batched**: a single call can carry several `i`. That is what
allows a Discover feed to group markings rather than issue one request per
visible article.

> ⚠️ **The batch is not unlimited, and exceeding it is silent.** The bound does
> not come from FreshRSS but from PHP: `max_input_vars` is 1,000 by default, and
> beyond that the excess fields are **silently ignored**. `edit-tag` would then
> answer `OK` with no report signalling the lost articles — the API produces
> none anyway. This limit is inferred from PHP's behaviour, it has **not** been
> observed against a server; that is precisely why this project caps its batches
> well below it (100).

`user/-/state/com.google/broadcast`, `…/like` and `…/tracking-kept-unread` are
accepted but **ignored** — FreshRSS does not implement them.

**Response**: `OK` in plain text. No JSON body, no per-article report. A
non-existent article produces no error.

### 4.2 Marking everything as read

```
POST /api/greader.php/reader/api/0/mark-all-as-read
T=<token>&s=<streamId>&ts=<timestamp>
```

`ts` is in **nanoseconds** and means "only articles older than". It must consist
of digits only, otherwise `400`.

Careful: three time units coexist in this API — `ot`/`nt` in seconds,
`newestItemTimestampUsec` in microseconds, `ts` in nanoseconds.

---

## 5. Error codes

| Code | Meaning on the FreshRSS side | Expected handling on the client side |
|---|---|---|
| `400` | Malformed request, or syntactically invalid identifier | Input or programming anomaly: do not retry |
| `401` | Token absent or invalid, unknown username, API password changed — **or unknown path** | Back to the login screen |
| `404` | **The host is not a FreshRSS instance**, or the URL prefix is wrong | Server address to be corrected |
| `500` | Server configuration missing | Server error, retry possible |
| `501` | `output` other than `json` where it is required | Programming anomaly |
| `503` | API disabled on the server | Explicit message: "enable the API in FreshRSS" |

> ⚠️ **`404` does not mean "unknown endpoint".** Authorization is checked
> **before** routing: a non-existent path under `/reader/api/0/` answers `401`,
> not `404`. Observed on `…/reader/api/0/nexistepas`.
>
> Consequence: a `404` received by an authenticated client designates the
> **host**, not the path — wrong server address, or a FreshRSS installation in a
> subdirectory that has not been accounted for. And a `401` does not prove that
> the credentials are wrong: it may equally betray a typo in a path. Hence the
> value of the §1.1 probe **before** any connection attempt.

The error bodies are in **plain text**, never JSON — observed:
`text/plain; charset=UTF-8`. Trying to deserialise an error response would fail
and hide the real code.

---

## 6. Points to be validated against a real server

These items could not be established with certainty by reading alone. They must
be **observed** before being taken for granted — and this section updated
accordingly.

| # | Point | Why it is uncertain |
|---|---|---|
| 1 | Maximum value accepted for `n` | `n=100000` was **accepted without error** (§3.5): no bound was reached at that value. That does not prove none exists — a limit may exist downstream (memory, PHP execution time), and would only show up on a bulkier stream |
| 2 | Actual truncation length of `summary.content` | The `API_MAX_COMPAT_CONTENT_LENGTH` constant has not been read |
| 3 | Behaviour of `continuation` in ascending order (`r=o`) | The cursor logic was tested in descending order only (§3.5); the reverse order has not been tried |
| 4 | Actual presence of `enclosure` depending on the feed | Depends on the source RSS feeds, not on FreshRSS. Partial observation: **absent from every article observed** (§3.4), which is enough to decide not to rely on it |
| 5 | Number of `i` accepted in an `edit-tag` | Limited in practice by the size of the POST body and PHP's `max_input_vars` (§4.1). The exact value depends on the server's configuration and **has not been observed**; the project works around it by capping its batches at 100 rather than looking for the bound |
| 6 | Exact form of `frss:priority` | Values come from an enumeration that has not been read |

These points are tracked collectively by the "Open questions" section of
[TASKS.md](../TASKS.md): each is settled by the Goal that meets it, not by a
task opened in advance. None of them must be "assumed" in the code: if a Goal
needs one, it starts by observing it, then records it here and moves it to the
next section.

### What has been observed, and now counts as established

- recognising an instance: a bare `GET` on the root → `OK` body (§1.1);
- a query string on the root → `400`;
- `check/compatibility`: status always `200`, verdict in the body,
  `Authorization` header required in the request itself (§1.2);
- the `400` / `401` boundary of `ClientLogin`: syntax versus existence (§2.1);
- unknown user and wrong password are **indistinguishable** (§2.1);
- unknown path under `/reader/api/0/` → `401`, never `404` (§5);
- error bodies in `text/plain; charset=UTF-8` (§5);
- API disabled: `503` everywhere **except** on the recognition probe, which goes
  on answering `OK` (§1.1 and §2.1).

Established since gaining access to a personal instance *(2026-08-07)*:

- **successful `ClientLogin`**: `200`, `text/plain`, exactly three lines
  `SID` / `LSID=null` / `Auth`, `SID` and `Auth` carrying the same value (§2.1);
- **the modification token is exactly 57 characters long** (§2.3);
- `user-info` returns `userId`, `userName`, `userProfileId`, `userEmail` — the
  last of which may be **empty** (§2.4);
- **the real shape of an article**: a four-key root, an eleven-key article, a
  three-key `origin`. **`content` is absent**, **so is `enclosure`** on every
  article observed — the illustration must be looked for in the `<img>` tags of
  the content (§3.4);
- **`categories` mixes three forms**: prefixed system states, prefixed
  categories, and **user tags as bare text**. The membership test must be an
  **exact equality** (§3.4);
- **the `continuation` is indeed the decimal identifier of the last article
  returned**, and the next page contains **no duplicate** (§3.5);
- **an invalid cursor silently repeats the first page**, with no HTTP error — so
  an infinite loop is possible, to be detected on the client side (§3.5);
- **the absence of `continuation` is indeed the only end-of-stream signal**:
  4645 articles returned at once, with no `continuation` (§3.5);
- `n=100000` is **accepted without error**; no bound reached, which does not
  mean none exists (§3.5 and §6, point 1).
