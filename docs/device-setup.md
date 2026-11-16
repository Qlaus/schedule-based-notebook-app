# Installing on the device

## Huawei MatePad Pro (HarmonyOS 4.2)

1. *Settings → About tablet* → tap **Software version** 7 times (enter PIN) → developer mode on.
2. *Settings → System & updates → Developer options* → enable **USB debugging**
   (optionally *Allow ADB debugging in charge-only mode*).
3. If installs are blocked: *Settings → System & updates → Pure mode* → turn off
   (and disable *Monitor apps installed via ADB* in Developer options if present).
4. On the PC install Android **platform-tools** (adb) from developer.android.com/tools/releases/platform-tools.
5. Connect USB, choose *Transfer files*, accept the "Allow USB debugging?" prompt. `adb devices` must list the tablet.
6. GitHub → repo → *Actions* → latest **Android build** run → *Artifacts* → `calnotes-debug-apk` → unzip.
7. `adb install -r app-debug.apk`
8. Background work: *Settings → Battery → App launch* → Not Takvimi → **Manage manually**, enable
   auto-launch, secondary launch and run in background.

## Emulator (no GMS)

Android Studio → *Device Manager* → *Create device* → **Pixel Tablet** → system image API 34
**"Android Open Source Project"** (not "Google APIs"/"Google Play") → finish. Drag the APK onto the
running emulator, or `adb install -r app-debug.apk`. Install Firefox or Chrome APK in the emulator if
no browser is present (needed for sign-in).
