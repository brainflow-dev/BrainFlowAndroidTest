package com.example.brainflowplot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import brainflow.BoardShim;
import brainflow.BrainFlowError;
import brainflow.BrainFlowInputParams;

public class SettingsActivity extends AppCompatActivity {

    public BoardShim boardShim = null;
    public int samplingRate = 0;
    public int[] channels = null;

    private boolean isTryingToConnect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public void prepareSession(View view) {
        // don't run it twice
        if (isTryingToConnect) {
            return;
        }
        isTryingToConnect = true;

        Context context = getApplicationContext();
        CharSequence text = "Trying to establish connection";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int boardId = Integer.valueOf(prefs.getString(getString(R.string.board_id_key), "-1"));
        String ipAddr = prefs.getString(getString(R.string.ip_address_key), "");
        int ipPort = 0;
        try {
            ipPort = Integer.valueOf(prefs.getString(getString(R.string.ip_port_key), "0"));
        } catch (Exception e) {
            // do nothing
        }
        String dataType = prefs.getString(getString(R.string.data_type_key), "");
        boolean use_timeseries = prefs.getBoolean(getString(R.string.plot_timeseries_key), true);
        boolean use_psd = prefs.getBoolean(getString(R.string.plot_psd_key), true);
        boolean use_bandpower = prefs.getBoolean(getString(R.string.plot_bandpower_key), true);

        if ((!use_bandpower) && (!use_psd) && (!use_timeseries)) {
            text = "No widgets selected";
            duration = Toast.LENGTH_LONG;
            toast = Toast.makeText(context, text, duration);
            toast.show();
            isTryingToConnect = false;
            return;
        }
        try {
            BrainFlowInputParams params = new BrainFlowInputParams();
            params.ip_address = ipAddr;
            params.ip_port = ipPort;
            boardShim = new BoardShim(boardId, params);
            boardShim.prepare_session();
            boardShim.start_stream();
        } catch (Exception e) {
            text = "Error occurred, validate provided parameters and your board";
            duration = Toast.LENGTH_LONG;
            toast = Toast.makeText(context, text, duration);
            toast.show();
            Log.e(getString(R.string.log_tag), e.getMessage());
        }

        isTryingToConnect = false;
    }

    @Override
    protected void onDestroy() {
        try {
            if (boardShim != null) {
                boardShim.release_session();
            }
        } catch (BrainFlowError e) {
            Log.e(getString(R.string.log_tag), e.getMessage());
        }
        super.onDestroy();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}