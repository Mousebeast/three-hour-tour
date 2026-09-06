#!/usr/bin/env bash
# Build a CurseForge-importable client pack zip from pack/manifest.json.
#
# The official CurseForge client CAN download the 12 mods that set
# allowModDistribution:false — that flag only blocks third-party API
# downloads. So importing this zip resolves all 257 mods, which a
# scripted installer cannot do.
#
# Output: pack/build/3ht-<version>.zip  (pack/build/ is gitignored)
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-$(python3 -c "import json;print(json.load(open('pack/manifest.json'))['version'])")}"
OUT="pack/build"
STAGE="$OUT/stage"
ZIP="$OUT/3ht-${VERSION}.zip"

# Which commit produced this zip. Without it, "which build is the server
# running" has no answer at all -- the version string comes from a file anyone
# can edit and says nothing about the code beside it (L6-02).
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    DIRTY=" (dirty)"
else
    DIRTY=""
fi

# `pre` is the standing placeholder in pack/manifest.json and means "not a
# release". Anything else is a version somebody will quote back at you, so it
# has to name a commit that actually exists in history: a released zip built
# from uncommitted edits cannot be reproduced, and its version string is then
# a lie that outlives the working tree that told it.
if [ "$VERSION" != "pre" ]; then
    if [ "$COMMIT" = "unknown" ]; then
        echo "refusing to build $VERSION outside a git checkout" >&2
        exit 1
    fi
    if [ -n "$DIRTY" ]; then
        echo "refusing to build $VERSION from a dirty tree -- commit first:" >&2
        git status --short >&2
        exit 1
    fi
fi

BUILT_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

rm -rf "$STAGE" && mkdir -p "$STAGE"

# manifest, with author/version filled in for the client's display
python3 - "$VERSION" <<'PY'
import json, sys
m = json.load(open('pack/manifest.json'))
# What a human sees in the CurseForge app, the launcher and the instance
# folder. '3ht' is the filesystem spelling and reads like a typo to anyone who
# has not been in this repo; 'threehourtour' is the frozen mod id. Three
# spellings, one job each (owner, 2026-09-02).
m['name'] = 'Three Hour Tour'
m['version'] = sys.argv[1]
if not m.get('author'):
    m['author'] = 'Mousebeast'
json.dump(m, open('pack/build/stage/manifest.json', 'w'), indent=2)
print('manifest: %d files, mc %s, %s'
      % (len(m['files']), m['minecraft']['version'],
         m['minecraft']['modLoaders'][0]['id']))
PY

# overrides — drop the .gitkeep placeholders, they serve git not the client
cp -r pack/overrides "$STAGE/overrides"
find "$STAGE/overrides" -name '.gitkeep' -delete
find "$STAGE/overrides" -type d -empty -delete

# The companion mod. It is not a CurseForge file, so it has no manifest entry
# and the client cannot fetch it -- it has to ride in overrides/mods or the
# imported pack boots without a ship core, a sea scoop or a research terminal.
# Until 2026-09-02 this step did not exist and the jar was copied in by hand,
# which worked for exactly one person.
# A stale jar would be the worse of the two failures -- it produces a pack that
# boots, runs, and is a release behind, with nothing anywhere saying so. So the
# jar is not merely required, it is rebuilt: gradle is the only thing that knows
# whether it is current. Comparing the jar's mtime against companion/src looks
# like the cheaper check and is wrong, because gradle is content-hashed --
# touching a file with no edit leaves the jar legitimately current and older
# than its own source, and that check refuses to build a correct release.
#
# **Clear the old jars first, and refuse to guess between two.** Gradle writes
# a jar named from `mod_version` and never deletes the one the previous version
# left behind, so a version bump leaves both in build/libs -- and `head -1`
# picked the alphabetically first, which is the OLD one. Bumping to 1.0.0 would
# have shipped `threehourtour-0.1.0.jar` inside the first public release, from a
# repo that looked entirely correct. Same family as the zip that was appended to
# rather than rebuilt (2026-09-05): an output directory that only ever
# accumulates, read by something that assumes it holds one answer.
rm -f companion/build/libs/threehourtour-*.jar
( cd companion && ./gradlew build -q )
mapfile -t COMPANION_JARS < <(ls -1 companion/build/libs/threehourtour-*.jar 2>/dev/null || true)
if [ "${#COMPANION_JARS[@]}" -eq 0 ]; then
    echo "gradle built no jar into companion/build/libs/" >&2
    exit 1
fi
if [ "${#COMPANION_JARS[@]}" -gt 1 ]; then
    echo "companion/build/libs/ holds ${#COMPANION_JARS[@]} companion jars and this" >&2
    echo "script must not guess which one the release ships:" >&2
    printf '  %s\n' "${COMPANION_JARS[@]}" >&2
    exit 1
fi
COMPANION="${COMPANION_JARS[0]}"
mkdir -p "$STAGE/overrides/mods"
cp "$COMPANION" "$STAGE/overrides/mods/"
echo "companion: $(basename "$COMPANION") (rebuilt)"

# The stamp, in the instance root where an operator will actually find it.
# Inside overrides rather than in manifest.json: the CurseForge client parses
# that file and unknown keys are its business, not ours.
cat > "$STAGE/overrides/3ht-build.txt" <<STAMP
Three Hour Tour
version   $VERSION
commit    $COMMIT$DIRTY
built     $BUILT_AT
minecraft 1.21.1, $(python3 -c "import json;print(json.load(open('pack/manifest.json'))['minecraft']['modLoaders'][0]['id'])")

This file names the commit this pack was built from. It is written by
tools/build-clientpack.sh and is the only thing tying a running instance back
to the code that produced it.
STAMP

# modlist.html for the client's pack page
PACK_VERSION="$VERSION" PACK_COMMIT="$COMMIT$DIRTY" PACK_BUILT_AT="$BUILT_AT" \
python3 - <<'PY'
import json, urllib.request, html, os
key = open(os.path.expanduser('~/.config/curseforge/api.key')).read().strip()
m = json.load(open('pack/build/stage/manifest.json'))
ids = [f['projectID'] for f in m['files']]
req = urllib.request.Request(
    'https://api.curseforge.com/v1/mods',
    data=json.dumps({'modIds': ids}).encode(),
    headers={'x-api-key': key, 'Content-Type': 'application/json',
             'Accept': 'application/json'})
data = json.load(urllib.request.urlopen(req, timeout=60))['data']
rows = sorted(((d['name'], d.get('links', {}).get('websiteUrl', ''),
                ', '.join(a['name'] for a in d.get('authors', [])[:2]))
               for d in data), key=lambda r: r[0].lower())
out = ['<ul>']
for name, url, auth in rows:
    out.append('<li><a href="%s">%s</a> (by %s)</li>'
               % (html.escape(url), html.escape(name), html.escape(auth)))
out.append('</ul>')
out.append('<hr><p><small>Three Hour Tour %s &mdash; commit %s, built %s</small></p>'
           % (html.escape(os.environ['PACK_VERSION']),
              html.escape(os.environ['PACK_COMMIT']),
              html.escape(os.environ['PACK_BUILT_AT'])))
open('pack/build/stage/modlist.html', 'w').write('\n'.join(out))
print('modlist.html: %d entries' % len(rows))
PY

# **Delete the old zip first.** `zip -r` UPDATES an existing archive: it adds and
# replaces entries and never removes one that is no longer there. So every zip
# built here accumulated every file the pack had ever shipped, and kept handing
# them to players.
#
# It was found on 2026-09-05 in the worst way. The History Stages files were
# flattened on 2026-09-04 -- `global/hull/seaworthy.json` became
# `global/hull_seaworthy.json`, because the mod registers a stage by its filename
# and throws the path away. The nested files left the repo, and stayed in the
# zip. A player who deleted their instance and re-imported from this zip got
# both layouts: 150 stages registered where 75 exist, every one of them twice
# under the same id, and every locked item naming its stage twice in its
# tooltip. Nothing in the deploy could see it, because everything in the deploy
# looks at the repo and at the server, and this was neither.
rm -f "$ZIP"
( cd "$STAGE" && zip -qr "../$(basename "$ZIP")" manifest.json overrides modlist.html )

# And prove it: the archive must contain exactly what was staged, no more. This
# is the assertion the deletion above makes true, kept because "we remembered to
# delete it" is not a check.
python3 - "$STAGE" "$ZIP" <<'CHECK'
import os, sys, zipfile
stage, archive = sys.argv[1], sys.argv[2]
on_disk = set()
for root, _dirs, files in os.walk(stage):
    for name in files:
        on_disk.add(os.path.relpath(os.path.join(root, name), stage))
in_zip = {n for n in zipfile.ZipFile(archive).namelist() if not n.endswith("/")}
extra = sorted(in_zip - on_disk)
missing = sorted(on_disk - in_zip)
if extra or missing:
    if extra:
        print("IN THE ZIP BUT NOT STAGED -- the archive was appended to, not "
              "rebuilt, and these are files the pack no longer ships:",
              file=sys.stderr)
        for name in extra[:20]:
            print(f"  {name}", file=sys.stderr)
        if len(extra) > 20:
            print(f"  ... and {len(extra) - 20} more", file=sys.stderr)
    for name in missing[:20]:
        print(f"STAGED BUT NOT IN THE ZIP: {name}", file=sys.stderr)
    sys.exit(1)
print(f"zip contents: {len(in_zip)} files, exactly what was staged")
CHECK

rm -rf "$STAGE"

echo "built: $ZIP  ($(du -h "$ZIP" | cut -f1))  commit $COMMIT$DIRTY"
unzip -l "$ZIP" | tail -5
