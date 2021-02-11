package com.example.brainflowplot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import brainflow.AggOperations;
import brainflow.BoardShim;
import brainflow.BrainFlowError;
import brainflow.BrainFlowInputParams;
import brainflow.DataFilter;

public class DataActivity extends AppCompatActivity {

    // to dont sync threads for dataplot, bandpower, psd, etc each thread will has its own data array  share boardShim object
    public static BoardShim boardShim = null;
    public static int samplingRate = 0;
    public static int[] channels = null;
    public static final int[] colors = {Color.BLUE, Color.YELLOW, Color.RED, Color.MAGENTA, Color.GREEN, Color.CYAN, Color.GRAY, Color.BLACK};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);
        BottomNavigationView navView = findViewById(R.id.nav_view);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupWithNavController(navView, navController);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // read settings
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int boardId = Integer.valueOf(prefs.getString(getString(R.string.board_id_key), "-1"));
        String ipAddr = prefs.getString(getString(R.string.ip_address_key), "");
        int ipPort = 0;
        try {
            ipPort = Integer.valueOf(prefs.getString(getString(R.string.ip_port_key), "0"));
        } catch (Exception e) {
            // do nothing
        }

        try {
            BrainFlowInputParams params = new BrainFlowInputParams();
            params.ip_address = ipAddr;
            params.ip_port = ipPort;
            boardShim = new BoardShim(boardId, params);
            // prepare_session is relatively long operation, doing it in UI thread lead to black window for a few seconds
            boardShim.prepare_session();
            boardShim.start_stream();
            samplingRate = BoardShim.get_sampling_rate(boardId);
            channels = BoardShim.get_exg_channels(boardId);
            SettingsActivity.isPrevFailed = false;
        } catch (Exception e) {
            Log.e(getString(R.string.log_tag), Log.getStackTraceString(e));
            SettingsActivity.isPrevFailed = true;
        }
        if (SettingsActivity.isPrevFailed) {
            // if failed to connect go back to settings page
            Intent myIntent = new Intent(this, SettingsActivity.class);
            startActivity(myIntent);
        }
    }

    @Override
    protected void onPause() {
        try {
            if (boardShim != null) {
                boardShim.release_session();
            }
        } catch (BrainFlowError e) {
            Log.e(getString(R.string.log_tag), Log.getStackTraceString(e));
        }
        super.onPause();
    }
}