# Social Cues

An independent, open-source alternative to WATUT: it shows other players what
they are actually doing — typing, in a menu, idle, or speaking — through in-game
visuals rather than a chat message.

It ships as two things, published separately because they are installed by
different people in different places:

| Artifact | Platform | Versions |
|---|---|---|
| Fabric mod | client and Fabric servers | 1.21 → 1.21.11 (twelve jars) |
| Paper plugin | Bukkit / Spigot / Paper / Purpur / Leaf | all of 1.21.x (one jar) |

The server side is not optional. A vanilla server does not forward unknown
custom payloads to other clients, so something has to relay them: the Fabric mod
if the server runs Fabric, the plugin if it runs Paper.

See **`CLEANROOM.md`** for the clean-room rule: this project was built without ever
reading WATUT's code, from its publicly described behaviour and the official
Minecraft / Fabric / Bukkit APIs only.

## Status

Version `1.0.0`. All three cue layers (nametag icon, tab-list icon, body
language and the held screen panel), the configuration screen, the privacy
switches, and all twelve Minecraft rows are implemented. `buildAll` produces
twelve Fabric jars plus the Paper jar, `:core:test` runs 435 tests, and
`tools/verify-mixins.py` confirms every mixin target resolves on all twelve rows.

Compiling is not the same as running, so release channels follow what has
actually been played rather than what has been built. `versions.json` records
that per row in a `handTested` field, and CI turns it into the Modrinth channel:

| Row | Channel |
|---|---|
| 1.21, 1.21.11 | `release` — hand tested |
| 1.21.1 – 1.21.10 | `beta` — builds and passes mixin verification, not yet played |

## Optional integrations

All four are silently inert when absent. None is required, and none of them has
a single byte in the published jars.

| Integration | What it adds | How it attaches |
|---|---|---|
| Mod Menu | opens the settings screen from the mod list | `modmenu` entrypoint |
| Cloth Config | the settings screen itself | jar-in-jar (bundled) |
| Simple Voice Chat | the "speaking" cue | `voicechat` entrypoint, `compileOnly` |
| PlaceholderAPI | server-side `%socialcues_*%` placeholders | `softdepend`, `compileOnly` |

The last two are not licensed the same way this project is (Simple Voice Chat is
All Rights Reserved, PlaceholderAPI is GPL-3.0). Both are on the **compile
classpath only**: none of their bytes are redistributed, and every reference to
either is confined to a single file so the claim can be machine-checked instead
of taken on trust:

```
unzip -l mc/1.21.11/build/libs/socialcues-fabric-1.21.11-1.0.0.jar | grep de/maxhenkel
unzip -l paper/build/libs/socialcues-paper-1.0.0.jar               | grep me/clip
```

Both must come back empty.

## Module layout

```
core/         Pure Java protocol and state model. No net.minecraft.* or org.bukkit.* imports.
mc-shared/    Shared Fabric code that compiles identically on all twelve rows.
adapters/     Render buckets (A/B/C/D) — version-specific, mixin-heavy code.
mc/           :mc:<version> projects, generated from versions.json by settings.gradle.kts.
paper/        The single-jar Bukkit/Paper plugin.
```

`core/` never depends on Minecraft or Bukkit types. That is what lets the Fabric
mod and the Paper plugin share one protocol and state model, which in turn makes
protocol drift between the two sides impossible. The rule is enforced by the
`checkCleanRoom` Gradle task (`./gradlew :core:check`), not just by convention.

## Building

Requires **JDK 21** — not just to target, but to run Gradle on. Loom 1.17.17 has
only been verified there, so the build refuses to start on anything else and
tells you how to fix it. Set `JAVA_HOME`, or put `org.gradle.java.home` in your
own `~/.gradle/gradle.properties`; it is deliberately not committed here, since
an absolute path in a shared file means nobody else can build the repo.

```
./gradlew :core:test                 # protocol and state-model unit tests
./gradlew :mc:1.21.11:build          # a single Fabric row
./gradlew :paper:build               # the Paper plugin
./gradlew buildAll                   # all twelve Fabric jars plus the plugin
```

The first `:mc:*` build downloads and decompiles the Minecraft jars and Yarn
mappings for that row; expect it to take a while.

Measurement tools — version boundaries are measured here, never guessed:

```
tools/seam.sh <class> [pattern]      # which rows actually have this member?
python3 tools/verify-mixins.py       # will the mixins really bind?
python3 tools/gen_icons.py           # the cue icon atlas (generator lives in the repo)
python3 tools/gen_mod_icon.py        # the mod and project icon
```

The version matrix (`mc`, `yarn`, `fabric-api`, render bucket, Loom version)
lives in `versions.json` in machine-readable form. The twelve build files are
not written by hand; `settings.gradle.kts` generates them from that one file.

## License

MIT — see `LICENSE`.
