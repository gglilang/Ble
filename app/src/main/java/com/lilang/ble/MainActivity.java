package com.lilang.ble;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;
    //10秒钟以后，结束扫描设备
    private static final long SCAN_PERIOD = 10000;
    private static final String TAG = "MainActivity";
    //蓝牙适配器
    private BluetoothAdapter bluetoothAdapter;

    private Handler handler;

   // final static private UUID uuid1 = UUID.fromString("1111");
    //private String deviceName = "Alert Notification";
    private String deviceAddress = "45:D9:C8:DD:69:0A";

    private List<BluetoothDevice> bluetoothDevices;
    private BluetoothDevice findBluetoothDevice;


    private BluetoothLeService bluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private TextView tv_status;
    private TextView tv_weight;

    private String findServiceUUID = "00001811-0000-1000-8000-00805f9b34fb";
    private String findCharacteristicUUID = "527d54f8-c581-4c7e-bd1a-d0a3e28cf135";

    private boolean isBindService = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_status = (TextView) findViewById(R.id.tv_status);
        tv_weight = (TextView) findViewById(R.id.tv_weight);

        bluetoothDevices = new ArrayList<>();
        handler = new Handler();

        //判断设备是否支持ble，不支持就直接跳出
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this, "设备不支持BLE", Toast.LENGTH_SHORT).show();

            finish();
        }

        //初始化蓝牙适配器，适配器是从BluetoothManager中得到
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        //检查设备是否支持蓝牙
        if(bluetoothAdapter == null){
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
        }

//        //监听设备数据的变化
//        new Thread(){
//            @Override
//            public void run() {
//                super.run();
//                while(findBluetoothDevice != null){
//                    System.out.println("================");
//                    deviceAddress = findBluetoothDevice.getAddress();
//                    Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
//                    bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
//                    System.out.println("启动服务");
//                }
//            }
//        }.start();


    }


    @Override
    protected void onResume() {
        super.onResume();

        //如果蓝牙没有打开，请求用户打开蓝牙
        if(!bluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        scanLeDevice(true);


        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if(bluetoothLeService != null){
            final boolean result = bluetoothLeService.connect(deviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }


    /**
     * 扫描设备
     * @param enable
     */
    private void scanLeDevice(final boolean enable) {
        bluetoothAdapter.startLeScan(leScanCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            System.out.println(bluetoothDevice.getName());
            if(!bluetoothDevices.contains(bluetoothDevice)) {
                bluetoothDevices.add(bluetoothDevice);
                //if("Alert Notification".equals(bluetoothDevice.getName())) System.out.println("jjjjj" + bluetoothDevice.getAddress());
                if(deviceAddress.equals(bluetoothDevice.getAddress())){
                    findBluetoothDevice = bluetoothDevice;
                    Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
                    bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
                    isBindService = true;
                    System.out.println("启动服务========");
                }


            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if(!bluetoothLeService.initialize()){
                Log.e(TAG, "无法初始化蓝牙");
                finish();
            }

            //自动连接设备，并开始初始化
            bluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            System.out.println("action" + action);
            if(BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)){
                System.out.println("成功");
                mConnected = true;

                updateConnectionState("成功");
            } else if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)){
                System.out.println("失败");
                mConnected = false;
                updateConnectionState("失败");
            }  else if(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){

                displayGattServices(bluetoothLeService.getSupportedGattServices());
            }else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void displayData(String data) {
        if(data != null){
            tv_weight.setText(data);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if(gattServices == null) return;
        String serviceUUID = null;
        String characteristicUUID = null;


        for(BluetoothGattService gattService : gattServices){

            serviceUUID = gattService.getUuid().toString();

            if(findServiceUUID.equals(serviceUUID)){
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                for(BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics){

                    characteristicUUID = gattCharacteristic.getUuid().toString();
                    System.out.println("成功找到serviceUUID：" + characteristicUUID);
                    final int charaProp = gattCharacteristic.getProperties();
                    if(findCharacteristicUUID.equals(characteristicUUID)){
                        System.out.println("成功找到CharacteristicUUID");
                        if((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0){
                            if(mNotifyCharacteristic != null){
                                bluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false
                                );
                                mNotifyCharacteristic = null;
                            }
                            bluetoothLeService.readCharacteristic(gattCharacteristic);
                        }

                        if((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                            mNotifyCharacteristic = gattCharacteristic;
                            bluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        }
                        return;
                    }

                }
            }
        }
    }

    private void updateConnectionState(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_status.setText(text);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(isBindService) {
            unbindService(serviceConnection);
            bluetoothLeService = null;
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter(){
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
