package com.lilang.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private static final String TAG = BluetoothLeService.class.getSimpleName();
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private String bluetoothDeviceAddress;
    //连接状态
    private static final int STATE_DISCONNECTED = 0;    //没有连接
    private static final int STATE_CONNECTING = 1;      //正在连接
    private static final int STATE_CONNECTED = 2;       //连接成功

    private int connectionState = STATE_DISCONNECTED;   //当前ble的连接状态

    public final static String ACTION_GATT_CONNECTED =
            "com.lilang.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.lilang.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.lilang.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.lilang.ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.lilang.ble.EXTRA_DATA";

    //要连接设备的BluetoothGattCharacteristic的UIDD
    public final static UUID UUID_WEIGHT = UUID.fromString(MyGattAttributes.WEIGHT);


    /**
     * 实现GATT中app关心的事件的回调方法
     * 例如连接状态的改变、服务的实现
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction;
            //已经连接ble设备
            if(newState == BluetoothProfile.STATE_CONNECTED){
                intentAction = ACTION_GATT_CONNECTED;
                connectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "连接到GATT服务");
                Log.i(TAG, "尝试搜索连接设备所支持的service。:" + bluetoothGatt.discoverServices());
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                intentAction = ACTION_GATT_DISCONNECTED;
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "不能连接GATT服务");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                System.out.println("status:" + status +"======" +  BluetoothGatt.GATT_SUCCESS);
            }else{
                Log.w("TAG", "onServicesDiscovered接受到的状态：" + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }
    };


    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private void broadcastUpdate(final String action){
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);

        if(UUID_WEIGHT.equals(characteristic.getUuid())){
            int flag = characteristic.getProperties();
            int format = -1;
            if((flag & 0x01) != 0){
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "体重的格式是UINT16");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "体重的格式是UINT8");
            }
            final float weight = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("接收到的体重：", weight));
            intent.putExtra(EXTRA_DATA, String.valueOf(weight));
        } else {
            final byte[] data = characteristic.getValue();
            if(data != null && data.length > 0){
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data){
                    stringBuilder.append(String.format("%02X ", byteChar));
                }
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder{
        BluetoothLeService getService(){
            return BluetoothLeService.this;
        }
    }


    /**
     * 初始化本地蓝牙适配器的引用。
     * @return 初始化是否成功
     */
    public boolean initialize(){
        if(bluetoothManager == null){
            bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            if(bluetoothManager == null){
                Log.e(TAG, "不能初始化BluetoothManager");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null){
            Log.e(TAG, "无法获得BluetoothAdapter");
            return false;
        }

        return true;
    }

    /**
     * 根据ble设备连接到GATT服务主机
     * @param address 设备的地址
     * @return 返回真表示连接成功 返回结果通过连接异步
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address){
        if(bluetoothAdapter == null || address == null){
            Log.w(TAG, "BluetoothAdapter没有初始化或空地址");
            return false;
        }

        if(bluetoothDeviceAddress != null && address.equals(bluetoothDeviceAddress)
                && bluetoothGatt != null){
            Log.d(TAG, "尝试使用一个已经存在的BluetoothGatt去连接");
            if(bluetoothGatt.connect()){
                connectionState = STATE_CONNECTING;
                return true;
            }else{
                return false;
            }
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if(device == null){
            Log.w(TAG, "没有找到设备，不能连接");
            return false;
        }

        //我们想直接连接设备，所以我们设置自动连接参数为false
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        Log.d(TAG, "尝试创建一个新的连接");
        bluetoothDeviceAddress = address;
        connectionState = STATE_CONNECTING;
        return true;
    }

    public void disconnect(){
        if(bluetoothAdapter == null || bluetoothGatt == null){
            Log.w(TAG, "BluetoothAdapter没有初始化");
            return;
        }
        bluetoothGatt.disconnect();
    }


    /**
     * 释放资源
     */
    public void close(){
        if(bluetoothGatt == null){
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic){
        if(bluetoothAdapter == null || bluetoothGatt == null){
            Log.w(TAG, "BluetoothAdapter没有初始化");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled){
        if(bluetoothAdapter == null || bluetoothGatt == null){
            Log.w(TAG, "BluetoothAdapter没有初始化");
            return;
        }

        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices(){
        if(bluetoothGatt == null) return null;

        return bluetoothGatt.getServices();
    }

}
