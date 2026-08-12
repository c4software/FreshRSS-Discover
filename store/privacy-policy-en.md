# Privacy Policy — FreshRSS Discover

Last updated: 13 August 2026

## Summary

FreshRSS Discover collects nothing, sends nothing to us and stores nothing
outside your device. It has no server of ours, no account of ours, no analytics
and no advertising. The only server it talks to is **your own FreshRSS
instance**, whose address you enter yourself.

## Data the app handles

All of the following stays on your phone, in the app's private storage, and is
deleted when the app is uninstalled:

| Data | Why | Where it goes |
|---|---|---|
| The address of your FreshRSS server and your login | To reach your instance | Local preferences, in clear text, on the device only — neither is a secret |
| The session token issued by your server | To reopen the app without logging in again | Local preferences, **encrypted**, the key held by the device keystore |
| A cache of articles (title, excerpt, source, date, link, illustration URL) | To let you read the feed offline and reopen it as you left it | Local database, on the device only |
| Read statuses waiting to be sent | So that a network outage does not lose what you have read | Local database, on the device only |
| Your settings (presentation mode, marking thresholds, reminder) | To remember your choices | Local preferences, on the device only |

**Your API password is never stored.** It is used once, to obtain the session
token from your server, and then dropped.

The app never collects your name, your email address, your contacts, your
files, your position or any device or advertising identifier.

## Outgoing connections

The app opens connections to three destinations, and to no others:

1. **Your FreshRSS server**, to authenticate, fetch articles and send back read
   statuses. The credentials you entered are sent to that server and to nothing
   else.
2. **The hosts serving the illustrations of the articles**, to display them.
   These are the sites your feeds point to; the app does not choose them, your
   subscriptions do.
3. **Your browser**, when you open an article. From then on the page is handled
   by the browser, under its own privacy rules.

There is **no background synchronisation**: nothing is fetched while the app is
closed. The daily reading reminder reads the local cache and opens no
connection.

## Unencrypted connections

An `http://` address is accepted, because self-hosted instances on a local
network often have no certificate. The connection screen states plainly, as
soon as the address begins with `http://`, that the link is not encrypted. On
such a link, your credentials travel in clear text over your network — that is
your decision to make, and it is announced before you make it.

## Permissions

| Permission | What it is for |
|---|---|
| `INTERNET` | Reaching your FreshRSS server and loading article images |
| `ACCESS_NETWORK_STATE` | Telling "offline" apart from "server unreachable", so the app falls back to its cache instead of showing a misleading error |
| `POST_NOTIFICATIONS` | Showing the daily reading reminder. Refusing it only removes the reminder |
| `ACCESS_LOCAL_NETWORK` (Android 17+) | Reaching a FreshRSS instance hosted on your local network. It grants no access to your position |

None of these permissions is used to collect data.

## Logging out

Logging out erases the stored credentials, the session token and the local
cache from the device.

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Any change to this policy will be published in the application's public
repository, together with the version it applies to.

## Contact

Questions or requests: open an issue on
<https://github.com/c4software/FreshRSS-Discover/issues>, or write to
<c4software@gmail.com>.
