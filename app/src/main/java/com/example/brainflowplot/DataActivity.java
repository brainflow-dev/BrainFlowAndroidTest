package com.example.brainflowplot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import brainflow.BoardShim;
import brainflow.BrainFlowError;
import brainflow.BrainFlowInputParams;

import java.util.Arrays;

public class DataActivity extends AppCompatActivity {

    private static final String TAG = "BrainFlowLog";
    private static final int MUSE_2_BOARD = 38;
    private static final int MUSE_S_BOARD = 39;
    private static final int MUSE_ATHENA_BOARD = 67;

    // to dont sync threads for dataplot, bandpower, psd, etc each thread will has its own data array share boardShim object
    public static BoardShim boardShim = null;
    public static int samplingRate = 0;
    public static int[] channels = new int[0];
    public static boolean isSessionReady = false;
    public static final int[] colors = {Color.BLUE, Color.YELLOW, Color.RED, Color.MAGENTA, Color.GREEN, Color.CYAN, Color.GRAY, Color.BLACK};

    private Thread prepareThread = null;
    private volatile boolean destroyed = false;
    private boolean keepSessionOnDestroy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);
        BottomNavigationView navView = findViewById(R.id.nav_view);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupWithNavController(navView, navController);

        try {
            BoardShim.enable_dev_board_logger();
            Log.i(TAG, "BrainFlow logger enabled, version: " + BoardShim.get_version());
        } catch (Exception e) {
            Log.w(TAG, "Failed to enable BrainFlow logger", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isSessionReady || prepareThread != null) {
            Log.i(TAG, "Skipping prepare_session, existing session state: isSessionReady=" + isSessionReady);
            return;
        }

        SessionConfig config = readSessionConfig();
        Log.i(TAG, "Starting BrainFlow prepare thread for boardId=" + config.boardId);
        prepareThread = new Thread(() -> prepareSession(config), "BrainFlowPrepareSession");
        prepareThread.start();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (!keepSessionOnDestroy && !isChangingConfigurations()) {
            releaseCurrentSession();
            resetSessionState();
        }
        super.onDestroy();
    }

    private SessionConfig readSessionConfig() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int boardId = Integer.valueOf(prefs.getString(getString(R.string.board_id_key), "-1"));
        String ipAddr = prefs.getString(getString(R.string.ip_address_key), "").trim();
        String serialNumber = prefs.getString(getString(R.string.serial_number_key), "").trim();
        String macAddress = prefs.getString(getString(R.string.mac_address_key), "").trim();
        int ipPort = parseIntPreference(prefs, getString(R.string.ip_port_key), 0);
        int timeout = parseIntPreference(prefs, getString(R.string.timeout_key), 0);
        if (isMuseBoard(boardId) && timeout < 1) {
            timeout = 20;
        }
        return new SessionConfig(boardId, ipAddr, serialNumber, macAddress, ipPort, timeout);
    }

    private void prepareSession(SessionConfig config) {
        BoardShim preparedBoard = null;
        boolean prepared = false;
        try {
            BrainFlowInputParams params = new BrainFlowInputParams();
            params.ip_address = config.ipAddress;
            params.ip_port = config.ipPort;
            params.serial_number = config.serialNumber;
            params.mac_address = config.macAddress;
            params.timeout = config.timeout;

            Log.i(TAG, "Creating BoardShim: boardId=" + config.boardId
                    + ", timeout=" + config.timeout
                    + ", serialNumberSet=" + !config.serialNumber.isEmpty()
                    + ", macAddressSet=" + !config.macAddress.isEmpty()
                    + ", ipAddressSet=" + !config.ipAddress.isEmpty()
                    + ", ipPort=" + config.ipPort);
            preparedBoard = new BoardShim(config.boardId, params);
            Log.i(TAG, "Calling prepare_session for boardId=" + config.boardId);
            preparedBoard.prepare_session();
            prepared = true;
            Log.i(TAG, "prepare_session completed, board is connected");
            Log.i(TAG, "Calling start_stream for boardId=" + config.boardId);
            preparedBoard.start_stream();
            Log.i(TAG, "start_stream completed");

            BoardShim finalBoard = preparedBoard;
            int finalSamplingRate = BoardShim.get_sampling_rate(config.boardId);
            int[] finalChannels = BoardShim.get_exg_channels(config.boardId);
            Log.i(TAG, "BrainFlow stream metadata: samplingRate=" + finalSamplingRate
                    + ", exgChannels=" + Arrays.toString(finalChannels));
            runOnUiThread(() -> finishPrepareSession(finalBoard, finalSamplingRate, finalChannels));
        } catch (Exception e) {
            Log.e(TAG, "BrainFlow prepare/start failed", e);
            if (prepared && preparedBoard != null) {
                releaseBoard(preparedBoard);
            }
            String stackTrace = Log.getStackTraceString(e);
            runOnUiThread(() -> failPrepareSession(stackTrace));
        }
    }

    private void finishPrepareSession(BoardShim preparedBoard, int preparedSamplingRate, int[] preparedChannels) {
        prepareThread = null;
        if (destroyed || isFinishing()) {
            releaseBoard(preparedBoard);
            resetSessionState();
            return;
        }

        boardShim = preparedBoard;
        samplingRate = preparedSamplingRate;
        channels = preparedChannels;
        isSessionReady = true;
        Log.i(TAG, "BrainFlow session ready for plotting: samplingRate=" + samplingRate
                + ", channels=" + Arrays.toString(channels));
        SettingsActivity.isPrevFailed = false;
        keepSessionOnDestroy = true;
        recreate();
    }

    private void failPrepareSession(String stackTrace) {
        prepareThread = null;
        Log.e(TAG, stackTrace);
        SettingsActivity.isPrevFailed = true;
        resetSessionState();
        if (!destroyed && !isFinishing()) {
            Intent myIntent = new Intent(this, SettingsActivity.class);
            startActivity(myIntent);
            finish();
        }
    }

    private void releaseCurrentSession() {
        if (boardShim != null) {
            releaseBoard(boardShim);
        }
    }

    private void releaseBoard(BoardShim board) {
        try {
            Log.i(TAG, "Releasing BrainFlow session");
            board.release_session();
            Log.i(TAG, "BrainFlow session released");
        } catch (BrainFlowError e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static boolean isMuseBoard(int boardId) {
        return boardId == MUSE_2_BOARD || boardId == MUSE_S_BOARD || boardId == MUSE_ATHENA_BOARD;
    }

    private int parseIntPreference(SharedPreferences prefs, String key, int defaultValue) {
        try {
            return Integer.valueOf(prefs.getString(key, String.valueOf(defaultValue)).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void resetSessionState() {
        isSessionReady = false;
        boardShim = null;
        samplingRate = 0;
        channels = new int[0];
    }

    private static class SessionConfig {
        final int boardId;
        final String ipAddress;
        final String serialNumber;
        final String macAddress;
        final int ipPort;
        final int timeout;

        SessionConfig(int boardId, String ipAddress, String serialNumber, String macAddress, int ipPort, int timeout) {
            this.boardId = boardId;
            this.ipAddress = ipAddress;
            this.serialNumber = serialNumber;
            this.macAddress = macAddress;
            this.ipPort = ipPort;
            this.timeout = timeout;
        }
    }
}
