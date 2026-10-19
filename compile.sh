#!/bin/bash
set -e
cd "$(dirname "$0")"
ROOT="${STARSECTOR:-$HOME/Documents/starsector}"
CP="$ROOT/starfarer.api.jar:$ROOT/lwjgl.jar:$ROOT/lwjgl_util.jar:$ROOT/json.jar:$ROOT/log4j-1.2.9.jar:$ROOT/mods/LazyLib/jars/LazyLib.jar:$ROOT/mods/MagicLib/jars/MagicLib.jar:$ROOT/mods/Nexerelin/jars/ExerelinCore.jar"
rm -rf build
mkdir -p build jars
find src -name '*.java' > build/sources.txt
javac --release 17 -encoding UTF-8 -classpath "$CP" -d build @build/sources.txt
jar cf build/SovereignCommand.jar -C build sovereign
mv build/SovereignCommand.jar jars/SovereignCommand.jar
rm -rf build
