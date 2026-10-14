#!/bin/sh
# Renders the app icon from the SVGs in this folder. Needs rsvg-convert (librsvg).
# Run it again after changing an SVG.
set -eu
cd "$(dirname "$0")/.."

# Android: the adaptive icon's foreground at each density (108 dp); the background is a colour resource.
res=androidApp/src/main/res
for density in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
    dir=$res/mipmap-${density%:*}
    mkdir -p "$dir"
    rsvg-convert -w "${density#*:}" -h "${density#*:}" art/android-foreground.svg -o "$dir/ic_launcher_foreground.png"
done
