# Console (Private Mode)

A hidden, PIN-gated space inside the app for exactly two paired partners — messaging, a shared
calendar, a period tracker, and a private photo vault ("Safe Locker"). Entered via a long-press
on the app logo in the Overview screen's toolbar (deliberately undocumented in the UI itself —
short tap keeps the logo's normal branding behavior).

## How it works

- **Trigger:** long-press the logo → account setup (first run) or a PIN prompt (every run
  after). The public app around it is completely unaware this exists; there's no visible menu
  item, setting, or icon hinting at it.
- **PIN:** local only, never sent to the server — a salted hash lives in Keystore-backed
  `EncryptedSharedPreferences`, separate from every other credential the app stores. Repeated
  failures trigger an escalating lockout (30s → 60s → 120s → 240s, capped at 300s), enforced
  before the candidate PIN is even hashed.
- **Local storage:** its own SQLCipher-encrypted Room database (`console.db`), entirely separate
  from anything the public Device Info X app touches, excluded from Android Auto Backup. Photos
  and voice notes live in private app-storage directories — never MediaStore, never visible in
  the system gallery.
- **Sync model:** everything (messages, calendar, period data, Safe Locker) is backed by a
  server account and kept in sync over REST + a WebSocket for realtime delivery (typing
  indicators, read receipts, live message/calendar updates). Push notifications go through
  Firebase Cloud Messaging, always as a single generic/disguised notification — the payload
  never contains message content, and the two `NotificationTier` styles (Settings → Security)
  control only what the *notification itself* looks like, not what's transmitted.
- **Pairing:** two separate accounts, deliberately not a shared login — one partner generates a
  one-time code, the other redeems it. Either side can unpair later (data is retained, just
  locked until re-paired).

## Requires a server — this app doesn't work standalone

The console needs a running instance of the sibling backend,
**[device-info-x-server](https://github.com/wwwescape/device-info-x-server)** (FastAPI +
PostgreSQL, self-hosted). There's no public/shared server — every deployment serves exactly one
couple, hard-capped at 2 accounts. You (or whoever's setting this up) need to deploy that repo
somewhere reachable from both partners' phones before any of this works. See its README for the
full deploy guide (Docker, HTTPS/Let's Encrypt, Firebase service-account setup, backups, etc.) —
it's written for zero prior Docker/Firebase/Nginx experience.

## Getting it running, end to end

**Before you start, gather:**
- Two phones (one per partner) able to reach the server's address, whether that's a LAN IP for
  local testing or a public domain for a real deployment.
- A place to run the server — anything from a spare machine on your network to a small VPS. See
  [device-info-x-server's Prerequisites](https://github.com/wwwescape/device-info-x-server#prerequisites).
- A Firebase project (free tier is fine) — see [Firebase setup](#firebase-setup-push-notifications)
  below.

Data lives on the server, not the phone — Room is a synced local cache, not the source of truth.
Losing or wiping a phone doesn't lose any messages/calendar/period/Safe Locker data, as long as
you *log back into the same account* on a new device rather than registering a fresh one. The
one thing that's genuinely destroyed on-device-only is the local PIN, which gets set again on
next login regardless.

1. **Deploy the server** — follow
   [device-info-x-server's README](https://github.com/wwwescape/device-info-x-server), through
   at least "Local development" or "Deploying with Docker". You'll come out of this with a
   reachable server URL and a `SERVER_SETUP_TOKEN` value (set in that repo's `.env` — a shared
   secret that gates registration so strangers can't self-register on your server).
2. **Set up push for this app** — see [Firebase setup](#firebase-setup-push-notifications)
   below. (The server needs its *own*, separate Firebase service-account credentials to actually
   *send* the pushes — that's covered in the server README's Firebase section, not here.)
3. **Build and install this app** on both partners' phones (`./gradlew installDebug`, or a
   signed release build — see the main `README.md`).
4. **On each phone**, long-press the logo → Register: server URL, the `SERVER_SETUP_TOKEN`,
   a username + password (this device's own account, not shared between partners), display
   name, and gender. Birthday and photo aren't asked here — they're optional, set later from
   Settings → General → Profile if you want them.
5. Right after registering, you're prompted to set a local PIN (enter twice to confirm). This is
   what gates entry from now on — the server login only happens again if the session lapses.
6. **Pick one phone to generate a partner code:** Settings → Pairing → copy the code shown, send
   it to your partner however you like (text, read it aloud — it's single-use and expires in 15
   minutes, not something that needs its own secure channel).
7. **On the other phone:** Settings → Pairing → "Redeem a code" → paste it in.
8. Once paired, messaging, the shared calendar, period tracking, and Safe Locker all unlock on
   both devices.

## Firebase setup (push notifications)

The console's push notifications need a Firebase config file that isn't checked in
(`app/google-services.json`, gitignored — see `app/google-services.json.example` for the
expected shape). Without it, the build fails outright (the Google Services Gradle plugin
requires the file to be present).

If you haven't created the Firebase project yet, follow
[device-info-x-server's Firebase setup guide](https://github.com/wwwescape/device-info-x-server#firebase-setup)
— steps 1–3 there (create the project, register the Android app, download `google-services.json`)
are the ones that produce this file, written for zero prior Firebase experience. Drop the
downloaded file at `app/google-services.json` before building. Steps 4+ in that same guide cover
the *server's* separate service-account credentials, needed for it to actually send the pushes.

## Notification wording reference

What each push notification actually says today, by category (`PushCategory` in
`ConsoleFcmService.kt`) and by the `NotificationTier` chosen in Settings → Security. Recorded here
as a snapshot of current behavior — useful for spotting what's inconsistent or worth changing
before touching the notification code (see `TODOS.md`'s notification items).

A few things true of every row below:
- Each category now has its own fixed notification slot (`NOTIFICATION_ID_BASE + category.ordinal`)
  — a push of one category no longer replaces an unseen notification of a different one. A
  *second* push of the *same* category before the first is dismissed collapses into it instead of
  stacking, appending a real, live `" (x2)"`/`" (x3)"`/… suffix to the **title** (never the body)
  once the count is above 1 — no suffix at count 1. The count resets to 0 the moment that
  category's notification is either tapped (`MainActivity`) or swiped away
  (`NotificationDismissReceiver`), whichever happens first (see `TODOS.md`).
- `EVENT_NOW` and `EVENT_UPCOMING` are the same server push type (`"reminder"`), split client-side
  by whether `minutes_before` is `0`.
- `ONLINE` is the one category with no server-side *event* behind it at all — it's fired by the
  Messages header icon's manual "let them know you're online" ping
  (`presence_service.send_online_ping`, rate-limited to once per 15 minutes server-side), not by
  something happening to shared data.
- One deliberate exception bypasses this table entirely: a `"birthday"` push always shows
  **"Happy Birthday!" / "Wishing you a wonderful day."**, regardless of tier — the one case where
  disguising the wording would reveal more than showing it plainly, since a birthday reminder from
  an ordinary app doesn't hint at anything. It also doesn't participate in the count/suffix
  behavior above — its own fixed id, never expected to arrive twice in quick succession.

### Generic

| Category | Title | Body |
|---|---|---|
| MESSAGE | Device Info X | New message |
| EVENT_NOW | Device Info X | Event starting now |
| EVENT_UPCOMING | Device Info X | Upcoming event reminder |
| PERIOD_UPCOMING | Device Info X | Cycle update |
| PERIOD_NOT_LOGGED | Device Info X | Cycle reminder |
| ONLINE | Device Info X | Available now |
| OTHER | Device Info X | New update |

### Disguised

| Category | Title | Body |
|---|---|---|
| MESSAGE | Cache Refreshed | App cache refreshed in the background. |
| EVENT_NOW | Scheduled Sync | A scheduled sync is starting now. |
| EVENT_UPCOMING | Scheduled Sync | A scheduled sync is starting soon. |
| PERIOD_UPCOMING | Storage Notice | Routine cleanup recommended in the next few days. |
| PERIOD_NOT_LOGGED | Diagnostic Incomplete | Last diagnostic scan didn't finish. Tap to retry. |
| ONLINE | 5G Available | A faster network connection is now available. |
| OTHER | CPU Load Alert | Unusual background activity detected. Tap for details. |

## Fake card wording reference

The fake in-app card (`FakeNotificationCardState`, rendered above `DashboardHero()` in the
*public* Overview screen — the innocent app, not the console) is a faithful echo of whatever's
currently in the notification shade: `ConsoleFcmService` calls `.show(title, body)` with the exact
same `title`/`body` it already resolved for the push's tier/category, count-suffix included, from
both `showNotification()` and `showBirthdayNotification()` — deliberately not gated on category,
so its wording is always identical to the table above, row for row. Recorded here for a
standalone reference without needing to cross-check the notification table.

### Generic

| Category | Title | Body |
|---|---|---|
| MESSAGE | Device Info X | New message |
| EVENT_NOW | Device Info X | Event starting now |
| EVENT_UPCOMING | Device Info X | Upcoming event reminder |
| PERIOD_UPCOMING | Device Info X | Cycle update |
| PERIOD_NOT_LOGGED | Device Info X | Cycle reminder |
| ONLINE | Device Info X | Available now |
| OTHER | Device Info X | New update |

### Disguised

| Category | Title | Body |
|---|---|---|
| MESSAGE | Cache Refreshed | App cache refreshed in the background. |
| EVENT_NOW | Scheduled Sync | A scheduled sync is starting now. |
| EVENT_UPCOMING | Scheduled Sync | A scheduled sync is starting soon. |
| PERIOD_UPCOMING | Storage Notice | Routine cleanup recommended in the next few days. |
| PERIOD_NOT_LOGGED | Diagnostic Incomplete | Last diagnostic scan didn't finish. Tap to retry. |
| ONLINE | 5G Available | A faster network connection is now available. |
| OTHER | CPU Load Alert | Unusual background activity detected. Tap for details. |

As with the notification shade, a `"birthday"` push bypasses tier wording entirely and always
shows **"Happy Birthday!" / "Wishing you a wonderful day."** on the fake card too, and a repeat
push of the same category before the card is dismissed appends the same live `" (x2)"`/`" (x3)"`/…
suffix to the title, mirroring the notification's own count/suffix behavior described above.

## Changing servers or starting over

Settings has two relevant escape hatches, both under the last two categories:
- **Server → Change server** — wipes all local data and points the app at a different server
  URL. Use this if you're moving to a new deployment; your account and data on the *old* server
  are untouched, this device just disconnects from it.
- **Dangerous → Delete all data / Destroy all data** — "Delete all data" wipes the 4 content
  sections but keeps the account; "Destroy all data" (PIN re-verification required) deletes the
  account itself, server-side, and unpairs your partner.

## Troubleshooting

- **Build fails immediately, mentions `google-services.json`.** It's missing from `app/` — see
  [Firebase setup](#firebase-setup-push-notifications) above. The Google Services Gradle plugin
  needs it present just to configure the project, before any real build work starts.
- **Registration returns 403, "cap reached" (or similar).** The server permanently refuses new
  registrations once 2 accounts exist, regardless of the setup token — this is by design (one
  couple per deployment), not a bug. If you registered a throwaway/test account and need a
  clean slate, you'll need to remove it directly in the server's database, or redeploy.
- **Most screens look empty or locked right after registering.** Expected before pairing —
  messaging, the shared calendar, and Safe Locker all require both accounts to be paired first
  (see step 6–7 above). Period Tracker is the one exception; it works solo.
- **Can't reach the server from a phone at all.** Usually networking, not the app — confirm the
  phone can open the server's URL in a normal browser first. For anything beyond local
  same-Wi-Fi testing, see
  [device-info-x-server's Port forwarding and HTTPS sections](https://github.com/wwwescape/device-info-x-server#https-lets-encrypt-and-the-reverse-proxy).
- **Typing indicators / realtime updates aren't showing up, but everything else works.** REST
  traffic (sending messages, loading the calendar) and the WebSocket are separate connections —
  a WebSocket auth failure won't break the app, just realtime delivery. Check the server logs
  (`docker compose logs -f api` if using Docker) for `WS auth rejected` lines around the time it
  happened.
