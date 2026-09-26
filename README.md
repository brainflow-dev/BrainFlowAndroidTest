# BrainFlow Android Example

A learning-focused Android application that shows how to use the BrainFlow Java API with real Bluetooth hardware. The example connects to Muse 2, Muse S, or Muse S Athena, proves that EEG packets are arriving, reads three-axis acceleration from a second BrainFlow preset, calculates edge-safe EEG powerbands, and releases the native session safely.

The app intentionally shows raw, inspectable data. It does not calculate or display a relaxation score.

## What you can learn here

- Add a local BrainFlow Android AAR to a Gradle project.
- Request Android 12+ Bluetooth permissions at runtime.
- Configure and connect a BLE board with <code>BrainFlowInputParams</code>.
- Keep slow native calls away from the Android main thread.
- Read EEG from <code>DEFAULT_PRESET</code> and motion from <code>AUXILIARY_PRESET</code>.
- Ask BrainFlow for sampling rates and channel rows instead of hard-coding a matrix layout.
- Buffer real EEG context before zero-phase filtering and PSD analysis.
- Stop streaming and release the board on disconnect, errors, and activity destruction.
- Test Java-only buffer logic on the host and BrainFlow native metadata on an Android device.

## Current state

The project builds with Android Gradle Plugin 9.4, Gradle 9.8, Java 17 bytecode, and Android 17 / API 37. The minimum Android version is Android 12 / API 31 because the bundled BrainFlow native libraries use API 31.

The bundled <code>app/libs/brainflow-android.aar</code> contains:

- BrainFlow 5.23.0 from upstream tag <code>5.23.0</code>, commit <code>7994b54b4ee23898456d96446dc7e51ebfa18840</code>.
- JNA 5.19.1, kept as a matched Java/native set.
- <code>arm64-v8a</code>, <code>armeabi-v7a</code>, <code>x86</code>, and <code>x86_64</code>.
- 20 native libraries whose ELF load segments are all aligned to at least 16 KB.
- SHA-256 <code>4C2E1B6B393CD7E77EF6915504CE97F881585B552300C1D6EAF0420FA58402AD</code>.

The all-ABI AAR makes the debug APK large. That is useful for a portable example and emulator work; a production app would normally publish ABI-specific app bundles or splits.

This revision was live-validated on 2026-09-26 with a Samsung Galaxy S23 Ultra running Android 15 and a Muse S Athena. At a captured 114-second checkpoint, the app had received 29,392 EEG samples at 256 Hz, was receiving acceleration at 52 Hz, had produced all five powerband traces, and then stopped the stream and released the session cleanly.

## App data flow

    MainActivity UI
        |
        | one serialized worker thread
        v
    BoardShim.prepare_session() -> start_stream()
        |
        +-- DEFAULT_PRESET ----> EEG evidence ----> rolling 16 s window
        |                                          |
        |                                          +-> filter 16 s
        |                                              discard 3 s per edge
        |                                              analyze central 10 s
        |                                              plot five powerbands
        |
        +-- AUXILIARY_PRESET --> newest X/Y/Z sample -> magnitude and counters
        |
        +-- disconnect/error --> stop_stream() -> release_session()

For the included Muse profiles, EEG is 256 Hz and acceleration is 52 Hz. The app still queries those values and the relevant row indexes through <code>BoardShim</code> because preset layouts are board-specific.

## Prerequisites

- Android Studio with Android SDK Platform 37 and Build Tools 37.0.0.
- JDK 17 or newer. The checked-in Gradle wrapper was verified with JDK 21.
- A physical Android 12+ device with Bluetooth LE for a real Muse test.
- One powered Muse 2, Muse S, or Muse S Athena.

The repository already includes the AAR, so you do not need CMake, Maven, Ninja, or the Android NDK just to build this app.

## Build the example

Clone the repository and run:

    git clone https://github.com/brainflow-dev/BrainFlowAndroidTest.git
    cd BrainFlowAndroidTest

On Windows PowerShell:

    ./gradlew.bat testDebugUnitTest lintDebug assembleDebug

On macOS or Linux:

    ./gradlew testDebugUnitTest lintDebug assembleDebug

The debug APK is written to:

    app/build/outputs/apk/debug/app-debug.apk

You can also open the repository in Android Studio, wait for Gradle sync, choose a physical device, and run the <code>app</code> configuration.

## Run it with a Muse

1. Close the official Muse app and any other application that may already own the headset connection.
2. Power on one Muse and keep it near the Android phone.
3. Install and open **BrainFlow Android Example**.
4. Grant the **Nearby devices** permission.
5. Select the exact Muse model.
6. Optionally enter the exact advertised BLE name, such as <code>MuseS-XXXX</code>. Leaving it empty lets BrainFlow discover a compatible nearby device.
7. Keep the default discovery timeout unless the environment needs a longer scan.
8. Tap **Connect & stream**.
9. Confirm that the status becomes green and EEG sample counts, timestamps, and values continue changing.
10. Move the headset gently and confirm that X, Y, Z, magnitude, timestamp, and auxiliary sample count change. A stationary magnitude should usually be near 1 g.
11. Stay connected for at least 16 seconds. The five EEG powerband traces then update once per second.
12. Tap **Disconnect** and confirm that the event log reports both stream stop and session release.

A successful <code>prepare_session()</code> is not treated as proof. The app waits for real packets from <code>get_board_data()</code>.

## Connect an Android device over Wi-Fi

Enable **Developer options > Wireless debugging** on the phone. Pair once with the pairing address and code shown by Android:

    adb pair DEVICE_IP:PAIRING_PORT

Then use the separate connection address shown on the Wireless debugging screen:

    adb connect DEVICE_IP:ADB_PORT
    adb devices

Build, install, and launch:

    ./gradlew.bat installDebug
    adb shell am start -n com.example.brainflowplot/.MainActivity

If more than one device is connected, select one for Gradle:

    $env:ANDROID_SERIAL = "DEVICE_SERIAL_OR_IP:PORT"
    ./gradlew.bat connectedDebugAndroidTest

## Add BrainFlow to your own Android project

### 1. Add the AAR

Create <code>app/libs</code>, copy <code>brainflow-android.aar</code> into it, and add the file dependency:

    dependencies {
        implementation files('libs/brainflow-android.aar')
    }

BrainFlow publishes the Android AAR on its GitHub release page. This repository keeps a verified artifact in source control so the example remains immediately buildable.

### 2. Declare BLE requirements

For an Android 12+ BLE-only application:

    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="true" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

Manifest declarations are not enough. Request both dangerous permissions before scanning:

    requestPermissions(
            new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            },
            BLUETOOTH_PERMISSION_REQUEST);

If you lower <code>minSdk</code> below 31, add the legacy Bluetooth/location rules appropriate for those Android versions.

### 3. Create and prepare a board

Choose the BrainFlow board ID that matches the physical device. Muse S Athena is:

    BrainFlowInputParams params = new BrainFlowInputParams();
    params.timeout = 20;
    params.serial_number = "MuseS-XXXX"; // Optional exact BLE name.
    params.other_info = "preset=p21;low_latency=true";

    int boardId = BoardIds.MUSE_S_ATHENA_BOARD.get_code();
    BoardShim board = new BoardShim(boardId, params);
    board.prepare_session();
    board.start_stream(45000);

The sample uses <code>preset=p21</code> for a compact four-channel Muse EEG stream. It adds <code>low_latency=true</code> for Athena.

Run <code>prepare_session()</code>, streaming, reads, and cleanup away from the UI thread. This app serializes every BrainFlow call through one <code>ScheduledExecutorService</code> so connect, polling, analysis, and disconnect cannot overlap.

### 4. Discover channel metadata

Do not assume a row number from another board or preset:

    int eegRate = BoardShim.get_sampling_rate(boardId);
    int[] eegRows = BoardShim.get_eeg_channels(boardId);
    int eegTimestampRow = BoardShim.get_timestamp_channel(boardId);

    BrainFlowPresets motionPreset = BrainFlowPresets.AUXILIARY_PRESET;
    int motionRate = BoardShim.get_sampling_rate(boardId, motionPreset);
    int[] xyzRows = BoardShim.get_accel_channels(boardId, motionPreset);
    int motionTimestampRow =
            BoardShim.get_timestamp_channel(boardId, motionPreset);

Each returned matrix is organized as <code>data[row][sample]</code>. Row indexes belong to that preset's matrix; do not use default-preset row indexes on auxiliary-preset data.

### 5. Poll both presets

    double[][] eeg = board.get_board_data();
    double[][] motion =
            board.get_board_data(BrainFlowPresets.AUXILIARY_PRESET);

<code>get_board_data()</code> drains the samples it returns. This is convenient for one polling consumer, which is how this app works. If multiple components need the same buffered samples, coordinate one reader or use <code>get_current_board_data()</code>, which copies current samples without removing them.

Always validate matrix dimensions before indexing. The example's <code>AccelerometerSample.latest()</code> shows a small, testable way to select the newest X/Y/Z sample and timestamp.

### 6. Clean up every exit path

    try {
        board.stop_stream();
    } finally {
        board.release_session();
    }

Track whether streaming actually started before calling <code>stop_stream()</code>. Release a partially prepared board after connection errors as well. See <code>MainActivity.cleanupBoard()</code> for the complete pattern used here.

## EEG analysis in this sample

The chart is an example of BrainFlow signal processing, not a medical interpretation.

1. Retain the newest 16 seconds of real EEG.
2. Apply constant detrending.
3. Apply zero-phase 48-52 Hz and 58-62 Hz band-stop filters.
4. Apply a zero-phase 2-45 Hz band-pass filter.
5. Discard three seconds from both filtered edges.
6. Calculate average delta, theta, alpha, beta, and gamma power on the untouched central ten seconds.
7. Plot <code>10 * log10(power)</code> once per second.

The two real-data guards keep finite-window filter transients out of the PSD input. They intentionally add three seconds of right-side analysis latency.

## Project map

| File | Teaching purpose |
| --- | --- |
| <code>MainActivity.java</code> | Permissions, board configuration, serialized lifecycle, preset polling, UI state, and cleanup |
| <code>AccelerometerSample.java</code> | Safe extraction of the newest three-axis sample |
| <code>RollingBoardBuffer.java</code> | Fixed-capacity chronological EEG buffer |
| <code>EdgeSafeEegWindow.java</code> | Filter a guarded window and trim both transient edges |
| <code>BrainFlowBandPowerAnalyzer.java</code> | BrainFlow filters, PSD preparation, and five-band calculation |
| <code>BandPowerPlotView.java</code> | Small dependency-free Android Canvas plot |
| <code>app/src/test</code> | Host-side buffer/window/accelerometer tests |
| <code>app/src/androidTest</code> | Native BrainFlow metadata and filter-transient tests |

## Tests

Run the host tests, lint, and APK build:

    ./gradlew.bat testDebugUnitTest lintDebug assembleDebug

Run instrumentation tests on a connected Android device:

    ./gradlew.bat connectedDebugAndroidTest

The instrumentation suite does not connect to a headset. It loads the real bundled AAR, verifies that all three Muse profiles expose X/Y/Z at 52 Hz through <code>AUXILIARY_PRESET</code>, and exercises BrainFlow's native filters on a deterministic signal.

The actual BLE workflow still requires a physical headset and a physical Android device.

## Use a different BrainFlow board

BrainFlow supports many boards, but this launcher intentionally exposes only the three Muse profiles that were hardware-tested. To adapt it:

1. Add the desired <code>BoardIds</code> value and its user-facing name.
2. Populate the required <code>BrainFlowInputParams</code> fields for that transport, such as serial port, MAC address, IP address, or serial number.
3. Query supported presets, sampling rate, EEG rows, accelerometer rows, and timestamp rows from <code>BoardShim</code>.
4. Hide or replace UI sections for data the board does not expose.
5. Test cleanup after successful connection, timeout, cancellation, and mid-stream errors.

The official supported-board table is the source of truth for per-board parameters and platform availability.

## Replace or rebuild the AAR

The simplest option is to download <code>brainflow-android.aar</code> from a BrainFlow release and replace <code>app/libs/brainflow-android.aar</code>.

For BrainFlow development, install its documented CMake, Ninja, Maven, Android SDK, and NDK prerequisites, then run from the BrainFlow repository:

    python tools/build_android_aar.py --build-native --abis arm64-v8a --allow-missing-abis

To build every ABI, pass:

    --abis arm64-v8a armeabi-v7a x86 x86_64

After replacing any native dependency, rerun Android lint. Modern Android packages should have 16 KB-compatible native libraries; suppressing <code>Aligned16KB</code> does not fix an incompatible binary.

## Troubleshooting

- **The Muse is not found:** verify the selected model, turn off other Muse clients, move the headset closer, and try its exact BLE name.
- **Android denies scanning:** grant Nearby devices. Some vendor builds also require the system Location switch to be on even when an app does not request location data.
- **Connected but no EEG arrives:** wait for the ten-second warning, check headset power/contact, and verify that the selected profile matches the hardware. A BLE connection alone is not a successful data test.
- **EEG works but motion does not:** query <code>get_accel_channels(boardId, AUXILIARY_PRESET)</code>; preset support is board-specific.
- **Imports are red after copying the AAR:** sync Gradle. If needed, invalidate Android Studio caches and restart.
- **<code>UnsatisfiedLinkError</code>:** check Android API level, ABI packaging, and whether the AAR was copied intact.
- **The APK is unexpectedly large:** the example packages four ABIs and BrainFlow's full native stack. Use Android App Bundles or ABI splits for production distribution.
- **Counts look lower than the sampling rate:** UI refresh cadence is not acquisition cadence. The app drains batches four times per second while BrainFlow samples independently.

## Upstream references

- [BrainFlow Android installation and native build guide](https://brainflow.readthedocs.io/en/stable/BuildBrainFlow.html#android)
- [BrainFlow supported boards](https://brainflow.readthedocs.io/en/stable/SupportedBoards.html)
- [BrainFlow presets and data layout](https://brainflow.readthedocs.io/en/stable/DataFormatDesc.html)
- [BrainFlow Java examples](https://brainflow.readthedocs.io/en/stable/Examples.html)
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android 16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes)
- [JNA release notes](https://github.com/java-native-access/jna/blob/master/CHANGES.md)

## Scope

This is a transparent learning example and hardware-validation app, not a production medical or wellness product. It deliberately favors visible metadata, explicit lifecycle handling, and small testable classes over framework abstraction.

## License

MIT
