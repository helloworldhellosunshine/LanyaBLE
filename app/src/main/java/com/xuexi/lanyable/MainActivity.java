package com.xuexi.lanyable;

import android.Manifest;
import android.app.Activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends Activity{

    private final static String TAG = MainActivity.class.getSimpleName();

    private CircleWaveDivergenceView search_device_view;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private static Handler mHandler;
    private String mRSSI;
    private String mDeviceAddress;
    private String mDeviceName;
    private String[] mDeviceAddressData = new String[]{"08:7C:BE:96:27:F2", "08:7C:BE:96:27:2D"};

    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;

    private Button send_button;
    private Button Disconnect;
    private Button Connect;

    private long lastSecondBytes = 0;
    private long sendBytes = 0;
    private StringBuilder mData;

    int sendIndex = 0;
    int sendDataLen = 0;
    byte[] sendBuf;

    //蓝牙链接断开 what
    private static final int DISCONNECT = 0;


    private static final int REQUEST_ENABLE_BT = 1;
    //设备扫描时间为10秒
    private static final long SCAN_PERIOD = 5000;

    //管理服务生命周期的代码
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    //处理由服务触发的各种事件
    // ACTION_GATT_CONNECTED: 连接到关贸总协定服务器
    // ACTION_GATT_DISCONNECTED: 与关贸总协定服务器断开
    // ACTION_GATT_SERVICES_DISCOVERED: 发现关贸总协定服务
    // ACTION_DATA_AVAILABLE: 从设备接收到的数据。这可能是阅读的结果。或通知操作。

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                mBluetoothLeService.connect(mDeviceAddress);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //特征值找到才代表连接成功
                mConnected = true;
                updateConnectionState(R.string.connected);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_NO_DISCOVERED.equals(action)) {
                mConnected = true;
                mBluetoothLeService.connect(mDeviceAddress);
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

            } else if (BluetoothLeService.ACTION_WRITE_SUCCESSFUL.equals(action)) {
                if (sendDataLen > 0) {
                    onSendBtnClicked();
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        setContentView(R.layout.main);
        //使用此检查来确定是否在设备上支持。然后可以选择性地禁用与此相关的特性。
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(MainActivity.this, "不支持蓝牙设备", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 初始化蓝牙适配器。API 18级及以上，就可以通过bluetoothmanager蓝牙适配器。
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // 检查设备是否支持蓝牙。
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        //andoird 6.0需要开启定位请求
        mayRequestLocation();
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //初始化按钮
        send_button = (Button) findViewById(R.id.send);
        Disconnect=(Button)findViewById(R.id.disconnect);
        Connect=(Button)findViewById(R.id.connect);

        //断开蓝牙连接
        Disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Disconnect();
                search_device_view.setSearching(false);
                Toast.makeText(MainActivity.this,"成功断开连接设备"+mDeviceName,Toast.LENGTH_SHORT).show();
            }
        });

        search_device_view = (CircleWaveDivergenceView) findViewById(R.id.search_device_view);
        search_device_view.setWillNotDraw(false);

        //产看周围蓝牙设备
        Button SEE=(Button) findViewById(R.id.next);
        SEE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Disconnect();
                Intent intent=new Intent(MainActivity.this,Around.class);
                startActivity(intent);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        // 确保设备上启用了蓝牙。如果当前没有启用蓝牙，则触发一个对话框，显示请求用户授予其启用权限的对话框。
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        Connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //设备扫描
                scanLeDevice(true);
                search_device_view.setSearching(true);
            }
        });

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

    }
    //扫描
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    //设备扫描回调
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDeviceAddress = device.getAddress();
                            mDeviceName = device.getName();
                            mRSSI = String.valueOf(rssi);
                            if (mDeviceAddress!=null){
                                mHandler.post(
                                        new Runnable(){
                                    @Override
                                    public void run() {
                                        for (int i = 0; i < mDeviceAddressData.length; i++) {
                                            if (mDeviceAddressData[i].equals(mDeviceAddress)) {
                                                mBluetoothLeService.connect(mDeviceAddress);
                                                onSendBtn();
                                                Toast.makeText(MainActivity.this,"成功连接"+mDeviceName,
                                                        Toast.LENGTH_SHORT).show();
                                            } else {

                                            }
                                        }
                                    }
                                });
                            }else {
                                Toast.makeText(MainActivity.this,"未扫描的蓝牙设备",Toast.LENGTH_SHORT).show();
                            }

                        }
                    });
                }
            };

    private void onSendBtn() {
       send_button.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
               mHandler.post(new Runnable() {
                   @Override
                   public void run() {
                       getSendBuf();
                       onSendBtnClicked();
                       if (mBluetoothLeService.getRssiVal()) {
                           //获取已经读到的RSSI值
                           mRSSI = String.valueOf(BluetoothLeService.getBLERSSI());
                       }
                       mHandler.postDelayed(this, 2000);
                   }

               });
           }
       });
    }

    private void mayRequestLocation() {
        if (Build.VERSION.SDK_INT >= 23) {
            int checkCallPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (checkCallPhonePermission != PackageManager.PERMISSION_GRANTED) {
                //判断是否需要 向用户解释，为什么要申请该权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION))
                    Toast.makeText(this,R.string.ble_need_location, Toast.LENGTH_LONG).show();

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                return;
            } else {

            }
        } else {

        }
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // mConnectionState.setText(resourceId);
            }
        });
    }

    /**
     * IntentFilter对象负责过滤掉组件无法响应和处理的Intent，只将自己关心的Intent接收进来进行处理。
     * IntentFilter实行“白名单”管理，即只列出组件乐意接受的Intent，但IntentFilter只会过滤隐式Intent，
     * 显式的Intent会直接传送到目标组件。 Android组件可以有一个或多个IntentFilter，每个IntentFilter之间相互独立
     * ，只需要其中一个验证通过则可。除了用于过滤广播的IntentFilter可以在代码中创建外，
     * 其他的IntentFilter必须在AndroidManifest.xml文件中进行声明。
     * @return
     */

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITE_SUCCESSFUL);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_NO_DISCOVERED);
        return intentFilter;
    }

    private void getSendBuf() {
        sendIndex = 0;
        sendBuf = mRSSI.getBytes();
        sendDataLen = sendBuf.length;
        Log.d("hhh", String.valueOf(sendDataLen));
    }

    private void onSendBtnClicked() {
        if (sendDataLen > 20) {
            sendBytes += 20;
            final byte[] buf = new byte[20];
            // System.arraycopy(buffer, 0, tmpBuf, 0, writeLength);
            for (int i = 0; i < 20; i++) {
                buf[i] = sendBuf[sendIndex + i];
            }
            sendIndex += 20;
            mBluetoothLeService.writeData(buf);
            sendDataLen -= 20;
        } else {
            sendBytes += sendDataLen;
            final byte[] buf = new byte[sendDataLen];
            for (int i = 0; i < sendDataLen; i++) {
                buf[i] = sendBuf[sendIndex + i];
            }
            mBluetoothLeService.writeData(buf);
            sendDataLen = 0;
            sendIndex = 0;
        }
    }
    //蓝牙断开连接
    private void Disconnect() {
        mBluetoothLeService.disconnect();

    }


}
























