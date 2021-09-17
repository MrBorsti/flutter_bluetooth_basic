package com.tablemi.flutter_bluetooth_basic;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gprinter.command.FactoryCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

/**
 * FlutterBluetoothBasicPlugin
 */
public class FlutterBluetoothBasicPlugin implements MethodCallHandler, RequestPermissionsResultListener, FlutterPlugin, ActivityAware {
    private static final String TAG = "BluetoothBasicPlugin";
    private final int id = 0;
    private ThreadPool threadPool;
    private static final int REQUEST_LOCATION_PERMISSIONS = 1451;
    private static final String NAMESPACE = "flutter_bluetooth_basic";
    ActivityPluginBinding binding;
    private BinaryMessenger messenger;
    private Activity activity;
    private MethodChannel channel;
    private EventChannel stateChannel;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private Result pendingResult;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        messenger = binding.getBinaryMessenger();
    }


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        tearDownChannels();
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        setupChannels(messenger, binding);
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        tearDownChannels();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        setupChannels(messenger, binding);
    }

    @Override
    public void onDetachedFromActivity() {
        tearDownChannels();
    }

    public void setupChannels(BinaryMessenger messenger, ActivityPluginBinding binding) {
        this.binding = binding;
        this.activity = binding.getActivity();
        this.channel = new MethodChannel(messenger, NAMESPACE + "/methods");
        this.stateChannel = new EventChannel(messenger, NAMESPACE + "/state");
        this.mBluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        this.mBluetoothAdapter = mBluetoothManager.getAdapter();
        channel.setMethodCallHandler(this);
        stateChannel.setStreamHandler(stateStreamHandler);
        binding.addRequestPermissionsResultListener(this);
    }

    private void tearDownChannels() {
        if (binding != null) {
            binding.removeRequestPermissionsResultListener(this);
        }
        if (channel != null) {
            channel.setMethodCallHandler(null);
        }
        if (stateChannel != null) {
            stateChannel.setStreamHandler(null);
        }

        mBluetoothAdapter = null;
        mBluetoothManager = null;

        activity = null;
        binding = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
            result.error("bluetooth_unavailable", "Bluetooth is unavailable", null);
            return;
        }

        final Map<String, Object> args = call.arguments();

        switch (call.method) {
            case "state":
                state(result);
                break;
            case "isAvailable":
                result.success(mBluetoothAdapter != null);
                break;
            case "isOn":
                result.success(mBluetoothAdapter.isEnabled());
                break;
            case "isConnected":
                result.success(threadPool != null);
                break;
            case "startScan": {
                if (needToAskForPermissions()) {
                    pendingResult = result;
                    break;
                }
                startScan(result);
                break;
            }
            case "stopScan":
                stopScan();
                result.success(null);
                break;
            case "connect":
                connect(result, args);
                break;
            case "disconnect":
                result.success(disconnect());
                break;
            case "destroy":
                result.success(destroy());
                break;
            case "writeData":
                writeData(result, args);
                break;
            case "print":
            case "printReceipt":
            case "printLabel":
                print(result, args);
                break;
            case "printTest":
                printTest(result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private boolean needToAskForPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSIONS);
                return true;
            }
        } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSIONS);
            return true;
        }
        return false;
    }

    private void state(Result result) {
        try {
            switch (mBluetoothAdapter.getState()) {
                case BluetoothAdapter.STATE_OFF:
                    result.success(BluetoothAdapter.STATE_OFF);
                    break;
                case BluetoothAdapter.STATE_ON:
                    result.success(BluetoothAdapter.STATE_ON);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    result.success(BluetoothAdapter.STATE_TURNING_OFF);
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    result.success(BluetoothAdapter.STATE_TURNING_ON);
                    break;
                default:
                    result.success(0);
                    break;
            }
        } catch (SecurityException e) {
            result.error("invalid_argument", "Argument 'address' not found", null);
        }

    }

    private void startScan(Result result) {
        Log.d(TAG, "start scan ");

        try {
            startScan();
            result.success(null);
        } catch (Exception e) {
            result.error("startScan", e.getMessage(), null);
        }
    }

    private void invokeMethodUIThread(final String name, final BluetoothDevice device) {
        final Map<String, Object> ret = new HashMap<>();
        ret.put("address", device.getAddress());
        ret.put("name", device.getName());
        ret.put("type", device.getType());

        activity.runOnUiThread(
                () -> channel.invokeMethod(name, ret));
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null) {
                invokeMethodUIThread("ScanResult", device);
            }
        }
    };

    private void startScan() throws IllegalStateException {
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null)
            throw new IllegalStateException("getBluetoothLeScanner() is null. Is the Adapter on?");

        // 0:lowPower 1:balanced 2:lowLatency -1:opportunistic
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(null, settings, mScanCallback);
    }

    private void stopScan() {
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) scanner.stopScan(mScanCallback);
    }

    private void connect(Result result, Map<String, Object> args) {
        if (args.containsKey("address")) {
            String address = (String) args.get("address");
            disconnect();

            new DeviceConnFactoryManager.Build()
                    .setId(id)
                    // Set the connection method
                    .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                    // Set the connected Bluetooth mac address
                    .setMacAddress(address)
                    .build();
            // Open port
            threadPool = ThreadPool.getInstantiation();
            threadPool.addSerialTask(() -> DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort());

            result.success(true);
        } else {
            result.error("invalid_argument", "Argument 'address' not found", null);
        }

    }

    /**
     * Reconnect to recycle the last connected object to avoid memory leaks
     */
    private boolean disconnect() {

        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null && DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort != null) {
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].reader.cancel();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort.closePort();
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].mPort = null;
        }
        return true;
    }

    private boolean destroy() {
        DeviceConnFactoryManager.closeAllPort();
        if (threadPool != null) {
            threadPool.stopThreadPool();
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void writeData(Result result, Map<String, Object> args) {
        if (args.containsKey("bytes")) {
            final ArrayList<Integer> bytes = (ArrayList<Integer>) args.get("bytes");

            if (bytes != null) {
                threadPool = ThreadPool.getInstantiation();
                threadPool.addSerialTask(() -> {
                    Vector<Byte> vectorData = new Vector<>();
                    for (int i = 0; i < bytes.size(); ++i) {
                        Integer val = bytes.get(i);
                        vectorData.add(Byte.valueOf(Integer.toString(val > 127 ? val - 256 : val)));
                    }

                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(vectorData);
                });
            } else {
                result.error("bytes_empty", "Bytes param is empty", null);
            }
        } else {
            result.error("bytes_empty", "Bytes param is empty", null);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan(pendingResult);
            } else {
                pendingResult.error("no_permissions", "This app requires location permissions for scanning", null);
                pendingResult = null;
            }
            return true;
        }
        return false;

    }

    private final StreamHandler stateStreamHandler = new StreamHandler() {
        private EventSink sink;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                Log.d(TAG, "stateStreamHandler, current action: " + action);

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    threadPool = null;
                    sink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    sink.success(1);
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    threadPool = null;
                    sink.success(0);
                }
            }
        };

        @Override
        public void onListen(Object o, EventSink eventSink) {
            sink = eventSink;
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            activity.registerReceiver(mReceiver, filter);
        }

        @Override
        public void onCancel(Object o) {
            sink = null;
            activity.unregisterReceiver(mReceiver);
        }
    };

    private void printTest(Result result) {
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState()) {

            result.error("not connect", "state not right", null);
        }

        threadPool = ThreadPool.getInstantiation();
        threadPool.addSerialTask(() -> {
            if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.ESC) {
                DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendByteDataImmediately(FactoryCommand.printSelfTest(FactoryCommand.printerMode.ESC));
            } else if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.TSC) {
                DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendByteDataImmediately(FactoryCommand.printSelfTest(FactoryCommand.printerMode.TSC));
            } else if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.CPCL) {
                DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendByteDataImmediately(FactoryCommand.printSelfTest(FactoryCommand.printerMode.CPCL));
            }
        });

    }

    @SuppressWarnings("unchecked")
    private void print(Result result, Map<String, Object> args) {
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null ||
                !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getConnState()) {

            result.error("not connect", "state not right", null);
        }

        if (args.containsKey("config") && args.containsKey("data")) {
            final Map<String, Object> config = (Map<String, Object>) args.get("config");
            final List<Map<String, Object>> list = (List<Map<String, Object>>) args.get("data");
            if (list == null) {
                return;
            }

            threadPool = ThreadPool.getInstantiation();
            threadPool.addSerialTask(() -> {
                if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.ESC) {
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(PrintContent.mapToReceipt(config, list));
                } else if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.TSC) {
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(PrintContent.mapToLabel(config, list));
                } else if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].getCurrentPrinterCommand() == PrinterCommand.CPCL) {
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(PrintContent.mapToCPCL(config, list));
                }
            });
        } else {
            result.error("please add config or data", "", null);
        }

    }
}
