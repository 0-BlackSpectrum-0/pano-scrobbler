#!/bin/bash

scriptDir="$(cd "$(dirname "$0")" && pwd)"
arch=$(uname -m)

if [ "$arch" = "aarch64" ]; then
    resourcesDirName="linux-arm64"
else
    resourcesDirName="linux-x64"
fi
appNameWithoutSpaces="pano-scrobbler"
nativeImageDir="$scriptDir/build/native/$resourcesDirName"
appDir="/tmp/PanoScrobbler.AppDir"
libExecDir="$appDir/usr/libexec/pano-scrobbler"
distDir="$scriptDir/../dist"

# Read version code from version.txt
verCode=$(cat "$scriptDir/../version.txt")
verName="$((verCode / 100)).$((verCode % 100))"

# Strip .so files
strip --strip-unneeded "$nativeImageDir"/*.so
strip --strip-unneeded "$nativeImageDir"/lib/*.so

# Clean AppDir
rm -rf "$appDir"

install -Dm644 -t "${libExecDir}/lib/" "${nativeImageDir}"/lib/*.so
install -Dm644 -t "${libExecDir}/" "${nativeImageDir}"/*.so
install -Dm644 -t "${libExecDir}/icons/hicolor/scalable/apps/" "${nativeImageDir}/icons/hicolor/scalable/apps/"*.svg
install -Dm644 -t "${libExecDir}/icons/hicolor/symbolic/apps/" "${nativeImageDir}/icons/hicolor/symbolic/apps/"*.svg
install -Dm644 -t "${libExecDir}/icons/hicolor/" "${nativeImageDir}/icons/hicolor/index.theme"
install -Dm644 -t "${libExecDir}/" "${nativeImageDir}"/LICENSE
install -Dm644 -t "${libExecDir}/" "${nativeImageDir}"/${appNameWithoutSpaces}.desktop
install -Dm755 -t "${libExecDir}/" "${nativeImageDir}/${appNameWithoutSpaces}"

# Create tarball
tarFile="$distDir/$appNameWithoutSpaces-$resourcesDirName.tar.gz"
tar -czf "$tarFile" -C "$libExecDir" .

# Relauncher script for appimage
echo '#!/bin/bash
APP="$1"
sleep 3
"$APP" &' > "$libExecDir/relaunch.sh"
chmod +x "$libExecDir/relaunch.sh"

# LICENSE
install -d "$appDir/usr/share/licenses/$appNameWithoutSpaces"
mv "$libExecDir/LICENSE" "$appDir/usr/share/licenses/$appNameWithoutSpaces/"

# Icons
mv "$libExecDir/icons" "$appDir/usr/share/"

for f in "$appDir/usr/share/icons/hicolor/symbolic/apps/"*-symbolic.svg; do
  mv -- "$f" "${f%-symbolic.svg}-appimage-symbolic.svg"
done

for f in "$appDir/usr/share/icons/hicolor/scalable/apps/"*.svg; do
  mv -- "$f" "${f%.svg}-appimage.svg"
done

cp "$appDir/usr/share/icons/hicolor/scalable/apps/"*.svg "$appDir/"

# Desktop file
desktopFile="$libExecDir/$appNameWithoutSpaces.desktop"
sed -i -e "s/^Icon=.*/Icon=$appNameWithoutSpaces-appimage/" "$desktopFile"
sed -e "s/^Exec=.*/Exec=AppRun %U/" "$desktopFile" >  "$appDir/$appNameWithoutSpaces.desktop"
install -d "$appDir/usr/share/applications/"
mv "$desktopFile" "$appDir/usr/share/applications/"

# Create AppRun symlink
ln -srf "$libExecDir/$appNameWithoutSpaces" "$appDir/AppRun"

# Download appimagetool if missing
appImageToolFile="$HOME/appimagetool-$arch.AppImage"
if [ ! -f "$appImageToolFile" ]; then
    curl -L -o "$appImageToolFile" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-$arch.AppImage"
    chmod +x "$appImageToolFile"
fi

# Build AppImage
distFile="$distDir/$appNameWithoutSpaces-$resourcesDirName.AppImage"
ARCH=$arch VERSION="$verName" "$appImageToolFile" "$appDir" "$distFile"

# Build Flatpak bundle, if flatpak is available
if ! command -v flatpak &> /dev/null; then
    echo "flatpak command not found, skipping Flatpak creation."
    exit 0
fi

flatpakAppId="org.blackspectrum.scrobbler"
flatpakAppDir="/tmp/pano-scrobbler-flatpak"
flatpakRepoDir="/tmp/pano-scrobbler-flatpak-repo"
flatpakBundleFile="${distDir}/${appNameWithoutSpaces}-${resourcesDirName}.flatpak"

rm -rf "$flatpakAppDir" "$flatpakRepoDir"

# Initialize flatpak build directory using GNOME or Freedesktop runtime
flatpak build-init "$flatpakAppDir" "$flatpakAppId" org.gnome.Sdk org.gnome.Platform 47 2>/dev/null || \
flatpak build-init "$flatpakAppDir" "$flatpakAppId" org.freedesktop.Sdk org.freedesktop.Platform 24.08

mkdir -p "$flatpakAppDir/files/bin" "$flatpakAppDir/files/lib" "$flatpakAppDir/files/share/applications" "$flatpakAppDir/files/share/icons"

install -Dm755 "${nativeImageDir}/${appNameWithoutSpaces}" "$flatpakAppDir/files/bin/${appNameWithoutSpaces}"
install -Dm644 -t "$flatpakAppDir/files/lib/" "${nativeImageDir}"/*.so 2>/dev/null || true
if [ -d "${nativeImageDir}/lib" ]; then
    cp -r "${nativeImageDir}/lib/." "$flatpakAppDir/files/lib/"
fi

# Desktop file & icons
sed "s/^Exec=.*/Exec=${appNameWithoutSpaces} %U/" "${nativeImageDir}/${appNameWithoutSpaces}.desktop" > "$flatpakAppDir/files/share/applications/${flatpakAppId}.desktop"
if [ -d "$appDir/usr/share/icons" ]; then
    cp -r "$appDir/usr/share/icons/." "$flatpakAppDir/files/share/icons/"
elif [ -d "${nativeImageDir}/icons" ]; then
    cp -r "${nativeImageDir}/icons/." "$flatpakAppDir/files/share/icons/"
fi

flatpak build-finish "$flatpakAppDir" \
    --command="${appNameWithoutSpaces}" \
    --share=ipc \
    --socket=fallback-x11 \
    --socket=wayland \
    --share=network \
    --socket=pulseaudio \
    --talk-name=org.freedesktop.Notifications \
    --talk-name=org.mpris.MediaPlayer2.*

flatpak build-export "$flatpakRepoDir" "$flatpakAppDir"
flatpak build-bundle "$flatpakRepoDir" "$flatpakBundleFile" "$flatpakAppId"
echo "Flatpak bundle successfully created: $flatpakBundleFile"