package com.example.brainflowplot;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import brainflow.BoardIds;
import brainflow.BoardShim;
import brainflow.BrainFlowInputParams;
import brainflow.BrainFlowPresets;

/**
 * Minimal foreground-only smoke test for Muse BLE support in the BrainFlow Android AAR.
 *
 * A successful proof requires prepare_session(), start_stream(), and at least one EEG packet.
 * All BrainFlow native calls are serialized on one worker so board lifecycle, preset polling,
 * analysis, and disconnect operations cannot overlap.
 */
public class MainActivity extends AppCompatActivity {

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final int STREAM_BUFFER_SIZE = 45000;
    private static final long POLL_INTERVAL_MS = 250L;
    private static final long NO_DATA_WARNING_MS = 10000L;
    private static final int ANALYSIS_WINDOW_SECONDS = 10;
    private static final int FILTER_GUARD_SECONDS = 3;
    private static final long ANALYSIS_INTERVAL_MS = 1000L;
    private static final int STATUS_NEUTRAL_BACKGROUND = 0xFFE8EAF6;
    private static final int STATUS_NEUTRAL_TEXT = 0xFF212121;
    private static final int STATUS_SUCCESS_BACKGROUND = 0xFFE8F5E9;
    private static final int STATUS_SUCCESS_TEXT = 0xFF1B5E20;
    private static final int STATUS_WARNING_BACKGROUND = 0xFFFFF8E1;
    private static final int STATUS_WARNING_TEXT = 0xFF795548;
    private static final int STATUS_ERROR_BACKGROUND = 0xFFFFEBEE;
    private static final int STATUS_ERROR_TEXT = 0xFFB71C1C;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService brainFlowExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean disconnectRequested = new AtomicBoolean(false);

    private Spinner boardSpinner;
    private EditText deviceNameInput;
    private EditText timeoutInput;
    private Button connectButton;
    private Button disconnectButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView accelerometerValuesText;
    private TextView accelerometerMagnitudeText;
    private TextView accelerometerMetaText;
    private BandPowerPlotView bandPowerPlot;
    private TextView bandPowerStatusText;
    private TextView evidenceText;
    private TextView logText;

    // Accessed only by brainFlowExecutor.
    private BoardShim boardShim;
    private boolean streamStarted;
    private DeviceProfile activeProfile;
    private int activeSamplingRate;
    private int[] activeEegChannels = new int[0];
    private int activeTimestampChannel = -1;
    private int activeAccelSamplingRate;
    private int[] activeAccelChannels = new int[0];
    private int activeAccelTimestampChannel = -1;
    private long streamStartedAtMs;
    private long totalSamples;
    private long totalAccelSamples;
    private long lastEegUiUpdateMs;
    private long lastAccelUiUpdateMs;
    private boolean firstPacketSeen;
    private boolean firstAccelPacketSeen;
    private boolean noDataWarningShown;
    private boolean noAccelDataWarningShown;
    private AccelerometerSample latestAccelerometerSample;
    private RollingBoardBuffer analysisBuffer;
    private int analysisWindowSamples;
    private int analysisGuardSamples;
    private int analysisBufferSamples;
    private long lastAnalysisAtMs;
    private boolean bandAnalysisEnabled;
    private boolean firstAnalysisSeen;
    private volatile ScheduledFuture<?> pollingTask;

    private volatile boolean destroyed;
    private boolean connectAfterPermissionGrant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root_scroll));

        boardSpinner = findViewById(R.id.board_spinner);
        deviceNameInput = findViewById(R.id.device_name_input);
        timeoutInput = findViewById(R.id.timeout_input);
        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);
        progressBar = findViewById(R.id.connection_progress);
        statusText = findViewById(R.id.status_text);
        accelerometerValuesText = findViewById(R.id.accelerometer_values_text);
        accelerometerMagnitudeText = findViewById(R.id.accelerometer_magnitude_text);
        accelerometerMetaText = findViewById(R.id.accelerometer_meta_text);
        bandPowerPlot = findViewById(R.id.band_power_plot);
        bandPowerStatusText = findViewById(R.id.band_power_status_text);
        evidenceText = findViewById(R.id.evidence_text);
        logText = findViewById(R.id.log_text);

        ArrayAdapter<DeviceProfile> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                DeviceProfile.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        boardSpinner.setAdapter(adapter);

        timeoutInput.setText(String.valueOf(DEFAULT_TIMEOUT_SECONDS));
        connectButton.setOnClickListener(view -> connectFromUi());
        disconnectButton.setOnClickListener(view -> disconnectFromUi());
        resetLiveDataUi();

        try {
            BoardShim.enable_dev_board_logger();
            appendLog("BrainFlow " + BoardShim.get_version() + " loaded from Android AAR.");
        } catch (Exception error) {
            showIdleError("BrainFlow AAR failed to initialize: " + readableMessage(error));
        }
    }

    private static void applySystemBarInsets(View root) {
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            android.graphics.Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom);
            return windowInsets;
        });
    }

    private void connectFromUi() {
        if (!hasBluetoothPermissions()) {
            connectAfterPermissionGrant = true;
            requestPermissions(
                    new String[] {
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    BLUETOOTH_PERMISSION_REQUEST);
            return;
        }

        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter =
                bluetoothManager == null ? null : bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            showIdleError("This Android device does not provide Bluetooth.");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            showIdleError("Bluetooth is off. Enable Bluetooth, then try again.");
            return;
        }

        DeviceProfile profile = (DeviceProfile) boardSpinner.getSelectedItem();
        if (profile == null) {
            showIdleError("Select a Muse model.");
            return;
        }

        String deviceName = deviceNameInput.getText().toString().trim();
        int timeoutSeconds = parseTimeoutSeconds(timeoutInput.getText().toString());
        timeoutInput.setText(String.valueOf(timeoutSeconds));

        disconnectRequested.set(false);
        setConnectingUi(profile, deviceName, timeoutSeconds);
        brainFlowExecutor.execute(
                () -> connectOnWorker(profile, deviceName, timeoutSeconds));
    }

    private void connectOnWorker(
            DeviceProfile profile, String deviceName, int timeoutSeconds) {
        BoardShim candidate = null;
        boolean candidateStreamStarted = false;
        try {
            BrainFlowInputParams params = new BrainFlowInputParams();
            params.timeout = timeoutSeconds;
            params.other_info = profile.preset;
            if (!TextUtils.isEmpty(deviceName)) {
                params.serial_number = deviceName;
            }

            postLog("Calling prepare_session() for " + profile.displayName
                    + " (board " + profile.boardId + ", " + profile.preset + ").");
            candidate = new BoardShim(profile.boardId, params);
            candidate.prepare_session();
            postLog("prepare_session() succeeded; BrainFlow found and connected to the Muse.");

            if (disconnectRequested.get()) {
                throw new CancellationException("Connection cancelled.");
            }

            candidate.start_stream(STREAM_BUFFER_SIZE);
            candidateStreamStarted = true;
            postLog("start_stream() succeeded; waiting for EEG packets.");

            if (disconnectRequested.get()) {
                throw new CancellationException("Connection cancelled.");
            }

            int samplingRate = BoardShim.get_sampling_rate(profile.boardId);
            int[] eegChannels = BoardShim.get_eeg_channels(profile.boardId);
            int timestampChannel = BoardShim.get_timestamp_channel(profile.boardId);
            int accelSamplingRate = BoardShim.get_sampling_rate(
                    profile.boardId, BrainFlowPresets.AUXILIARY_PRESET);
            int[] accelChannels = BoardShim.get_accel_channels(
                    profile.boardId, BrainFlowPresets.AUXILIARY_PRESET);
            int accelTimestampChannel = BoardShim.get_timestamp_channel(
                    profile.boardId, BrainFlowPresets.AUXILIARY_PRESET);
            if (accelChannels.length < 3) {
                throw new IllegalStateException(
                        "Muse auxiliary preset exposes fewer than three accelerometer axes");
            }

            boardShim = candidate;
            streamStarted = true;
            activeProfile = profile;
            activeSamplingRate = samplingRate;
            activeEegChannels = eegChannels;
            activeTimestampChannel = timestampChannel;
            activeAccelSamplingRate = accelSamplingRate;
            activeAccelChannels = accelChannels;
            activeAccelTimestampChannel = accelTimestampChannel;
            streamStartedAtMs = System.currentTimeMillis();
            totalSamples = 0;
            totalAccelSamples = 0;
            lastEegUiUpdateMs = 0;
            lastAccelUiUpdateMs = 0;
            firstPacketSeen = false;
            firstAccelPacketSeen = false;
            noDataWarningShown = false;
            noAccelDataWarningShown = false;
            latestAccelerometerSample = null;
            analysisBuffer = null;
            analysisWindowSamples = samplingRate * ANALYSIS_WINDOW_SECONDS;
            analysisGuardSamples = samplingRate * FILTER_GUARD_SECONDS;
            analysisBufferSamples = EdgeSafeEegWindow.requiredSamples(
                    analysisWindowSamples, analysisGuardSamples);
            lastAnalysisAtMs = 0;
            bandAnalysisEnabled = true;
            firstAnalysisSeen = false;

            candidate = null; // Ownership moved to boardShim.
            candidateStreamStarted = false;

            if (disconnectRequested.get()) {
                throw new CancellationException("Connection cancelled.");
            }

            postLog("Muse accelerometer available from AUXILIARY_PRESET at "
                    + accelSamplingRate + " Hz on rows " + Arrays.toString(accelChannels) + ".");
            postUi(() -> showStreamingUi(
                    profile,
                    deviceName,
                    samplingRate,
                    eegChannels,
                    accelSamplingRate,
                    accelChannels));
            pollingTask = brainFlowExecutor.scheduleWithFixedDelay(
                    this::pollDataOnWorker,
                    POLL_INTERVAL_MS,
                    POLL_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
            if (disconnectRequested.get()) {
                cancelPolling();
            }
        } catch (CancellationException cancelled) {
            cleanupFailedConnection(candidate, candidateStreamStarted);
            postLog("Connection cancelled.");
            postUi(() -> showDisconnectedUi("Disconnected."));
        } catch (Exception error) {
            cleanupFailedConnection(candidate, candidateStreamStarted);
            String message = readableMessage(error);
            postLog("Connection failed: " + message);
            postUi(() -> showIdleError("BLE connection failed: " + message));
        }
    }

    private void pollDataOnWorker() {
        BoardShim activeBoard = boardShim;
        if (activeBoard == null || !streamStarted || disconnectRequested.get()) {
            return;
        }

        try {
            double[][] eegData = activeBoard.get_board_data();
            double[][] accelData =
                    activeBoard.get_board_data(BrainFlowPresets.AUXILIARY_PRESET);
            long now = System.currentTimeMillis();
            int eegBatchSamples = sampleCount(eegData);
            int accelBatchSamples = sampleCount(accelData);

            if (accelBatchSamples > 0) {
                processAccelerometerDataOnWorker(accelData, accelBatchSamples, now);
            } else if (!firstAccelPacketSeen
                    && !noAccelDataWarningShown
                    && now - streamStartedAtMs >= NO_DATA_WARNING_MS) {
                noAccelDataWarningShown = true;
                postLog("No accelerometer packets arrived during the first 10 seconds.");
                postUi(() -> accelerometerMetaText.setText(
                        R.string.accelerometer_no_data));
            }

            if (eegBatchSamples > 0) {
                totalSamples += eegBatchSamples;
                if (analysisBuffer == null) {
                    analysisBuffer =
                            new RollingBoardBuffer(eegData.length, analysisBufferSamples);
                }
                analysisBuffer.append(eegData);
                int analysisSamples = analysisBuffer.size();
                if (!firstPacketSeen) {
                    firstPacketSeen = true;
                    postLog("First EEG packet received. BrainFlow Muse BLE streaming is verified.");
                }

                if (bandAnalysisEnabled
                        && analysisSamples >= analysisBufferSamples
                        && now - lastAnalysisAtMs >= ANALYSIS_INTERVAL_MS) {
                    lastAnalysisAtMs = now;
                    analyzeEegOnWorker();
                }

                if (now - lastEegUiUpdateMs >= 500L) {
                    lastEegUiUpdateMs = now;
                    String latestValues =
                            formatLatestEegValues(eegData, eegBatchSamples - 1);
                    String latestTimestamp =
                            formatLatestTimestamp(eegData, eegBatchSamples - 1);
                    long elapsedMs = now - streamStartedAtMs;
                    long observedSamples = totalSamples;
                    long observedAccelSamples = totalAccelSamples;
                    AccelerometerSample accelSample = latestAccelerometerSample;
                    postUi(() -> {
                        showVerifiedData(
                                eegBatchSamples,
                                observedSamples,
                                elapsedMs,
                                latestTimestamp,
                                latestValues,
                                observedAccelSamples,
                                accelSample);
                        if (analysisSamples < analysisBufferSamples) {
                            showBandPowerWarmup(
                                    analysisSamples,
                                    analysisBufferSamples,
                                    activeSamplingRate);
                        }
                    });
                }
            } else if (!firstPacketSeen
                    && !noDataWarningShown
                    && now - streamStartedAtMs >= NO_DATA_WARNING_MS) {
                noDataWarningShown = true;
                postLog("No EEG packets arrived during the first 10 seconds.");
                postUi(() -> setStatus(
                        firstAccelPacketSeen
                                ? R.string.status_connected_accel_only
                                : R.string.status_connected_no_data,
                        STATUS_WARNING_BACKGROUND,
                        STATUS_WARNING_TEXT));
            }
        } catch (Exception error) {
            cancelPolling();
            String message = readableMessage(error);
            postLog("Streaming failed: " + message);
            cleanupActiveBoard();
            postUi(() -> showIdleError("BLE stream failed: " + message));
        }
    }

    private void processAccelerometerDataOnWorker(
            double[][] data, int batchSamples, long now) {
        AccelerometerSample sample = AccelerometerSample.latest(
                data, activeAccelChannels, activeAccelTimestampChannel);
        latestAccelerometerSample = sample;
        totalAccelSamples += batchSamples;

        if (!firstAccelPacketSeen) {
            firstAccelPacketSeen = true;
            postLog("First Muse accelerometer packet received from AUXILIARY_PRESET.");
            if (noDataWarningShown && !firstPacketSeen) {
                postUi(() -> setStatus(
                        R.string.status_connected_accel_only,
                        STATUS_WARNING_BACKGROUND,
                        STATUS_WARNING_TEXT));
            }
        }

        if (now - lastAccelUiUpdateMs >= 250L) {
            lastAccelUiUpdateMs = now;
            long observedSamples = totalAccelSamples;
            postUi(() -> showAccelerometerData(sample, observedSamples));
        }
    }

    private void analyzeEegOnWorker() {
        RollingBoardBuffer buffer = analysisBuffer;
        if (buffer == null || buffer.size() < analysisBufferSamples) {
            return;
        }

        final double[] averageBandPowers;
        try {
            double[] averages = BrainFlowBandPowerAnalyzer.calculateAverageBandPowers(
                    buffer.snapshot(),
                    activeEegChannels,
                    activeSamplingRate,
                    analysisGuardSamples,
                    analysisWindowSamples);
            if (averages == null || averages.length < 5) {
                throw new IllegalStateException("BrainFlow returned fewer than five bands");
            }
            averageBandPowers = Arrays.copyOf(averages, 5);
            for (double value : averageBandPowers) {
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalStateException("BrainFlow returned invalid band power");
                }
            }
        } catch (Exception error) {
            bandAnalysisEnabled = false;
            String message = readableMessage(error);
            postLog("Band-power analysis disabled: " + message);
            postUi(() -> showBandAnalysisUnavailable(message));
            return;
        }

        if (!firstAnalysisSeen) {
            firstAnalysisSeen = true;
            postLog("First edge-safe ten-second BrainFlow band-power window analyzed after "
                    + "discarding three-second filter guards on both ends.");
        }

        postUi(() -> showAnalysisResult(averageBandPowers));
    }

    private void disconnectFromUi() {
        disconnectRequested.set(true);
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        setStatus(
                R.string.status_disconnecting,
                STATUS_NEUTRAL_BACKGROUND,
                STATUS_NEUTRAL_TEXT);
        appendLog("Disconnect requested.");
        cancelPolling();

        brainFlowExecutor.execute(() -> {
            cleanupActiveBoard();
            postLog("BrainFlow stream stopped and session released.");
            postUi(() -> showDisconnectedUi("Disconnected cleanly."));
        });
    }

    private void cleanupActiveBoard() {
        cancelPolling();
        BoardShim activeBoard = boardShim;
        boolean activeStreamStarted = streamStarted;
        boardShim = null;
        streamStarted = false;
        activeProfile = null;
        activeSamplingRate = 0;
        activeEegChannels = new int[0];
        activeTimestampChannel = -1;
        activeAccelSamplingRate = 0;
        activeAccelChannels = new int[0];
        activeAccelTimestampChannel = -1;
        totalAccelSamples = 0;
        firstAccelPacketSeen = false;
        noAccelDataWarningShown = false;
        latestAccelerometerSample = null;
        analysisBuffer = null;
        analysisWindowSamples = 0;
        analysisGuardSamples = 0;
        analysisBufferSamples = 0;
        lastAnalysisAtMs = 0;
        bandAnalysisEnabled = false;
        firstAnalysisSeen = false;
        cleanupBoard(activeBoard, activeStreamStarted);
    }

    private void cleanupFailedConnection(BoardShim candidate, boolean candidateStreamStarted) {
        if (candidate != null) {
            cleanupBoard(candidate, candidateStreamStarted);
        } else {
            // Ownership has already moved to boardShim. This also cancels a polling task that may
            // have been created after the UI requested a disconnect.
            cleanupActiveBoard();
        }
    }

    private void cleanupBoard(BoardShim board, boolean started) {
        if (board == null) {
            return;
        }
        if (started) {
            try {
                board.stop_stream();
            } catch (Exception error) {
                postLog("stop_stream() warning: " + readableMessage(error));
            }
        }
        try {
            board.release_session();
        } catch (Exception error) {
            postLog("release_session() warning: " + readableMessage(error));
        }
    }

    private int sampleCount(double[][] data) {
        return data == null || data.length == 0 || data[0] == null ? 0 : data[0].length;
    }

    private String formatLatestEegValues(double[][] data, int column) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < activeEegChannels.length; index++) {
            int row = activeEegChannels[index];
            if (row < 0 || row >= data.length || data[row] == null || column >= data[row].length) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append("CH").append(index + 1).append('=')
                    .append(String.format(Locale.US, "%.2f", data[row][column]));
        }
        return builder.length() == 0 ? "unavailable" : builder.toString();
    }

    private String formatLatestTimestamp(double[][] data, int column) {
        if (activeTimestampChannel < 0
                || activeTimestampChannel >= data.length
                || data[activeTimestampChannel] == null
                || column >= data[activeTimestampChannel].length) {
            return "unavailable";
        }
        return String.format(Locale.US, "%.3f", data[activeTimestampChannel][column]);
    }

    private String formatAccelerometerForEvidence(AccelerometerSample sample) {
        if (sample == null) {
            return "waiting for AUXILIARY_PRESET packets";
        }
        return String.format(
                Locale.US,
                "X=%.4f  Y=%.4f  Z=%.4f  |a|=%.4f  timestamp=%.3f",
                sample.xG,
                sample.yG,
                sample.zG,
                sample.magnitudeG(),
                sample.timestamp);
    }

    private void setConnectingUi(DeviceProfile profile, String deviceName, int timeoutSeconds) {
        resetLiveDataUi();
        boardSpinner.setEnabled(false);
        deviceNameInput.setEnabled(false);
        timeoutInput.setEnabled(false);
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        progressBar.setVisibility(View.VISIBLE);
        setStatus(
                R.string.status_connecting,
                STATUS_NEUTRAL_BACKGROUND,
                STATUS_NEUTRAL_TEXT);
        evidenceText.setText(getString(
                R.string.evidence_connecting,
                profile.displayName,
                profile.boardId,
                profile.preset,
                TextUtils.isEmpty(deviceName) ? "automatic discovery" : deviceName,
                timeoutSeconds));
        appendLog("Starting BrainFlow BLE discovery for " + profile.displayName + ".");
    }

    private void showStreamingUi(
            DeviceProfile profile,
            String deviceName,
            int samplingRate,
            int[] eegChannels,
            int accelSamplingRate,
            int[] accelChannels) {
        if (disconnectRequested.get()) {
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        setStatus(
                R.string.status_waiting_for_data,
                STATUS_NEUTRAL_BACKGROUND,
                STATUS_NEUTRAL_TEXT);
        evidenceText.setText(getString(
                R.string.evidence_waiting,
                profile.displayName,
                profile.boardId,
                profile.preset,
                TextUtils.isEmpty(deviceName) ? "automatically discovered" : deviceName,
                samplingRate,
                Arrays.toString(eegChannels),
                accelSamplingRate,
                Arrays.toString(accelChannels)));
    }

    private void showVerifiedData(
            int batchSamples,
            long observedSamples,
            long elapsedMs,
            String timestamp,
            String eegValues,
            long observedAccelSamples,
            AccelerometerSample accelSample) {
        if (disconnectRequested.get()) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        setStatus(
                R.string.status_verified,
                STATUS_SUCCESS_BACKGROUND,
                STATUS_SUCCESS_TEXT);
        long elapsedSeconds = Math.max(1L, elapsedMs / 1000L);
        evidenceText.setText(getString(
                R.string.evidence_verified,
                activeProfile == null ? "Muse" : activeProfile.displayName,
                activeProfile == null ? 0 : activeProfile.boardId,
                activeProfile == null ? "unknown" : activeProfile.preset,
                activeSamplingRate,
                Arrays.toString(activeEegChannels),
                observedSamples,
                batchSamples,
                elapsedSeconds,
                timestamp,
                eegValues,
                activeAccelSamplingRate,
                Arrays.toString(activeAccelChannels),
                observedAccelSamples,
                formatAccelerometerForEvidence(accelSample)));
    }

    private void resetLiveDataUi() {
        bandPowerPlot.reset();
        accelerometerValuesText.setText(R.string.accelerometer_waiting);
        accelerometerMagnitudeText.setText(R.string.accelerometer_magnitude_waiting);
        accelerometerMetaText.setText(R.string.accelerometer_detail);
        bandPowerStatusText.setText(R.string.band_plot_waiting);
        bandPowerStatusText.setTextColor(STATUS_NEUTRAL_TEXT);
    }

    private void showAccelerometerData(AccelerometerSample sample, long observedSamples) {
        if (disconnectRequested.get()) {
            return;
        }
        accelerometerValuesText.setText(getString(
                R.string.accelerometer_values,
                sample.xG,
                sample.yG,
                sample.zG));
        accelerometerMagnitudeText.setText(getString(
                R.string.accelerometer_magnitude,
                sample.magnitudeG()));
        accelerometerMetaText.setText(getString(
                R.string.accelerometer_stream_detail,
                activeAccelSamplingRate,
                Arrays.toString(activeAccelChannels),
                observedSamples,
                sample.timestamp));
    }

    private void showBandPowerWarmup(int samples, int requiredSamples, int samplingRate) {
        if (disconnectRequested.get() || samplingRate <= 0 || requiredSamples <= 0) {
            return;
        }
        double seconds = samples / (double) samplingRate;
        int requiredSeconds = (int) Math.ceil(requiredSamples / (double) samplingRate);
        bandPowerStatusText.setText(getString(
                R.string.band_power_collecting,
                seconds,
                requiredSeconds,
                ANALYSIS_WINDOW_SECONDS,
                FILTER_GUARD_SECONDS));
    }

    private void showAnalysisResult(double[] averageBandPowers) {
        if (disconnectRequested.get()) {
            return;
        }
        bandPowerPlot.addBandPowers(averageBandPowers);
        bandPowerStatusText.setText(R.string.band_power_live);
    }

    private void showBandAnalysisUnavailable(String message) {
        if (disconnectRequested.get()) {
            return;
        }
        bandPowerStatusText.setText(getString(R.string.band_analysis_unavailable, message));
        bandPowerStatusText.setTextColor(STATUS_ERROR_TEXT);
    }

    private void showDisconnectedUi(String message) {
        progressBar.setVisibility(View.GONE);
        setStatus(message, STATUS_NEUTRAL_BACKGROUND, STATUS_NEUTRAL_TEXT);
        setIdleControls();
    }

    private void showIdleError(String message) {
        progressBar.setVisibility(View.GONE);
        setStatus(message, STATUS_ERROR_BACKGROUND, STATUS_ERROR_TEXT);
        evidenceText.setText(R.string.evidence_empty);
        setIdleControls();
        appendLog(message);
    }

    private void setStatus(int textResource, int backgroundColor, int textColor) {
        statusText.setText(textResource);
        statusText.setBackgroundColor(backgroundColor);
        statusText.setTextColor(textColor);
    }

    private void setStatus(String text, int backgroundColor, int textColor) {
        statusText.setText(text);
        statusText.setBackgroundColor(backgroundColor);
        statusText.setTextColor(textColor);
    }

    private void setIdleControls() {
        boardSpinner.setEnabled(true);
        deviceNameInput.setEnabled(true);
        timeoutInput.setEnabled(true);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
    }

    private boolean hasBluetoothPermissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private int parseTimeoutSeconds(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(5, Math.min(120, parsed));
        } catch (Exception ignored) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    private void cancelPolling() {
        ScheduledFuture<?> task = pollingTask;
        pollingTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    private String readableMessage(Throwable error) {
        String message = error.getMessage();
        return TextUtils.isEmpty(message) ? error.getClass().getSimpleName() : message;
    }

    private void postLog(String message) {
        postUi(() -> appendLog(message));
    }

    private void appendLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logText.append(timestamp + "  " + message + "\n");
        if (logText.length() > 12000) {
            logText.setText(logText.getText().subSequence(logText.length() - 8000, logText.length()));
        }
    }

    private void postUi(Runnable action) {
        mainHandler.post(() -> {
            if (!destroyed) {
                action.run();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != BLUETOOTH_PERMISSION_REQUEST) {
            return;
        }
        if (hasBluetoothPermissions()) {
            appendLog("Nearby devices permission granted.");
            if (connectAfterPermissionGrant) {
                connectAfterPermissionGrant = false;
                connectFromUi();
            }
        } else {
            connectAfterPermissionGrant = false;
            showIdleError("Nearby devices permission is required for BrainFlow BLE discovery.");
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        disconnectRequested.set(true);
        cancelPolling();
        brainFlowExecutor.execute(this::cleanupActiveBoard);
        brainFlowExecutor.shutdown();
        super.onDestroy();
    }

    private enum DeviceProfile {
        MUSE_2("Muse 2", BoardIds.MUSE_2_BOARD.get_code(), "preset=p21"),
        MUSE_S("Muse S", BoardIds.MUSE_S_BOARD.get_code(), "preset=p21"),
        MUSE_S_ATHENA(
                "Muse S Athena",
                BoardIds.MUSE_S_ATHENA_BOARD.get_code(),
                "preset=p21;low_latency=true");

        final String displayName;
        final int boardId;
        final String preset;

        DeviceProfile(String displayName, int boardId, String preset) {
            this.displayName = displayName;
            this.boardId = boardId;
            this.preset = preset;
        }

        @NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }
}
