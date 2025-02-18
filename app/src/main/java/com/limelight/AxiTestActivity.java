package com.limelight;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.CombinedVibration;
import android.os.IBinder;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.driver.AbstractController;
import com.limelight.binding.input.driver.UsbDriverListener;
import com.limelight.binding.input.driver.UsbDriverService;
import com.limelight.binding.input.driver.Xbox360Controller;
import com.limelight.binding.input.driver.Xbox360WirelessDongle;
import com.limelight.binding.input.driver.XboxOneController;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.DeviceUtils;
import com.limelight.utils.ShellUtils;
import com.limelight.utils.UiHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 * Date: 2024-05-06
 * Time: 16:11
 */
public class AxiTestActivity extends Activity implements View.OnClickListener {

    private TextView tx_gamepad_info;

    private TextView tx_content;

    private Vibrator vibrator;

    private Button bt_vibrator;

    private Button bt_keyboard;

    private List<InputDevice> ids = new ArrayList<>();

    private Vibrator vibratorOnline;

    private VibratorManager vibratorManager;

    private Button bt_vibrator_value;

    private int simulatedAmplitude=220;

    private SensorManager sm;
    private SensorEventListener accelSensor;
    private SensorEventListener gyroSensor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PreferenceConfiguration.readPreferences(this).uiThemeColorWhite) {
            setTheme(R.style.AppTheme);
        }
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_axitest);
        if (PreferenceConfiguration.readPreferences(this).uiThemeColorWhite) {
            UiHelper.setStatusBarLightMode(getWindow(),true);
        }
        //填充刘海
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        tx_gamepad_info = findViewById(R.id.tx_game_pad_info);
        tx_content=findViewById(R.id.tx_content);
        bt_vibrator=findViewById(R.id.bt_vibrator);

        bt_vibrator_value=findViewById(R.id.bt_vibrator_value);

        bt_keyboard=findViewById(R.id.bt_keyboard);

        vibrator = (Vibrator) this.getSystemService(this.VIBRATOR_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            VibratorManager manager=(VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager.getDefaultVibrator();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        showDeviceInfo();
        boolean hasVibrator=vibrator.hasVibrator();
        String content=hasVibrator?"有震动马达":"无震动马达";
        bt_vibrator.setText("测试设备震动（"+content+"）");

        showSimlateAmp();
        startUsb();
    }

    private void showDeviceInfo(){
        String kernelVersion =System.getProperty("os.version");
        StringBuffer sb=new StringBuffer();
        sb.append("安卓版本："+ DeviceUtils.getSDKVersionName());
        sb.append("\tapi版本："+Build.VERSION.SDK_INT);
        int vibratorInput=getVibratorInput();
        if(vibratorInput!=-1){
            sb.append("\n振动重定向："+(vibratorInput==1?"开启":"关闭"));
        }else{
            sb.append("\n振动重定向：没有发现此参数");
        }
        sb.append("\n内核版本："+kernelVersion);
        sb.append("\n品牌型号："+DeviceUtils.getManufacturer()+"\t-\t"+DeviceUtils.getModel());
        String cpuInfo=DeviceUtils.getCpuInfo();
        if(!TextUtils.isEmpty(cpuInfo)){
            sb.append("\nCPU信息："+cpuInfo);
        }
        sb.append("\n覆盖USB驱动状态："+PreferenceConfiguration.readPreferences(this).bindAllUsb);
        sb.append("\n握把马达反转状态："+PreferenceConfiguration.readPreferences(this).enableFlipRumbleFF);

        int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
        sb.append("\n屏幕方向：");
        switch (deviceRotation) {
            case Surface.ROTATION_0:
                sb.append("0");
                break;
            case Surface.ROTATION_180:
                sb.append("180");
                break;

            case Surface.ROTATION_90:
                sb.append("90");
                break;
            case Surface.ROTATION_270:
                sb.append("270");
                break;
        }
        tx_content.setText(sb.toString());
//        LimeLog.info("axi--"+DeviceUtils.readCpuInfo());
    }

    private void showSimlateAmp(){
        bt_vibrator_value.setText("振幅强度（"+simulatedAmplitude+"）");
    }


    private void cancleRumble(){
        if(vibratorOnline!=null){
            vibratorOnline.cancel();
        }
        if(vibrator!=null){
            vibrator.cancel();
        }

        if(vibratorManager!=null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibratorManager.cancel();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(isTestKeyBoard){
            tx_gamepad_info.setText(testKeyboardTips+"\n"+"scancode:"+event.getScanCode()+",code:"+event.getKeyCode());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isTestKeyBoard;

    private String testKeyboardTips="开始测试按键键值，可以按任意键，退出测试模式请按停止按钮";
    @Override
    public void onClick(View v) {

        if(v.getId()==R.id.bt_keyboard){
            isTestKeyBoard=!isTestKeyBoard;
            if(!isTestKeyBoard){
                bt_keyboard.setText("测试键盘按键");
            }else{
                bt_keyboard.setText("停止测试键盘");
                tx_gamepad_info.setText(testKeyboardTips);
            }
            return;
        }

        if(v.getId()==R.id.bt_vibrator_input){
            String[] titles=new String[]{"设备已root选此项","设备未root选此项"};
            new AlertDialog.Builder(this).setItems(titles, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    switch (which){
                        case 0:
                            changeVibratorInput();
                            break;
                        case 1:
                            new AlertDialog.Builder(AxiTestActivity.this)
                                    .setMessage("开发者选项-USB调试-勾选USB调试（安全设置）执行adb命令\n" +
                                            "\n" +
                                            "查询安卓设置项\n查看是否有vibrate_input_devices参数\n" +
                                            "adb shell settings list system\n" +
                                            "\n" +
                                            "开启震动重定向\n" +
                                            "adb shell settings put system vibrate_input_devices 1\n" +
                                            "\n" +
                                            "关闭震动重定向\n" +
                                            "adb shell settings put system vibrate_input_devices 0")
                                    .setNegativeButton("确认", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setTitle("提示").create().show();
                            break;
                    }
                }
            }).setTitle("请选择").create().show();
            return;
        }

        if(v.getId()==R.id.bt_vibrator_gamepad_usb){

            if(controllerUsbs.isEmpty()){
                Toast.makeText(AxiTestActivity.this, "内置USB驱动不支持此手柄！", Toast.LENGTH_LONG).show();
                return;
            }
            if(!(controllerUsbs.get(0) instanceof XboxOneController)) {
                controllerUsbs.get(0).rumble((short) -256,(short) -256);
                return;
            }
            String[] titles=new String[]{"握把震动","扳机震动"};
            new AlertDialog.Builder(this).setItems(titles, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    switch (which){
                        case 0:
                            controllerUsbs.get(0).rumble((short) -simulatedAmplitude,(short) -simulatedAmplitude);
                            break;
                        case 1:
                            controllerUsbs.get(0).rumbleTriggers((short) -simulatedAmplitude,(short) -simulatedAmplitude);
                            break;
                    }
                }
            }).setTitle("请选择").create().show();
            return;
        }

        if(v.getId()==R.id.bt_vibrator_cancle){
            if(!controllerUsbs.isEmpty()){
                controllerUsbs.get(0).rumble((short) 0,(short) 0);
                controllerUsbs.get(0).rumbleTriggers((short) 0,(short) 0);
            }
            cancleRumble();
            canSensor();
            return;
        }
        //机身震动
        if (v.getId() == R.id.bt_vibrator) {
            String[] titles=new String[]{"简单震一秒","持续HD震动","陀螺仪"};
            new AlertDialog.Builder(this).setItems(titles, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    switch (which){
                        case 0:
                            vibrator.vibrate(1000);
                            break;
                        case 1:
                            rumble(vibrator);
                            break;
                        case 2:
                            sm=(SensorManager) getSystemService(Context.SENSOR_SERVICE);
                            initSensor();
                            break;
                    }
                }
            }).setTitle("请选择").create().show();
            return;
        }

        //手柄震动
        if (v.getId() == R.id.bt_vibrator_gamepad) {
            if(ids.size()==0){
                Toast.makeText(AxiTestActivity.this, "目前没有检测到手柄，请连接手柄，点击刷新按钮，再尝试！", Toast.LENGTH_LONG).show();
                return;
            }
            String[] strings = new String[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                strings[i] = ids.get(i).getName();
            }
            new AlertDialog.Builder(this).setItems(strings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    if (ids.get(which).getVibrator().hasVibrator()) {
                        String[] titles=new String[]{"简单震一秒","持续HD震动","扳机震动","左握把震动","右握把震动","陀螺仪"};
                        new AlertDialog.Builder(AxiTestActivity.this).setItems(titles, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which2) {
                                dialog.dismiss();
                                switch (which2){
                                    case 0:
                                        ids.get(which).getVibrator().vibrate(1000);
                                        break;
                                    case 1:
                                        cancleRumble();
                                        vibratorOnline=ids.get(which).getVibrator();
                                        rumble(vibratorOnline);
                                        break;
                                    case 2:
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            vibratorManager=ids.get(which).getVibratorManager();
                                            if(canRumbleTrgger(vibratorManager)){
                                                rumbleQuadVibrators(vibratorManager,(short) -simulatedAmplitude,(short)-simulatedAmplitude,(short)-simulatedAmplitude,(short)-simulatedAmplitude);
                                            }else{
                                                Toast.makeText(AxiTestActivity.this, "此手柄不支持扳机震动！", Toast.LENGTH_SHORT).show();
                                            }
                                        }else{
                                            Toast.makeText(AxiTestActivity.this, "低于安卓12不支持！", Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    case 3:
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            vibratorManager=ids.get(which).getVibratorManager();
                                            if(hasDualAmplitudeControlledRumbleVibrators(vibratorManager)){
                                                rumbleDualVibrators(vibratorManager,(short) simulatedAmplitude,(short) 0);
                                            }else{
                                                Toast.makeText(AxiTestActivity.this, "不支持振幅控制！", Toast.LENGTH_SHORT).show();
                                            }

                                        }else{
                                            Toast.makeText(AxiTestActivity.this, "低于安卓12不支持！", Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    case 4:
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            vibratorManager=ids.get(which).getVibratorManager();
                                            if(hasDualAmplitudeControlledRumbleVibrators(vibratorManager)){
                                                rumbleDualVibrators(vibratorManager,(short) 0,(short) simulatedAmplitude);
                                            }else{
                                                Toast.makeText(AxiTestActivity.this, "不支持振幅控制！", Toast.LENGTH_SHORT).show();
                                            }
                                        }else{
                                            Toast.makeText(AxiTestActivity.this, "低于安卓12不支持！", Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    case 5:
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ids.get(which).getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                                            sm=ids.get(which).getSensorManager();
                                            initSensor();
                                        }else{
                                            Toast.makeText(AxiTestActivity.this, "手柄没有识别到陀螺仪！", Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                }
                            }
                        }).setTitle("请选择").create().show();
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ids.get(which).getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                        new AlertDialog.Builder(AxiTestActivity.this)
                                .setTitle("请选择")
                                .setItems(new String[]{"陀螺仪"}, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        sm=ids.get(which).getSensorManager();
                                        initSensor();
                                    }
                                })
                                .create()
                                .show();

                    }else{
                        Toast.makeText(AxiTestActivity.this, "手柄没有识别到震动传感器！", Toast.LENGTH_SHORT).show();
                    }

                }
            }).setTitle("请选择").create().show();

            return;
        }
        //刷新手柄信息
        if (v.getId() == R.id.bt_update_gamepad) {
            updateGamePad();
            return;
        }

        if(v.getId()==R.id.bt_vibrator_value){
            SeekBar mSeekBar=new SeekBar(this);
            mSeekBar.setMax(255);
            mSeekBar.setProgress(simulatedAmplitude);
            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    simulatedAmplitude=progress;
                    showSimlateAmp();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            AlertDialog.Builder editDialog = new AlertDialog.Builder(this);
            editDialog.setTitle("设置振幅0-255【HD震动生效】");
            //设置dialog布局
            editDialog.setView(mSeekBar);
            editDialog.create().show();
            return;
        }

//        if(v.getId()==R.id.bt_vibrator_setting){
//            Intent intent=new Intent();
//            intent.setClassName("com.android.settings","com.android.settings.SubSettings");
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent);
//            return;
//        }
    }

    private void canSensor(){
        if(sm==null||gyroSensor==null||accelSensor==null){
            return;
        }
        sm.unregisterListener(accelSensor);
        sm.unregisterListener(gyroSensor);
    }

    private String accelDataString="";
    private String gyroDataString="";

    private void showSensorData(){
        StringBuffer sb=new StringBuffer();
        sb.append("【X,Y,Z轴对应传感器数据索引0,1,2】");
        sb.append("\n");
        sb.append(gyroDataString);
        sb.append("\n");
        sb.append("\n");
        sb.append(accelDataString);
        AxiTestActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tx_gamepad_info.setText(sb.toString());
            }
        });
    }

    private void initSensor(){
        accelSensor=new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                StringBuffer sb=new StringBuffer();
                sb.append("加速度");
                sb.append("\n");
                sb.append(event.sensor.getName());
                sb.append("\n");
                sb.append("【X】: ");
                sb.append(event.values[0]);
                sb.append("\n");
                sb.append("【Y】: ");
                sb.append(event.values[1]);
                sb.append("\n");
                sb.append("【Z】: ");
                sb.append(event.values[2]);
                accelDataString=sb.toString();
                showSensorData();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        gyroSensor=new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                StringBuffer sb=new StringBuffer();
                sb.append("陀螺仪");
                sb.append("\n");
                sb.append(event.sensor.getName());
                sb.append("\n");
                sb.append("【X】: ");
                sb.append(event.values[0]);
                sb.append("\n");
                sb.append("【Y】: ");
                sb.append(event.values[1]);
                sb.append("\n");
                sb.append("【Z】: ");
                sb.append(event.values[2]);
                gyroDataString=sb.toString();
                showSensorData();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        sm.registerListener(accelSensor,sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_NORMAL);

        sm.registerListener(gyroSensor,sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE),SensorManager.SENSOR_DELAY_NORMAL);
    }

    @TargetApi(31)
    private boolean hasDualAmplitudeControlledRumbleVibrators(VibratorManager vm) {
        int[] vibratorIds = vm.getVibratorIds();

        // There must be exactly 2 vibrators on this device
        if (vibratorIds.length != 2) {
            return false;
        }

        // Both vibrators must have amplitude control
        for (int vid : vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false;
            }
        }

        return true;
    }

    private void rumble(Vibrator vibrator){
        long pwmPeriod = 20;
        long onTime = (long)((simulatedAmplitude / 255.0) * pwmPeriod);
        long offTime = pwmPeriod - onTime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes vibrationAttributes = new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                    .build();
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, onTime, offTime}, 0), vibrationAttributes);
        }
        else {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            vibrator.vibrate(new long[]{0, onTime, offTime}, 0, audioAttributes);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(vibratorOnline!=null){
            vibratorOnline.cancel();
        }
        if (connectedToUsbDriverService) {
            stopUsb();
        }
        canSensor();
    }

    private void updateGamePad() {
        ids.clear();
        StringBuffer sb = new StringBuffer();
        sb.append("\n");
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice dev = InputDevice.getDevice(deviceId);
            int sources = dev.getSources();
            if (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                    || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_X) != null &&
                        getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Y) != null) {
                    // This is a gamepad
                    ids.add(dev);
                    //android 12
                    sb.append("名称："+dev.getName());
                    sb.append("\n");
                    sb.append("传感器：");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        String sensor="";
                        if(dev.getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null){
                            sensor+="+陀螺仪\t ";
                        }
                        if (dev.getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                            sensor+="+加速度";
                        }
                        if(sensor.isEmpty()){
                            sb.append("无（没有相关驱动或者手柄不支持）");
                        }else{
                            sb.append(sensor);
                        }
                        sb.append("\n");
                    }else{
                        sb.append("低于android12没有对应API");
                        sb.append("\n");
                    }
                    sb.append("VID_PID："+dev.getVendorId()+"_"+dev.getProductId()
                            +"\t    ["+String.format("%04x", dev.getVendorId())+"_"+String.format("%04x", dev.getProductId())+"]");
                    sb.append("\n");
                    sb.append("震动："+(dev.getVibrator().hasVibrator()?"支持":"不支持"));
                    sb.append("\n");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        sb.append("震动马达数量："+dev.getVibratorManager().getVibratorIds().length);
                    }
                    sb.append("\n");
                    sb.append("详细信息：\n");
                    sb.append(dev.toString());
                    sb.append("\n");
                }

            }
        }

        tx_gamepad_info.setText("手柄数量：" + ids.size() + "\n" + sb.toString());
    }


    private static InputDevice.MotionRange getMotionRangeForJoystickAxis(InputDevice dev, int axis) {
        InputDevice.MotionRange range;

        // First get the axis for SOURCE_JOYSTICK
        range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) {
            // Now try the axis for SOURCE_GAMEPAD
            range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD);
        }

        return range;
    }


    private void startUsb(){
        bindService(new Intent(this, UsbDriverService.class),
                usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
    }

    private void stopUsb(){
        unbindService(usbDriverServiceConnection);
    }

    private List<AbstractController> controllerUsbs=new ArrayList<>();

    private boolean connectedToUsbDriverService = false;

    private ServiceConnection usbDriverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            UsbDriverService.UsbDriverBinder binder = (UsbDriverService.UsbDriverBinder) iBinder;
            binder.setListener(new UsbDriverListener() {
                @Override
                public void reportControllerState(int controllerId, int buttonFlags, float leftStickX, float leftStickY, float rightStickX, float rightStickY, float leftTrigger, float rightTrigger) {
//                    LimeLog.warning("Rumble transfer axixi reportControllerState11111");
                }

                @Override
                public void reportControllerMotion(int controllerId, byte motionType, float motionX, float motionY, float motionZ) {

                }

                @Override
                public void deviceRemoved(AbstractController controller) {
                    LimeLog.warning("Rumble transfer axixi rm:"+controller.getControllerId()+":"+controller.getVendorId()+"、"+controller.getProductId());
                    controllerUsbs.clear();
                }

                @Override
                public void deviceAdded(AbstractController controller) {
                    LimeLog.warning("Rumble transfer axixi add:"+controller.getControllerId()+":"+controller.getVendorId()+"、"+controller.getProductId());
                    controllerUsbs.add(controller);
//                    controller.rumble((short) -256,(short) -255);
                    showControllerInfo();
                }
            });
            binder.setStateListener(new UsbDriverService.UsbDriverStateListener() {
                @Override
                public void onUsbPermissionPromptStarting() {
                    LimeLog.warning("Rumble transfer axixi onUsbPermissionPromptStarting");
                }

                @Override
                public void onUsbPermissionPromptCompleted() {
                    LimeLog.warning("Rumble transfer axixi onUsbPermissionPromptCompleted");
                }

                @Override
                public void onUSBInfo(UsbDevice device) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            StringBuffer sb = new StringBuffer();
                            sb.append("name："+device.getProductName());
                            sb.append("\n");
                            sb.append("vendorId："+device.getVendorId()+"\t["+String.format("%04x", device.getVendorId())+"]");
                            sb.append("\n");
                            sb.append("productId："+device.getProductId()+"\t["+String.format("%04x", device.getProductId())+"]");
                            sb.append("\n");
                            if(device.getInterfaceCount()>0){
                                sb.append("interfaceClass："+device.getInterface(0).getInterfaceClass());
                                sb.append("\n");
                                sb.append("interfaceSubclass："+device.getInterface(0).getInterfaceSubclass());
                                sb.append("\n");
                                sb.append("interfaceProtocol："+device.getInterface(0).getInterfaceProtocol());
                                sb.append("\n");
                            }
                            tx_gamepad_info.setText("内置USB驱动不支持此手柄\n"+sb.toString());
                        }
                    });
                }
            });
            binder.start();
            connectedToUsbDriverService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            connectedToUsbDriverService = false;
        }
    };


    private void showControllerInfo(){
        if(controllerUsbs.isEmpty()){
            return;
        }
        StringBuffer sb = new StringBuffer();
        sb.append("controllerId："+controllerUsbs.get(0).getControllerId());
        sb.append("\n");
        sb.append("vendorId："+controllerUsbs.get(0).getVendorId()+"\t["+String.format("%04x", controllerUsbs.get(0).getVendorId())+"]");
        sb.append("\n");
        sb.append("productId："+controllerUsbs.get(0).getProductId()+"\t["+String.format("%04x", controllerUsbs.get(0).getProductId())+"]");
        sb.append("\n");
        if(controllerUsbs.get(0) instanceof Xbox360Controller){
            sb.append("类型：Xbox360Controller");
        }
        if(controllerUsbs.get(0) instanceof XboxOneController){
            sb.append("类型：XboxOneController");
        }
        if(controllerUsbs.get(0) instanceof Xbox360WirelessDongle){
            sb.append("类型：Xbox360WirelessDongle");
        }
        sb.append("\n");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tx_gamepad_info.setText("内置USB驱动支持此手柄：\n" + sb.toString());
            }
        });
    }


    private boolean canRumbleTrgger(VibratorManager vibratorManager){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasQuadAmplitudeControlledRumbleVibrators(vibratorManager)) {
            return true;
        }
        return false;
    }


    @TargetApi(31)
    private void rumbleQuadVibrators(VibratorManager vm, short lowFreqMotor, short highFreqMotor, short leftTrigger, short rightTrigger) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        highFreqMotor = (short)((highFreqMotor >> 8) & 0xFF);
        lowFreqMotor = (short)((lowFreqMotor >> 8) & 0xFF);
        leftTrigger = (short)((leftTrigger >> 8) & 0xFF);
        rightTrigger = (short)((rightTrigger >> 8) & 0xFF);

        // If they're all zero, we can just call cancel().
        if (lowFreqMotor == 0 && highFreqMotor == 0 && leftTrigger == 0 && rightTrigger == 0) {
            vm.cancel();
            return;
        }

        // This is a guess based upon the behavior of FF_RUMBLE, but untested due to lack of Linux
        // support for trigger rumble!
        int[] vibratorIds = vm.getVibratorIds();
        int[] vibratorAmplitudes = new int[] { highFreqMotor, lowFreqMotor, leftTrigger, rightTrigger };

        CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();

        for (int i = 0; i < vibratorIds.length; i++) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]));
            }
        }

        VibrationAttributes.Builder vibrationAttributes = new VibrationAttributes.Builder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA);
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build());
    }

    // This must only be called if hasDualAmplitudeControlledRumbleVibrators() is true!
    @TargetApi(31)
    private void rumbleDualVibrators(VibratorManager vm, short lowFreqMotor, short highFreqMotor) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
//        highFreqMotor = (short)((highFreqMotor >> 8) & 0xFF);
//        lowFreqMotor = (short)((lowFreqMotor >> 8) & 0xFF);

        // If they're both zero, we can just call cancel().
        if (lowFreqMotor == 0 && highFreqMotor == 0) {
            vm.cancel();
            return;
        }

        // There's no documentation that states that vibrators for FF_RUMBLE input devices will
        // always be enumerated in this order, but it seems consistent between Xbox Series X (USB),
        // PS3 (USB), and PS4 (USB+BT) controllers on Android 12 Beta 3.
        int[] vibratorIds = vm.getVibratorIds();
        int[] vibratorAmplitudes = new int[2];
        if(PreferenceConfiguration.readPreferences(this).enableFlipRumbleFF){
            vibratorAmplitudes[0]=lowFreqMotor;
            vibratorAmplitudes[1]=highFreqMotor;
        }else{
            vibratorAmplitudes[0]=highFreqMotor;
            vibratorAmplitudes[1]=lowFreqMotor;
        }
//        int[] vibratorAmplitudes = new int[] { highFreqMotor, lowFreqMotor };
        CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();

        for (int i = 0; i < vibratorIds.length; i++) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]));
            }
        }

        VibrationAttributes.Builder vibrationAttributes = new VibrationAttributes.Builder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA);
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build());
    }

    @TargetApi(31)
    private boolean hasQuadAmplitudeControlledRumbleVibrators(VibratorManager vm) {
        int[] vibratorIds = vm.getVibratorIds();

        // There must be exactly 4 vibrators on this device
        if (vibratorIds.length != 4) {
            return false;
        }

        // All vibrators must have amplitude control
        for (int vid : vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false;
            }
        }
        return true;
    }

    //获取震动重定向开关
    private int getVibratorInput(){
        try {
            int resultVibrate=Settings.System.getInt(getContentResolver(),"vibrate_input_devices");
            return resultVibrate;
        } catch (Exception e) {
//            throw new RuntimeException(e);
            e.printStackTrace();
        }
        return -1;
    }

    //修改震动重定向
    private void changeVibratorInput(){
        //设置震动重定向 没权限
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (!Settings.System.canWrite(getApplicationContext())) {
//                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
//                intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
//                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                getApplicationContext().startActivity(intent);
//            } else {
//                Settings.System.putInt(getContentResolver(), "vibrate_input_devices", 0);
//            }
//        }
        try {
            int inputVibrate=getVibratorInput();
            ShellUtils.CommandResult result=ShellUtils.execCmd("settings put system vibrate_input_devices "+(inputVibrate==0?"1":"0"),true);
            if(result.result==0){
                showDeviceInfo();
                Toast.makeText(AxiTestActivity.this,"修改成功！",Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(AxiTestActivity.this,"发生错误:"+result.errorMsg,Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
