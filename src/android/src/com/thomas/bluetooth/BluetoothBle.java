package com.thomas.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class Bluetooth extends CordovaPlugin  {

//    private static final int REQUEST_PERMISSION_ACCESS_LOCATION = 0;

    private BluetoothAdapter mBluetoothAdapter;
    private JSONArray jsonArray ;
    private final UUID MY_UUID = UUID.fromString("abcd1234-ab12-ab12-ab12-abcdef123456");//随便定义一个

    private CallbackContext callbackContext;
    private boolean isSearch = true;



    @Override
    protected void pluginInitialize() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestPermission();
        registReceiver();
    }


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getWifiName".equals(action)){
            getWifiName(callbackContext);
        } else if ("bluetoothSearch".equals(action)){
            this.callbackContext = callbackContext;
            isSearch = true;
            searchBluetooth();
            return true;
        }else if("bluetoothSend".equals(action)){
            //判断当前是否正在搜索
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
            isSearch = false;
            this.callbackContext = callbackContext;
            String ssid = args.getString(0);
            String pwd = args.getString(1);
            String address = args.getString(2);
            String psn = args.getString(3);
            if (checkParam(ssid,pwd,address,psn))
                bluetoothSend(ssid,pwd,address,psn);
            return true;
        } else if ("bluetoothStop".equals(action)) {
            isSearch = false;
            stopBluetooth();
            return true;
        }
        return false;
    }

    private boolean checkParam(String ssid, String pwd, String address, String psn) {
        if (isEmpty(ssid)){
            callbackContext.error("请填写wifi的名称");
            return false;
        }
        if (isEmpty(pwd)){
            callbackContext.error("请填写wifi的密码");
            return false;
        }
        if (isEmpty(address)){
            callbackContext.error("请填写蓝牙设备的地址");
            return false;
        }
        if (isEmpty(psn)){
            callbackContext.error("请填写设备的PSN");
            return false;
        }
        return true;
    }
    private boolean isEmpty(String str){
        if (str !=null &&!"".equals(str)){
            return false;
        }
        return true;
    }

    private void getWifiName(CallbackContext callbackContext) {
        WifiManager wifiManager = (WifiManager) cordova.getActivity().getApplicationContext().getSystemService(cordova.getActivity().getApplicationContext().WIFI_SERVICE);
        WifiInfo mWifiInfo = wifiManager.getConnectionInfo();
        String ssid = null;
        if (mWifiInfo != null ) {
            int len = mWifiInfo.getSSID().length();
            if (mWifiInfo.getSSID().startsWith("\"") && mWifiInfo.getSSID().endsWith("\"")) {
                ssid = mWifiInfo.getSSID().substring(1, len - 1);
            } else {
                ssid = mWifiInfo.getSSID();
            }
        }
        callbackContext.success(ssid);
    }

    private void bluetoothSend(String ssid, String pwd, String address,String psn) {
        new AcceptThread(ssid,pwd,address,psn).start();
    }

    private void registReceiver() {
        // 设置广播信息过滤
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);//每搜索到一个设备就会发送一个该广播
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//当全部搜索完后发送该广播
        filter.setPriority(Integer.MAX_VALUE);//设置优先级
        // 注册蓝牙搜索广播接收者，接收并处理搜索结果
        cordova.getActivity().registerReceiver(receiver, filter);
    }

    private void requestPermission() {
        //获取定位权限
//        if (Build.VERSION.SDK_INT >= 23) {
//            int checkAccessFinePermission = ActivityCompat.checkSelfPermission(cordova.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION);
//            if (checkAccessFinePermission != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(cordova.getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_ACCESS_LOCATION);
//                Log.d(TAG, "没有权限，请求权限");
//                return;
//            }
//            Log.d(TAG, "已有定位权限");
//        }
        //做下面该做的事
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.getActivity().startActivity(intent);
        }
    }



    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    try {
                        JSONObject jsonObject = new JSONObject();
                        JSONArray deviceArray = new JSONArray();
                        JSONObject deviceObj = new JSONObject();
                        deviceObj.put("name",device.getName());
                        deviceObj.put("address",device.getAddress());
                        deviceObj.put("deviceType",device.getBluetoothClass().getDeviceClass());
                        deviceArray.put(deviceObj);
                        jsonObject.put("type","discover_one");
                        jsonObject.put("devices",deviceArray);
                        jsonArray.put(deviceObj);
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,jsonObject.toString());
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //已搜素完成
                try {
                    if (isSearch){
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("type","discover_finish");
                        jsonObject.put("devices",jsonArray);
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,jsonObject.toString());
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }
    };

    private BluetoothSocket clientSocket;
    private class AcceptThread extends Thread {
        private InputStream inputStream;
        private BluetoothDevice device;
        private OutputStream os;//输出流
        private String ssid;
        private String pwd;
        private String psn;
        public AcceptThread(String ssid, String pwd, String address,String psn) {
            this.ssid = ssid;
            this.pwd = pwd;
            this.psn = psn;

            if (clientSocket!=null){
                try {
                    clientSocket.close();
                    clientSocket =null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (device == null){
                //获得远程设备
                device = mBluetoothAdapter.getRemoteDevice(address);
            }
        }

        @Override
        public void run() {
            try {
                if (clientSocket == null) {
                    //创建客户端蓝牙Socket
                    clientSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                    //开始连接蓝牙，如果没有配对则弹出对话框提示我们进行配对
                    clientSocket.connect();
                    //获得输出流（客户端指向服务端输出文本）
                    os = clientSocket.getOutputStream();
                    inputStream = clientSocket.getInputStream();
                }
                if (os != null) {
                    //往服务端写信息
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("wifiSSID",ssid);
                    jsonObject.put("password",pwd);
                    jsonObject.put("psn",psn);
                    os.write(jsonObject.toString().getBytes("utf-8"));
                }
                int count;
                byte[] buffer =new byte[1024];
                while (inputStream != null && clientSocket.isConnected()&&(count = inputStream.read(buffer)) != -1) {
                    Message msg = Message.obtain();
                    String receive = new String(buffer, 0, count, "utf-8");
                    JSONObject jsonObject = new JSONObject(receive);
                    int id = jsonObject.getInt("id");
                    String content = jsonObject.getString("content");
                    msg.what = id;//失败
                    msg.obj = content;
                    handler.sendMessage(msg);
                    if (clientSocket!=null){
                        clientSocket.close();
                    }
                }
                clientSocket = null;
            } catch (Exception e) {
                e.printStackTrace();
                Message msg = Message.obtain();
                msg.obj = e.getMessage();
                msg.what = 1;
                handler.sendMessage(msg);
                if (clientSocket!=null){
                    try {
                        clientSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    clientSocket =null;
                }
            }
        }
    }


    /**
     * 开始搜索
     */
    private void searchBluetooth(){
        //如果当前在搜索，就先取消搜索
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        //开启搜索
        jsonArray = new JSONArray();
        mBluetoothAdapter.startDiscovery();
    }

    /**
     * 停止搜索
     */
    private void stopBluetooth(){
        //如果当前在搜索，就先取消搜索
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            String message = String.valueOf(msg.obj);
            switch (msg.what){
                case 0:
                    callbackContext.success(message);
                    break;
                case 1:
                    callbackContext.error(message);
                    break;
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        cordova.getActivity().unregisterReceiver(receiver);
    }

}
