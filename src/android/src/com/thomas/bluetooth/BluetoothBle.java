package com.thomas.bluetooth;

import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothBle extends CordovaPlugin {

    private BluetoothAdapter mBluetoothAdapter;
    private final UUID MY_UUID = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123456");//随便定义一个

    private CallbackContext callbackContext;
    private boolean isSearch = true;
    private List<BluetoothDevice> bluetoothDeviceList = new ArrayList<BluetoothDevice>();

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothGattservice;
    private BluetoothGattCharacteristic writeCharacteristic;
    private boolean connectedState = false;

    private final int packageMaxByte = 17;
    private final byte sendStart = 0x2b;
    private final byte receiveStart = 0x2c;
    private List<byte[]> sendBytesList;
    private List<byte[]> receiveBytesList;
    private String ssid;
    private String pwd;
    private String index;
    private String psn;
    private String mBluetoothDeviceAddress;


    private String TAG = BluetoothBle.class.getSimpleName();


    @Override
    protected void pluginInitialize() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestPermission();
    }


    private void requestPermission() {
        //做下面该做的事
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.getActivity().startActivity(intent);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getWifiName".equals(action)) {
            getWifiName(callbackContext);
        } else if ("bluetoothBleSearch".equals(action)) {
            this.callbackContext = callbackContext;
            isSearch = true;
            searchBluetoothDevice();

            return true;
        } else if ("bluetoothBleSend".equals(action)) {
            //判断当前是否正在搜索
            isSearch = false;
            this.callbackContext = callbackContext;
            this.ssid = args.getString(0);
            this.pwd = args.getString(1);
            this.index = args.getString(2);
            this.psn = args.getString(3);
            if (checkParam()) bluetoothSend();
            return true;
        } else if ("bluetoothBleStop".equals(action)) {
            isSearch = false;
            stopBluetoothBle();
            return true;
        }
        return false;
    }

    private boolean checkParam() {
        if (isEmpty(ssid)) {
            callbackContext.error("请填写wifi的名称");
            return false;
        }
        if (isEmpty(pwd)) {
            callbackContext.error("请填写wifi的密码");
            return false;
        }
        if (isEmpty(index)) {
            callbackContext.error("请给设备索引");
            return false;
        }
        if (isEmpty(psn)) {
            callbackContext.error("请填写设备的PSN");
            return false;
        }
        return true;
    }

    private boolean isEmpty(String str) {
        if (str != null && !"".equals(str)) {
            return false;
        }
        return true;
    }

    private void getWifiName(CallbackContext callbackContext) {
        WifiManager wifiManager = (WifiManager) cordova.getActivity().getApplicationContext().getSystemService(cordova.getActivity().getApplicationContext().WIFI_SERVICE);
        WifiInfo mWifiInfo = wifiManager.getConnectionInfo();
        String ssid = null;
        if (mWifiInfo != null) {
            int len = mWifiInfo.getSSID().length();
            if (mWifiInfo.getSSID().startsWith("\"") && mWifiInfo.getSSID().endsWith("\"")) {
                ssid = mWifiInfo.getSSID().substring(1, len - 1);
            } else {
                ssid = mWifiInfo.getSSID();
            }
        }
        callbackContext.success(ssid);
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void bluetoothSend() {
        generateSendList();
        stopBluetoothBle();
        BluetoothDevice device = bluetoothDeviceList.get(Integer.valueOf(index));
        connect(device.getAddress());
    }

    private List<byte[]> generateSendList() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("wifiSSID", ssid);
            jsonObject.put("password", pwd);
            jsonObject.put("psn", psn);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return string2Bytes(jsonObject.toString());
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                sendMessage(sendBytesList.remove(0));
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(cordova.getActivity(), false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(TAG, "status:" + status + ",newState:" + newState);//newState 1未连接 2连接
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedState = true;
                gatt.discoverServices();//连接成功后，我们就要去寻找我们所需要的服务，这里需要先启动服务发现，使用一句代码即可
            } else {
                connectedState = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "成功发现服务");

                sendMessage(sendBytesList.remove(0));

            } else {

                Log.e(TAG, "服务发现失败，错误码为:" + status);
            }
        }

        //读操作的回调
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "读取成功" + characteristic.getValue());
//                    sendMessage();


            } else {
                Log.e(TAG, "读取失败");
            }

        }

        //写操作的回调
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "写入成功");
//                    sendMessage("23456");
                if (sendBytesList.size() > 0)
                    sendMessage(sendBytesList.remove(0));

            } else {
                Log.e(TAG, "写入失败");
            }
        }


        //数据返回的回调（此处接收BLE设备返回数据）
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            ByteBuffer byteBuffer = ByteBuffer.wrap(characteristic.getValue());
            byte start = byteBuffer.get();
            if (start == receiveStart) {
                byte packageNum = byteBuffer.get();
                byte packageIndex = byteBuffer.get();
                byte[] content = new byte[byteBuffer.remaining()];
                byteBuffer.get(content);
                receiveBytesList.add(packageIndex - 1, content);
                if (packageIndex >= packageNum) {
                    String message = new String(unitByteArray(receiveBytesList));
                    System.out.println("receive:" + message);
                    try {
                        JSONObject jsonObject = null;
                        jsonObject = new JSONObject(message);
                        int id = jsonObject.getInt("id");
                        String contentStr = jsonObject.getString("content");
                        if (id == 0) {
                            callbackContext.success(contentStr);
                        } else {
                            callbackContext.error(contentStr);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }


            }


        }
    };

    private void sendMessage(byte[] bytes) {

        mBluetoothGattservice = mBluetoothGatt.getService(UUID.fromString("00001822-0000-1000-8000-00805f9b34fb"));
        writeCharacteristic = mBluetoothGattservice.getCharacteristic(UUID.fromString("00002abc-0000-1000-8000-00805f9b34fb"));

        final int charaProp = writeCharacteristic.getProperties();
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mBluetoothGatt.setCharacteristicNotification(writeCharacteristic, true);
        }

        writeCharacteristic.setValue(bytes);

        if (mBluetoothGatt.writeCharacteristic(writeCharacteristic)) {
            Log.i(TAG, "发送成功！");
        } else {
            Log.i(TAG, "发送失败！");
        }

    }


    private List<byte[]> string2Bytes(String str) {
        List<byte[]> list = new ArrayList<byte[]>();
        byte[] jsonObjBytes = str.getBytes();
        int jsonObjBytesLen = jsonObjBytes.length;
        int num1 = jsonObjBytesLen % packageMaxByte;
        int packageNum;
        if (num1 == 0) {
            packageNum = jsonObjBytesLen / packageMaxByte;
        } else {
            packageNum = jsonObjBytesLen / packageMaxByte + 1;
        }

        for (int i = 0; i < packageNum; i++) {
            byte[] dataBytes;
            if (i == packageNum - 1) {
                byte[] bytes = Arrays.copyOfRange(jsonObjBytes, packageMaxByte * i, jsonObjBytesLen);
                ByteBuffer byteBuffer = ByteBuffer.allocate(3 + bytes.length);
                byteBuffer.put(sendStart);
                byteBuffer.put((byte) packageNum);
                byteBuffer.put((byte) (i + 1));
                byteBuffer.put(bytes);
                dataBytes = byteBuffer.array();
            } else {
                byte[] bytes = Arrays.copyOfRange(jsonObjBytes, packageMaxByte * i, packageMaxByte * (i + 1));
                ByteBuffer byteBuffer = ByteBuffer.allocate(20);
                byteBuffer.put(sendStart);
                byteBuffer.put((byte) packageNum);
                byteBuffer.put((byte) (i + 1));
                byteBuffer.put(bytes);
                dataBytes = byteBuffer.array();
            }
            list.add(dataBytes);
        }
        return list;
    }


    /**
     * 合并byte数组
     */
    private byte[] unitByteArray(List<byte[]> receiveBytes) {


        int byteNum = 0;
        for (int i = 0; i < receiveBytes.size(); i++) {
            byteNum += receiveBytes.get(i).length;

        }

        byte[] unitByte = new byte[byteNum];
        for (int i = 0; i < receiveBytes.size(); i++) {
            byte[] bytes = receiveBytes.get(i);
            Log.d("unitByteArray", new String(bytes));

            if (i == 0) {
                for (int j = 0; j < bytes.length; j++) {
                    unitByte[j] = bytes[j];
                }
            } else {
                int lastLen = receiveBytes.get(i - 1).length;
                for (int j = 0; j < bytes.length; j++) {
                    unitByte[lastLen * i + j] = bytes[j];
                }
            }

        }

        return unitByte;
    }

    /**
     * 开始搜索
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void searchBluetoothDevice() {
        //如果当前在搜索，就先取消搜索
        disconnect();
        close();
        bluetoothDeviceList.clear();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    if (device.getName() != null && !"".equals(device.getName().trim())) {
                        bluetoothDeviceList.add(device);
                        try {
                            JSONObject deviceObj = new JSONObject();
                            deviceObj.put("name", device.getName());
                            deviceObj.put("deviceIndex", bluetoothDeviceList.indexOf(device));
                            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, deviceObj.toString());
                            pluginResult.setKeepCallback(true);
                            callbackContext.sendPluginResult(pluginResult);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

    /**
     * 停止搜索
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopBluetoothBle() {
        //如果当前在搜索，就先取消搜索
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        close();
    }


    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
}
