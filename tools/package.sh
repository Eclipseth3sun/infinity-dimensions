#!/usr/bin/env bash
# Builds the distributable zip: the mod jar + README + LICENSE + INSTALL.txt.
# Run after `./gradlew build`.   Output: dist/EndlessPortals-<version>.zip  (+ the bare jar)
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" gradle.properties | head -n1 | cut -d= -f2-; }
VERSION="$(prop mod_version)"
MC="$(prop minecraft_version)"
SAFE="${VERSION//+/-}"
PKG="EndlessPortals-${SAFE}"

JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | head -n1)"
[ -n "${JAR}" ] || { echo "No mod jar in build/libs - run ./gradlew build first." >&2; exit 1; }

rm -rf dist && mkdir -p "dist/${PKG}"
cp "${JAR}" "dist/${PKG}/"
cp README.md "dist/${PKG}/"
for f in LICENSE LICENSE.md LICENSE.txt; do [ -f "$f" ] && cp "$f" "dist/${PKG}/" || true; done

cat > "dist/${PKG}/INSTALL.txt" <<TXT
Endless Portals ${VERSION}
==========================
Requires: Minecraft ${MC}, Java 25+, Fabric Loader 0.20.1+, Fabric API (for ${MC}).

1. Install Fabric Loader for Minecraft ${MC}:  https://fabricmc.net/use/
2. Put Fabric API in your mods folder:         https://modrinth.com/mod/fabric-api
3. Put $(basename "${JAR}") in your mods folder (this zip's jar - not the README).
4. Launch the game. The mod is needed on BOTH client and server.

Read README.md for the full guide: recipes, named dimensions, troubleshooting.
TXT

( cd dist && zip -qr "${PKG}.zip" "${PKG}" )
cp "${JAR}" dist/
echo "Created dist/${PKG}.zip"
unzip -l "dist/${PKG}.zip"
