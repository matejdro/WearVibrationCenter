Vibration Center for Wear
================================

Android (Wear) application for managing notification vibrations.

## Installing

App is not on the Play Store anymore, due to Google's constant annoying policies and takedowns without a way to get a good explanation.

You can install the app by manually sideloading the apk from releases to your phone and to your watch. To make watch install easier, you can use [Wear Installer](https://www.xda-developers.com/wear-installer-sideload-wear-os-apps/).

## Building

1. Pull the repo
2. Pull the submodules (`git submodule update --init`)
3. Open the project in the Android Studio and wait for its dependencies to resolve
4. Either comment out [Fabric plugin from gradle.build](https://github.com/matejdro/WearVibrationCenter/blob/eb2a1588d32bf1441d789d65ce94bee10dc80ba9/mobile/build.gradle#L11) or [generate your own Fabric API key](https://docs.fabric.io/android/fabric/settings/api-keys.html)
