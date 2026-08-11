#!/usr/bin/env python3
"""Prove a built jar's mixins will bind, without launching the game.

DESIGN.md §7 (the method P5 introduced and P7 reused by hand): a jar that
*compiles* proves only that the mapped Minecraft jar had the members the source
named. It does not prove that the mixin remapper found a mapping for each
target, nor that the remapped intermediary name really exists on the class the
@Mixin points at. Those are different failures and both of them show up at
runtime as a mixin that silently does not apply, i.e. as "the feature is just
missing" -- the single hardest symptom this project has to debug (see the P4/P5
hand-test rounds in DESIGN.md §7).

So this reads the *remapped* jar back: every class under a bucket's mixin
package, its @Mixin target class and every @Inject/@ModifyArg/... method= value,
all of which loom has already rewritten to intermediary. Then it looks each one
up in that row's own intermediary-v2.tiny -- the same file the loader will use
at launch -- and checks the member exists on that exact owner.

Usage:
    tools/verify-mixins.py [version ...]      # default: every row in versions.json

Each row must have been built first (./gradlew :mc:<v>:build), because the jar
being checked is the artifact that ships.
"""

import json
import pathlib
import re
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOOM_CACHE = pathlib.Path.home() / ".gradle/caches/fabric-loom"
JAVAP = pathlib.Path("/home/erto/jdk21/bin/javap")

# javap renders the annotations we care about like:
#   org.spongepowered.asm.mixin.Mixin(value=[class Lnet/minecraft/class_355;]
#   method=["method_1923"]
MIXIN_TARGET = re.compile(r"value=\[class L([^;]+);")
METHOD_VALUES = re.compile(r'method=\[([^\]]*)\]')
STRING_LIT = re.compile(r'"([^"]*)"')
# javap -v prints the raw class-file header, which is where a superclass can be
# read without a bytecode library: "  super_class: #5   // fvx"
SUPER_CLASS = re.compile(r"^\s*super_class:\s*#\d+\s*//\s*(\S+)", re.M)


def rows():
    data = json.loads((ROOT / "versions.json").read_text())
    return {r["mc"]: r for r in data["versions"]}


DESC_CLASS = re.compile(r"L([^;]+);")


def load_tiny(version):
    """{owner_intermediary: {member_name: [descriptor_in_intermediary, ...]}}.

    intermediary-v2.tiny holds two namespaces, official (obfuscated) first and
    intermediary second, and a member line carries its descriptor in the
    *official* one -- `(Lgzf;Lxv;...)V`, not `(Lnet/minecraft/class_10055;...)V`.
    The mixin descriptors we are checking it against came out of a remapped jar
    and so are intermediary throughout. Comparing the two namespaces directly is
    how the first cut of this tool managed to call all twelve rows broken while
    every one of them was fine, so the class half of each descriptor is
    translated here, once, before anything is compared.
    """
    tiny = LOOM_CACHE / version / "intermediary-v2.tiny"
    if not tiny.exists():
        raise SystemExit(f"{version}: no intermediary-v2.tiny at {tiny} -- build this row once")
    lines = tiny.read_text(encoding="utf-8").splitlines()

    official_to_inter = {}
    for line in lines:
        if line.startswith("c\t"):
            parts = line.split("\t")
            if len(parts) > 2:
                official_to_inter[parts[1]] = parts[2]

    def to_intermediary(desc):
        # Names absent from the map are simply not obfuscated (java/lang/String,
        # com/mojang/... and friends) and pass through unchanged.
        return DESC_CLASS.sub(lambda m: "L" + official_to_inter.get(m.group(1), m.group(1)) + ";", desc)

    owners, cur = {}, None
    for line in lines:
        if line.startswith("c\t"):
            parts = line.split("\t")
            cur = parts[2] if len(parts) > 2 else None
            if cur:
                owners.setdefault(cur, {})
        elif cur and (line.startswith("\tm\t") or line.startswith("\tf\t")):
            parts = line.split("\t")
            if len(parts) > 4:
                owners[cur].setdefault(parts[4], []).append(to_intermediary(parts[2]))
    return owners, official_to_inter


_HIERARCHY_CACHE = {}


def supertypes(version, owner, official_to_inter):
    """[owner, its superclass, ...] in intermediary names, Object excluded.

    A mixin's method= may perfectly legitimately name a method its target class
    only *inherits the name of*. Intermediary assigns a member name once, at the
    highest declaration, and an override with the same descriptor keeps it -- so
    the tiny file lists that member under the superclass and not under the
    subclass, even though the subclass really does declare an override.
    PlayerEntityModel#setAngles on 1.21/1.21.1 is exactly that (declared on
    BipedEntityModel, overridden by PlayerEntityModel, same erasure), and
    checking the owner's own member list alone reported both bucket A rows
    broken while the mixin binds perfectly well: Mixin resolves method= against
    the target class's real bytecode, where the override is present. The same
    situation was already noted for *fields* in DESIGN.md §7's P5 acceptance
    note; this is its method-side twin.

    The hierarchy is read from loom's own official-namespace merged jar -- the
    only class graph on disk that is guaranteed to match this row exactly -- and
    translated back with the same class map load_tiny already builds.

    Accepted looseness: a private or package-private member of a superclass is
    not actually inherited, and this walk would pass it. That trade is
    deliberate -- the alternative is the false failure above, on a mixin the
    game loads fine -- and it is bounded, because the descriptor half of
    method= is still matched exactly wherever the name is found.
    """
    cached = _HIERARCHY_CACHE.get((version, owner))
    if cached is not None:
        return cached

    inter_to_official = {v: k for k, v in official_to_inter.items()}
    jar = LOOM_CACHE / version / "minecraft-merged.jar"
    chain, seen, current = [], set(), owner
    while current and current not in seen and jar.exists():
        seen.add(current)
        chain.append(current)
        official = inter_to_official.get(current, current)
        out = subprocess.run(
            [str(JAVAP), "-v", "-cp", str(jar), official.replace("/", ".")],
            capture_output=True, text=True).stdout
        match = SUPER_CLASS.search(out)
        if not match or match.group(1) == "java/lang/Object":
            break
        current = official_to_inter.get(match.group(1), match.group(1))
    if not chain:
        chain = [owner]
    _HIERARCHY_CACHE[(version, owner)] = chain
    return chain


def jar_for(version):
    jar = ROOT / "mc" / version / "build/libs" / f"socialcues-fabric-{version}-0.1.0.jar"
    if not jar.exists():
        raise SystemExit(f"{version}: {jar.relative_to(ROOT)} not built yet -- ./gradlew :mc:{version}:build")
    return jar


def check(version, row):
    """Returns (checked, [problem, ...])."""
    jar = jar_for(version)
    owners, official_to_inter = load_tiny(version)
    pkg = f"dev/zsithious/socialcues/adapter/bucket{row['bucket'].lower()}/mixin/"
    problems, checked = [], 0

    with zipfile.ZipFile(jar) as zf:
        names = [n for n in zf.namelist() if n.startswith(pkg) and n.endswith(".class")]
        if not names:
            # Not a failure of this tool's kind -- there is nothing to bind. It
            # is still the single most important thing to say about such a row
            # (SIRADAKI-IS.md: "12 jar derleniyor" is not "12 sürüm çalışıyor"),
            # so the caller prints it loudly and counts it separately.
            return None, []
        for name in names:
            out = subprocess.run(
                [str(JAVAP), "-v", "-p", "-cp", str(jar), name[:-len(".class")].replace("/", ".")],
                capture_output=True, text=True).stdout
            target = MIXIN_TARGET.search(out)
            simple = name[len(pkg):-len(".class")]
            if not target:
                # A mixin with no class target (or a helper/interface) is not
                # something this tool can check; say so rather than pass it.
                if "org.spongepowered.asm.mixin.Mixin(" in out:
                    problems.append(f"{simple}: @Mixin target is not a class literal, cannot verify")
                continue
            owner = target.group(1)
            if owner not in owners:
                problems.append(f"{simple}: @Mixin target {owner} absent from {version} mappings")
                continue
            checked += 1
            # The owner *and* everything it inherits from -- see supertypes().
            chain = supertypes(version, owner, official_to_inter)
            for group in METHOD_VALUES.findall(out):
                for member in STRING_LIT.findall(group):
                    # method= is either a bare name or name+descriptor; the
                    # descriptor half is what makes an overload unambiguous, so
                    # when it is there it gets checked too.
                    bare, _, desc = member.partition("(")
                    desc = "(" + desc if desc else ""
                    known, declared_on = None, None
                    for owner_or_super in chain:
                        found = owners.get(owner_or_super, {}).get(bare)
                        if found is not None:
                            known, declared_on = found, owner_or_super
                            break
                    checked += 1
                    if known is None:
                        problems.append(
                            f"{simple}: {owner}.{bare} absent on {version} "
                            f"(searched {' -> '.join(chain)})")
                    elif desc and desc not in known:
                        # Intermediary descriptors are in the *official*
                        # namespace, as is the jar's; a mismatch here means the
                        # remapper picked a different overload than the source did.
                        where = "" if declared_on == owner else f" (declared on {declared_on})"
                        problems.append(
                            f"{simple}: {owner}.{bare} exists on {version}{where} but not with {desc} "
                            f"(has {', '.join(known)})")
    return checked, problems


def main():
    all_rows = rows()
    wanted = sys.argv[1:] or list(all_rows)
    bad, renderless = False, []
    for version in wanted:
        if version not in all_rows:
            raise SystemExit(f"{version} is not a row in versions.json")
        bucket = all_rows[version]["bucket"]
        checked, problems = check(version, all_rows[version])
        if checked is None:
            renderless.append(version)
            print(f"{version:8s} NO RENDER  (bucket {bucket} ships no mixins)")
        elif problems:
            bad = True
            print(f"{version:8s} FAIL")
            for p in problems:
                print(f"           {p}")
        else:
            print(f"{version:8s} ok         ({checked} targets resolved, bucket {bucket})")
    if renderless:
        print(f"\n{len(renderless)} row(s) build a jar that renders nothing: {', '.join(renderless)}. "
              f"Those jars must not be published (DESIGN.md §14 P7/P8).")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
