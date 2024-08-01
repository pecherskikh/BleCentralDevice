package com.example.blecentraldevice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

import android.Manifest;

import java.util.List;

import android.bluetooth.le.ScanFilter;
import android.os.AsyncTask;
import android.bluetooth.le.ScanSettings;

import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.core.app.ActivityCompat;

import android.bluetooth.le.ScanCallback;
import android.annotation.SuppressLint;
import android.bluetooth.le.ScanResult;
import android.util.Log;

import static android.content.ContentValues.TAG;


public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    Button startScanningButton;
    Button stopScanningButton;
    ListView deviceListView;
    TextView textViewTemp;
    //--------------------
    ArrayAdapter<String> listAdapter;
    BluetoothDevice device;
    ArrayList<BluetoothDevice> deviceList;
    BluetoothLeScanner bluetoothLeScanner;
    BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;
    BluetoothGatt mBluetoothGatt;
    //----------------------------------------------
    ListView listServChar;
    ArrayAdapter<String> adapterServChar;
    ArrayList<String> listServCharUUID = new ArrayList<>();
    //----------------------------------------------
    Button txButton;
    Button rxButton;
    
    int alertStatus = 0;
    BluetoothGattService serviceAlert, servicePower;
    BluetoothGattCharacteristic characteristicAlert, characteristicPower;


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        //*************************************************************************************
        //                  I N I T   F U N C T
        //*************************************************************************************
        textViewTemp = findViewById(R.id.textView);
        //-------------------------------------------------------------------------------------
        startScanningButton = findViewById(R.id.button);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScanning();
            }
        });
        //-------------------------------------------------------------------------------------
        stopScanningButton = findViewById(R.id.button2);
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScanning();
            }
        });
        //---------------------------------------------------------------------------------
        deviceListView = findViewById(R.id.listView);
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        deviceListView.setAdapter(listAdapter);
        deviceListView.setOnItemClickListener((adapterView, view, position, id) -> {
            stopScanning();
            device = deviceList.get(position);
            mBluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
        });
        //--------------------------------------------------------------
        deviceList = new ArrayList<>();
        initializeBluetooth();
        //--------------------------------------------------------------
        listServChar = findViewById(R.id.listView2);
        adapterServChar = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listServChar.setAdapter(adapterServChar);
        listServChar.setOnItemClickListener((adapterView, view, position, id) -> {
            String uuid = listServCharUUID.get(position);
            CharactRxData(uuid);
        });
        //--------------------------------------------------------------
        txButton = findViewById(R.id.button3);
        txButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CharactTx();
            }
        });
        rxButton = findViewById(R.id.button4);
        rxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CharactRx();
            }
        });
    }
    //*****************************************************************************************
    //                  S C A N   F U N C T
    //*****************************************************************************************
    @SuppressLint("MissingPermission")
    public void startScanning() {
        if (!bluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }
        // We only need location permission when we start scanning
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestLocationPermission();
        } else {
            deviceList.clear();
            listAdapter.clear();
            stopScanningButton.setEnabled(true);
            startScanningButton.setEnabled(false);
            textViewTemp.setText("Поиск устройства");

            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
            filters.add(scanFilterBuilder.build());

            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
            settingsBuilder.setLegacy(false);
            AsyncTask.execute(() -> bluetoothLeScanner.startScan(leScanCallBack));
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        stopScanningButton.setEnabled(false);
        startScanningButton.setEnabled(true);
        textViewTemp.setText("Поиск остановлен");
        AsyncTask.execute(() -> bluetoothLeScanner.stopScan(leScanCallBack));
    }

    public void initializeBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    private boolean hasPermission(String permissionType) {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED;
    }

    private void promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activityResultLauncher.launch(enableIntent);
        }
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != MainActivity.RESULT_OK) {
                    promptEnableBluetooth();
                }
            }
    );

    private void requestLocationPermission() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return;
        }
        runOnUiThread(() -> {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Location Permission Required");
            alertDialog.setMessage("This app needs location access to detect peripherals.");
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE));
            alertDialog.show();
        });
    }

    //************************************************************************************
    //                      S C A N   C A L L   B A C K
    //************************************************************************************
    //    The BluetoothLEScanner requires a callback function, which would be called for every device found.
    private final ScanCallback leScanCallBack = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            if (result.getDevice() != null) {
                synchronized (result.getDevice()) {
                    listShow(result, true, true);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: code:" + errorCode);
        }
    };

    //************************************************************************************
    @SuppressLint("MissingPermission")
    //    Called by ScanCallBack function to check if the device is already present in listAdapter or not.
    private boolean listShow(ScanResult res, boolean found_dev, boolean connect_dev) {

        device = res.getDevice();
        String itemDetails;
        int i;

        for (i = 0; i < deviceList.size(); ++i) {
            String addedDeviceDetail = deviceList.get(i).getAddress();
            if (addedDeviceDetail.equals(device.getAddress())) {

                itemDetails = device.getAddress() + " " + rssiStrengthPic(res.getRssi()) + "  " + res.getRssi();
                itemDetails += res.getDevice().getName() == null ? "" : "\n       " + res.getDevice().getName();

                Log.d(TAG, "Index:" + i + "/" + deviceList.size() + " " + itemDetails);
                listAdapter.remove(listAdapter.getItem(i));
                listAdapter.insert(itemDetails, i);
                return true;
            }
        }
        itemDetails = device.getAddress() + " " + rssiStrengthPic(res.getRssi()) + "  " + res.getRssi();
        itemDetails += res.getDevice().getName() == null ? "" : "\n       " + res.getDevice().getName();

        Log.e(TAG, "NEW:" + i + " " + itemDetails);
        listAdapter.add(itemDetails);
        deviceList.add(device);
        return false;
    }

    //************************************************************************************
    private String rssiStrengthPic(int rs) {
        if (rs > -45) {
            return "▁▃▅▇";
        }
        if (rs > -62) {
            return "▁▃▅";
        }
        if (rs > -80) {
            return "▁▃";
        }
        if (rs > -95) {
            return "▁";
        } else
            return "";
    }
    //************************************************************************************
    //                      C O N N E C T   C A L L   B A C K
    //************************************************************************************
    //    The connectGatt method requires a BluetoothGattCallback
    //    Here the results of connection state changes and services discovery would be delivered asynchronously.

    protected BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        //*********************************************************************************
        private volatile boolean isOnCharacteristicReadRunning = false;

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String address = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.w(TAG, "onConnectionStateChangeMy() - Successfully connected to " + address);
                    boolean discoverServicesOk = gatt.discoverServices();
                    Log.i(TAG, "onConnectionStateChange: discovered Services: " + discoverServicesOk);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.w(TAG, "onConnectionStateChangeMy() - Successfully disconnected from " + address);
                    gatt.close();
                }
            } else {
                Log.w(TAG, "onConnectionStateChangeMy: Error " + status + " encountered for " + address);
            }
        }
        //************************************************************************************
        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            final List<BluetoothGattService> services = gatt.getServices();
            runOnUiThread(() -> {

                for (int i = 0; i < services.size(); i++) {
                    BluetoothGattService service = services.get(i);
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    StringBuilder log = new StringBuilder("\nService Id: \n" + "UUID: " + service.getUuid().toString());

                    adapterServChar.add("     Service UUID : " + service.getUuid().toString());
                    listServCharUUID.add(service.getUuid().toString());

                    for (int j = 0; j < characteristics.size(); j++) {
                        BluetoothGattCharacteristic characteristic = characteristics.get(j);
                        String characteristicUuid = characteristic.getUuid().toString();

                        log.append("\n   Characteristic: ");
                        log.append("\n   UUID: ").append(characteristicUuid);

                        if (characteristicUuid.equals("00002a06-0000-1000-8000-00805f9b34fb")) {
                            characteristicAlert = characteristic;
                            serviceAlert = service;
                        }
                        if (characteristicUuid.equals("00002a07-0000-1000-8000-00805f9b34fb")) {
                            characteristicPower = characteristic;
                            servicePower = service;
                        }

                        adapterServChar.add("Charact UUID : " + characteristicUuid);
                        listServCharUUID.add(service.getUuid().toString());
                    }
                    Log.d(TAG, "\nonServicesDiscovered: New Service: " + log);
                }
            });
        }
        //************************************************************************************
        //public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int stat) {}
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                textViewTemp.setText("Запись " + Integer.toString(alertStatus) + " успешна");
                Log.i(TAG, "onCharacteristicRead: Write characteristic: UUID: " + characteristic.getUuid().toString());
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                Log.e(TAG, "onCharacteristicRead: Write not permitted for " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "onCharacteristicRead: Characteristic write failed for " + characteristic.getUuid().toString());
            }
        }
        //************************************************************************************
        //public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int stat) {}
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onCharacteristicRead: Read characteristic: UUID: " + characteristic.getUuid().toString());

                byte[] valueInputCode = new byte[characteristic.getValue().length];
                System.arraycopy(characteristic.getValue(), 0, valueInputCode, 0, characteristic.getValue().length);

                StringBuffer sb1 = new StringBuffer();
                for (int j = 0; j < valueInputCode.length; j++) {
                    sb1.append(String.format("%02X", valueInputCode[j]));
                }
                Log.i(TAG, "onCharacteristicRead: Value: " + sb1);
                textViewTemp.setText("Уровень мощности = " + sb1.toString());

            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                Log.e(TAG, "onCharacteristicRead: Read not permitted for " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "onCharacteristicRead: Characteristic read failed for " + characteristic.getUuid().toString());
            }
        }
    };
    //************************************************************************************
    //************************************************************************************
    public void CharactRxData(String uuid)
    {
        Log.d(TAG, "UUID : " + uuid);
        textViewTemp.setText(uuid);
    }
    //************************************************************************************
    public boolean CharactTx()
    {
        if ((mBluetoothGatt == null) || (serviceAlert == null) || (characteristicAlert == null)) {
            return false;
        }
        alertStatus ^= 0x01;
        byte[] alert = new byte[1];
        alert[0] = (byte)alertStatus;

        characteristicAlert.setValue(alert);
        characteristicAlert.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 0);
        }
        mBluetoothGatt.writeCharacteristic(characteristicAlert);
        return true;
    }
    //************************************************************************************
    @SuppressLint("MissingPermission")
    public void CharactRx()
    {
        mBluetoothGatt.readCharacteristic(characteristicPower);
    }
}
//****************************************************************************************
//****************************************************************************************

