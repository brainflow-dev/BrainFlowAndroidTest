# BrainFlow Android Muse BLE Test

Android 12+ hardware-validation app for BrainFlow 5.23.0 with Muse 2, Muse S, and Muse S Athena BLE streaming, edge-safe brainwave powerbands, and a smoothed relaxation metric.

<p align="center">
    <img width="400" height="160" src="https://live.staticflickr.com/65535/49908747533_f359f83610_w.jpg">
</p>

**Disclaimer: this code is a focused hardware smoke test and does not pretend to be production quality.**

## What it verifies

The launcher activity proves that the BrainFlow Android AAR can discover, connect to, and stream EEG from:

* Muse 2 (`MUSE_2_BOARD`)
* Muse S (`MUSE_S_BOARD`)
* Muse S Athena (`MUSE_S_ATHENA_BOARD`)

The app uses `preset=p21` for a small four-channel EEG stream. Athena additionally enables BrainFlow low-latency mode. The selected board, preset, sampling rate, EEG rows, timestamps, sample counts, and latest values remain visible so a successful result is inspectable rather than implied.

This is deliberately narrower than InteraXon's consumer app. The official Android app pairs a Muse and then provides meditation, focus, sleep, audio-biofeedback, and post-session experiences. Its setup guide starts pairing from the Bluetooth control after the headset is powered on. This smoke test keeps that short device-first setup flow, but replaces wellness content with raw BrainFlow transport evidence.

### Brainwave powerbands and relaxation

After sixteen seconds of EEG are available, the app refreshes these results once per second:

* A dark, Muse-style line chart keeps the latest 60 powerband measurements. It plots BrainFlow's delta (2-4 Hz), theta (4-8 Hz), alpha (8-13 Hz), beta (13-30 Hz), and gamma (30-45 Hz) averages as `10 * log10(power)` in dB.
* A 0-100 relaxation score feeds the same five average band powers into `BrainFlowMetrics.RESTFULNESS` with `BrainFlowClassifiers.DEFAULT_CLASSIFIER`. BrainFlow 5.23's native default prediction smoothing is enabled with `other_info={"moving_average":true}`; in 5.23 this is a five-prediction moving average.

BrainFlow recommends at least four seconds for EEG metric calculation, with longer windows preferred; this app analyzes a rolling ten-second center window for a steadier metric. Before calculating band powers, it applies BrainFlow's standard constant detrend, 48-52 Hz and 58-62 Hz zero-phase notches, and 2-45 Hz zero-phase bandpass to sixteen seconds of real EEG. It then discards the three-second filter guard on both ends and sends only the clean center window to the PSD calculation. The real right-side guard adds three seconds of analysis latency but prevents either finite-window filter boundary from reaching the PSD.

The percentage is the BrainFlow default-model output averaged natively over the latest five predictions, not InteraXon's proprietary Muse Calm score, a session average, or a clinical stress measurement. Movement, poor sensor contact, and electrical noise can strongly affect both the chart and score.

The bundled `app/libs/brainflow-android.aar` is built from upstream BrainFlow tag `5.23.0`
(commit `7994b54b4ee23898456d96446dc7e51ebfa18840`) for all four Android ABIs:

```text
arm64-v8a, armeabi-v7a, x86, x86_64
SHA-256: CE27FFFC95E4A2E10551E61CD0E25CC7FFCFE1992B20A989DB8C1E035D87B025
```

The current build was live-validated on a Samsung Galaxy S23 Ultra running Android 15 with a Muse S Athena (`MUSE_S_ATHENA_BOARD`, board ID 67) at 256 Hz using the `p21` preset.

### Build and test

Requirements are Android SDK 33, JDK 11 or newer, and a physical Android 12+ BLE device for hardware or instrumentation validation.

On Windows, build the APK and run the host-side windowing tests and Android lint with:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Run the native classifier and filter-transient tests on one selected USB or wireless-ADB device with:

```powershell
$env:ANDROID_SERIAL = "<device-serial-or-wifi-host:port>"
.\gradlew.bat connectedDebugAndroidTest
```

The instrumentation suite executes against the bundled native AAR. It verifies BrainFlow's default moving-average classifier behavior and checks independently that the real-data guards suppress filter transients at both ends of the analysis window.

### Run the proof

1. Install the debug APK on a physical Android 12 or newer device.
2. Close the official Muse app on nearby phones, power on one Muse, and keep it close to the test phone.
3. Open **BrainFlow Muse BLE Test** and grant Nearby devices permission.
4. Select the exact Muse model. Optionally enter the advertised BLE name to avoid connecting to a different nearby Muse.
5. Tap **Connect & stream**.
6. BLE success is the green-style status text: `BLE stream verified`, accompanied by an increasing sample count, BrainFlow timestamps, and changing EEG values.
7. Remain still for at least sixteen seconds. Confirm that all five powerband lines gain new points once per second and that the relaxation percentage updates with BrainFlow's native default moving average.
8. Tap **Disconnect** and confirm the log reports that the stream stopped, the classifier was released, and the session was released.

`prepare_session()` alone is not considered proof: the application requires actual EEG samples from `get_board_data()`.

If automatic discovery does not find the headset, enter its exact advertised BLE name and retry. On Android variants that gate BLE scanning behind the system Location switch, enable that switch as well; this app requests only Android 12+ Nearby devices permissions and does not request location data.

Reference behavior and API choices were checked against the [official Muse app description](https://choosemuse.com/pages/app), the [Muse 2 starter guide](https://choosemuse.com/pages/starter-guide-muse-2), BrainFlow's [supported-board documentation](https://brainflow.readthedocs.io/en/stable/SupportedBoards.html), and its [Java band-power and EEG-metric examples](https://brainflow.readthedocs.io/en/stable/Examples.html).

## Scope and upstream documentation

This repository's launcher is intentionally scoped to Muse BLE hardware. See BrainFlow's [Android build guide](https://brainflow.readthedocs.io/en/stable/BuildBrainFlow.html#android) and [supported-board documentation](https://brainflow.readthedocs.io/en/stable/SupportedBoards.html) for the broader SDK capabilities.

An Android emulator can exercise non-BLE code with a synthetic board, but it cannot prove real Muse discovery, connection, or EEG delivery. Use a physical Android device for the workflow documented here.

## License

MIT
