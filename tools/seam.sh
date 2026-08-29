#!/usr/bin/env bash
# DESIGN.md §7 "P7 uygulama notu":
# "Sürüm sınırlarını tahmin etme, javap ile ölç."
#
# Prints, for every Minecraft row in versions.json, whether a member exists on a
# class — read out of that row's own *mapped* (yarn-named) Minecraft jar, the
# same artifact the compiler sees. This is the tool every version-boundary claim
# in DESIGN.md was produced with; it was rewritten from the working note because
# the original lived in /tmp and did not survive the session.
#
# Usage:
#   tools/seam.sh <yarn.class.Name> [grep-pattern]
#
#   tools/seam.sh net.minecraft.client.gui.DrawContext drawTexture
#   tools/seam.sh net.minecraft.client.render.entity.state.PlayerEntityRenderState
#   tools/seam.sh net.minecraft.client.render.RenderLayers ''
#
# With no pattern the whole javap signature listing is printed per version;
# with one, only matching lines. A version where the class itself is absent is
# reported as "(class absent)" — that is a measurement, not an error.
set -u

MAVEN_DIR="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"
# JAVA_HOME if set, otherwise whatever javap is on PATH.
JAVAP="${JAVA_HOME:+$JAVA_HOME/bin/}javap"
if ! command -v "$JAVAP" >/dev/null 2>&1; then
    echo "seam: javap not found at '$JAVAP'." >&2
    echo "Set JAVA_HOME to a JDK 21 installation -- a JRE is not enough, javap ships only with the JDK." >&2
    exit 1
fi
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ $# -lt 1 ]; then
    sed -n '2,22p' "${BASH_SOURCE[0]}"
    exit 2
fi

CLASS="$1"
PATTERN="${2-}"

# Read the version list from versions.json itself, so this tool can never drift
# from the matrix the build uses.
VERSIONS=$(grep -o '"mc": *"[^"]*"' "$ROOT/versions.json" | sed 's/.*"mc": *"\(.*\)"/\1/')

for v in $VERSIONS; do
    jar=$(ls "$MAVEN_DIR/$v-"*/minecraft-merged-"$v-"*.jar 2>/dev/null | grep -v '\.backup$' | head -1)
    if [ -z "$jar" ]; then
        printf '%-8s  !! no mapped jar cached (build this row once)\n' "$v"
        continue
    fi
    out=$("$JAVAP" -cp "$jar" "$CLASS" 2>/dev/null)
    if [ -z "$out" ]; then
        printf '%-8s  (class absent)\n' "$v"
        continue
    fi
    if [ -z "$PATTERN" ]; then
        printf '=== %s ===\n%s\n' "$v" "$out"
    else
        hits=$(printf '%s\n' "$out" | grep -- "$PATTERN")
        if [ -z "$hits" ]; then
            printf '%-8s  -\n' "$v"
        else
            printf '%-8s\n%s\n' "$v" "$(printf '%s\n' "$hits" | sed 's/^/          /')"
        fi
    fi
done
