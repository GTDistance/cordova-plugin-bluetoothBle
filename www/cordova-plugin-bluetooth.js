var exec = require('cordova/exec');

var bluetoothBle = {
    getWifiName:function(success){
      exec(success, null, "BluetoothBle", "getWifiName", []);
    },
    bluetoothSearch:function(success,error){
        exec(success, error, "BluetoothBle", "bluetoothSearch", []);
    },
    bluetoothSend:function(sendMessage,success,error){
        exec(success,error,"BluetoothBle","bluetoothSend",[sendMessage.ssid,sendMessage.pwd,sendMessage.address,sendMessage.psn]);
    },
    bluetoothStop:function () {
      exec(null,null,"BluetoothBle","bluetoothStop",[]);
    }
};
module.exports = bluetoothBle;
