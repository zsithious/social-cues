# Changelog

Notable changes per release. Dates are ISO-8601.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [semantic versioning](https://semver.org/) with a
`+mc<version>` build suffix on the Modrinth releases (`1.0.0+mc1.21.4`), because
one source release ships as twelve Fabric jars plus one Paper jar.

## [1.0.0] — unreleased

First public release. Everything below is the initial feature set rather than a
diff against anything, so it is grouped by what it does rather than by
added/changed/fixed.

### Cues

- **Nametag cue.** A status icon beside the player's name, obeying every rule
  the nametag itself obeys (F1, distance, spectator, through-wall behaviour).
  Fades with distance; range and scale are configurable. The icon carries its
  own motion, so a fast typist and a slow one look different.
- **Tab list column.** An 8×8 status icon in the player list, drawn immediately
  before the ping bars.
- **Pose and held screen.** Typing moves the arms at the rate the player is
  really typing, with the head tilted toward their hands; an open container
  puts a translucent panel in their hands styled after the screen they actually
  have open; idling slumps the shoulders and, after long enough, adds a "Zz".
  Each of the three layers switches off independently.

### What it can tell apart

- Typing: chat, commands, signs, books — with commands detected purely from
  keycodes, never from the text.
- Screens: 25 vanilla container/UI types, plus a generic cue for unrecognised
  modded screens.
- Idle, with a second deeper "gone a while" stage.
- Speaking, via the optional Simple Voice Chat integration.
- Sneaking, as a modifier on any of the above.
- Typing intensity: a rolling keystrokes-per-second average that drives the
  animation's speed and energy.

### Privacy

- The wire payload is four fields: activity, screen type, an intensity byte,
  and one flags byte. Written text is never read, not even transiently — the
  build fails if any source under the client modules so much as calls a text
  accessor.
- Per-signal sharing switches (typing / screens / screen detail / idle / voice),
  plus a **share nothing** master switch that suspends all of them without
  discarding the individual preferences.
- `reducedMotion` and `textOnly` accessibility modes.
- Server policy is always the ceiling: a client can share less than the server
  permits but never more, and a signal the policy forbids is never even
  measured client-side.
- AFK visibility is a server-side setting (`off` / `nearby` / `all`) defaulting
  to `nearby`, because a server-wide list of who stepped away is a target list.

### Platforms

- Fabric 1.21 → 1.21.11, twelve separate builds.
- One Paper/Purpur/Spigot/Leaf plugin jar covering all of them (no NMS; built
  against the oldest supported API).
- Optional integrations, each silent when absent: Mod Menu and Cloth Config for
  the settings screen, Simple Voice Chat for the speaking cue, PlaceholderAPI
  for `%socialcues_*%` placeholders on the server side.

### Notes

- No third-party code is redistributed. The Simple Voice Chat API (All Rights
  Reserved) and PlaceholderAPI (GPL-3.0) are compile-only; no byte of either
  reaches a published jar.
- Written from scratch against the public Minecraft, Fabric and Bukkit APIs.
  No other presence mod's source was read, copied or decompiled — see
  `CLEANROOM.md`, and the `checkCleanRoom` Gradle task that enforces it.
