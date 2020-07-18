package com.example.brainflowplot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import brainflow.BoardShim;
import brainflow.BrainFlowError;
import brainflow.BrainFlowInputParams;

public class DataActivity extends AppCompatActivity {

    public BoardShim boardShim = null;
    public int samplingRate = 0;
    public int[] channels = null;

    private boolean isTryingToConnect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.

        // comment out these two methods for theme wo actionbar
        /*
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(

                R.id.navigation_dataplot, R.id.navigation_psdplot, R.id.navigation_bandpowerplot)
                .build();
         */
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        // NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

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
        String dataType = prefs.getString(getString(R.string.data_type_key), "");

        boolean connected = false;
        try {
            BrainFlowInputParams params = new BrainFlowInputParams();
            params.ip_address = ipAddr;
            params.ip_port = ipPort;
            boardShim = new BoardShim(boardId, params);
            boardShim.prepare_session();
            boardShim.start_stream();
            connected = true;
        } catch (Exception e) {
            Context context = getApplicationContext();
            CharSequence text = "Error occurred, validate provided parameters and your board";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            Log.e(getString(R.string.log_tag), e.getMessage());
            connected = false;
        }
        if (!connected) {
            // if failed to connect back to settings page
            Intent myIntent = new Intent(this, SettingsActivity.class);
            startActivity(myIntent);
        }
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

}