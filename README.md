Vibration Center for Wear
================================

Android (Wear) application for managing notification vibrations.

Play store entry: https://play.google.com/store/apps/details?id=com.matejdro.wearvibrationcenter    
Discussion forums: https://plus.google.com/communities/107578448791725969947

## Building

1. Pull the repo
2. Pull the submodules (`git submodule update --init`)
3. Open the project in the Android Studio and wait for its dependencies to resolve
4. Either comment out [Fabric plugin from gradle.build](https://github.com/matejdro/WearVibrationCenter/blob/eb2a1588d32bf1441d789d65ce94bee10dc80ba9/mobile/build.gradle#L11) or [generate your own Fabric API key](https://docs.fabric.io/android/fabric/settings/api-keys.html)