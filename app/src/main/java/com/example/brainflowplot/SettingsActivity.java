package com.example.brainflowplot;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;
    private static final int MUSE_2_BOARD = 38;
    private static final int MUSE_S_BOARD = 39;
    private static final int MUSE_ATHENA_BOARD = 67;
    public static boolean isPrevFailed = false;

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
        if (isPrevFailed) {
            Context context = getApplicationContext();
            CharSequence text = "Error occurred, validate provided parameters and your board";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
    }

    public void prepareSession(View view) {
        if (selectedBoardUsesBluetooth() && !hasBluetoothPermissions()) {
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, BLUETOOTH_PERMISSION_REQUEST);
            return;
        }
        startDataActivity();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != BLUETOOTH_PERMISSION_REQUEST) {
            return;
        }
        if (hasBluetoothPermissions()) {
            startDataActivity();
        } else {
            Toast.makeText(this, "Bluetooth and location permissions are required for Muse boards", Toast.LENGTH_LONG).show();
        }
    }

    private boolean selectedBoardUsesBluetooth() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int boardId = Integer.valueOf(prefs.getString(getString(R.string.board_id_key), "-1"));
        return boardId == MUSE_2_BOARD || boardId == MUSE_S_BOARD || boardId == MUSE_ATHENA_BOARD;
    }

    private boolean hasBluetoothPermissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startDataActivity() {
        Intent myIntent = new Intent(this, DataActivity.class);
        startActivity(myIntent);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}