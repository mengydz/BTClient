package com.test.BTClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.util.UUID;

public class BTClient extends Activity implements View.OnClickListener {
    private final static int REQUEST_CONNECT_DEVICE = 1;    //宏定义查询设备句柄

    private final static String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";   //SPP服务UUID号

    private InputStream is;    //输入流，用来接收蓝牙数据
    //private TextView text0;    //提示栏解句柄
    private EditText edit0,editHex;    //发送数据输入句柄
    private TextView tv_in;       //接收数据显示句柄
    private ScrollView sv;      //翻页句柄
    private String smsg = "";    //显示用数据缓存
    private String fmsg = "";    //保存用数据缓存

    public String filename=""; //用来保存存储的文件名
    BluetoothDevice _device = null;     //蓝牙设备
    BluetoothSocket _socket = null;      //蓝牙通信socket
    boolean _discoveryFinished = false;
    boolean bRun = true;
    boolean bThread = false;

    private BluetoothAdapter _bluetooth = BluetoothAdapter.getDefaultAdapter();    //获取本地蓝牙适配器，即蓝牙设备

    private boolean receptionHex = false,sendHex = false;
    private CheckBox sendCheckBox,receptionCheckBox;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);   //设置画面为主画面 main.xml

        /* 解决兼容性问题，6.0以上使用新的API*/
        final int MY_PERMISSION_ACCESS_COARSE_LOCATION = 11;
        final int MY_PERMISSION_ACCESS_FINE_LOCATION = 12;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},MY_PERMISSION_ACCESS_COARSE_LOCATION);
                Log.e("11111","ACCESS_COARSE_LOCATION");
            }
            if(this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},MY_PERMISSION_ACCESS_FINE_LOCATION);
                Log.e("11111","ACCESS_FINE_LOCATION");
            }
        }
        ///---------------------------------------------------
        //text0 = (TextView)findViewById(R.id.Text0);  //得到提示栏句柄
        edit0 = (EditText)findViewById(R.id.Edit0);   //得到输入框句柄
        editHex = findViewById(R.id.EditHex);
        sv = (ScrollView)findViewById(R.id.ScrollView01);  //得到翻页句柄
        tv_in = (TextView) findViewById(R.id.in);      //得到数据显示句柄

        sendCheckBox = findViewById(R.id.button_group_send);
        receptionCheckBox = findViewById(R.id.button_group_reception);

        receptionCheckBox.setOnClickListener(this);
        sendCheckBox.setOnClickListener(this);

        //如果打开本地蓝牙设备不成功，提示信息，结束程序
        if (_bluetooth == null){
            Toast.makeText(this, "无法打开手机蓝牙，请确认手机是否有蓝牙功能！", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 设置设备可以被搜索
        new Thread(){
            public void run(){
                if(_bluetooth.isEnabled()==false){
                    _bluetooth.enable();
                }
            }
        }.start();
    }

    //发送按键响应
    public void onSendButtonClicked(View v){
        int i=0;
        int n=0;
        if(_socket==null){
            Toast.makeText(this, "请先连接HC模块", Toast.LENGTH_SHORT).show();
            return;
        }
        if(edit0.getText().length()==0&&!sendHex){
            Toast.makeText(this, "请先输入数据1", Toast.LENGTH_SHORT).show();
            return;
        }

        if(editHex.getText().length()==0&&sendHex){
            Toast.makeText(this, "请先输入数据2", Toast.LENGTH_SHORT).show();
            return;
        }

        try{

            OutputStream os = _socket.getOutputStream();   //蓝牙连接输出流
            byte[] bos ;
            if (!sendHex)
                bos =edit0.getText().toString().getBytes("GB2312");
            else {
                bos = hexString2ByteArray(editHex.getText().toString());
            }
            for(i=0;i<bos.length;i++){
                if(bos[i]==0x0a)n++;
            }
            byte[] bos_new = new byte[bos.length+n];
            n=0;
            for(i=0;i<bos.length;i++){ //手机中换行为0a,将其改为0d 0a后再发送
                if(bos[i]==0x0a){
                    bos_new[n]=0x0d;
                    n++;
                    bos_new[n]=0x0a;
                }else{
                    bos_new[n]=bos[i];
                }
                n++;
            }

            os.write(bos_new);
        }catch(IOException e){
        }
    }

    //接收活动结果，响应startActivityForResult()
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode){
            case REQUEST_CONNECT_DEVICE:     //连接结果，由DeviceListActivity设置返回
                // 响应返回结果
                if (resultCode == Activity.RESULT_OK) {   //连接成功，由DeviceListActivity设置返回
                    // MAC地址，由DeviceListActivity设置返回
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // 得到蓝牙设备句柄
                    _device = _bluetooth.getRemoteDevice(address);

                    // 用服务号得到socket
                    try{
                        _socket = _device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
                    }catch(IOException e){
                        Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                    }
                    //连接socket
                    Button btn = (Button) findViewById(R.id.BtnConnect);
                    try{
                        _socket.connect();
                        Toast.makeText(this, "连接"+_device.getName()+"成功！", Toast.LENGTH_SHORT).show();
                        btn.setText("断开");
                    }catch(IOException e){
                        try{
                            Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                            _socket.close();
                            _socket = null;
                        }catch(IOException ee){
                            Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                        }

                        return;
                    }

                    //打开接收线程
                    try{
                        is = _socket.getInputStream();   //得到蓝牙数据输入流
                    }catch(IOException e){
                        Toast.makeText(this, "接收数据失败！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if(!bThread){
                        initListenerThread();
                        bThread=true;
                        bRun = true;
                    }else{
                        bRun = true;
                    }
                }
                break;
            default:break;
        }
    }

   /* //接收数据线程
    Thread readThread=new Thread(){

        public void run(){
            int num = 0;
            byte[] buffer = new byte[1024];
            byte[] buffer_new = new byte[1024];
            byte[] dataBuffer = new byte[4096];
            int i = 0;
            int n = 0;
            bRun = true;
            //接收线程
            while(true){
                try{
                    while(is.available()==0){
                        while(bRun == false){}
                    }
                    while(true){
                        if(!bThread)//跳出循环
                            return;

                        num = is.read(buffer);         //读入数据
                        n=0;

                        String s0 = new String(buffer,0,num);
                        fmsg+=s0;    //保存收到数据
                        for(i=0;i<num;i++){
                            if((buffer[i] == 0x0d)&&(buffer[i+1]==0x0a)){
                                buffer_new[n] = 0x0a;
                                i++;
                            }else{
                                buffer_new[n] = buffer[i];
                            }
                            n++;
                        }
                        //String s = new String(buffer_new,0,n);
                        System.arraycopy(buffer_new,0,dataBuffer,lengthArray(dataBuffer),lengthArray(buffer_new));
                        buffer_new = clearArray(buffer_new);
                        String str = new String(dataBuffer,0,lengthArray(dataBuffer),"GBK");
                        smsg+=str;   //写入接收缓存
                        Thread.sleep(100);
                        if(is.available()==0)break;  //短时间没有数据才跳出进行显示
                    }
                    //发送显示消息，进行显示刷新
                    dataBuffer=clearArray(dataBuffer);
                    buffer_new=clearArray(buffer_new);
                    handler.sendMessage(handler.obtainMessage());
                }catch(Exception e){
                }
            }
        }
    };*/

    Thread mListenerThread;

    private void initListenerThread(){
        mListenerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] bytes = new byte[1024];
                byte[] dataByte = new byte[2048];
                String str = "";
                int len;
                while (bRun){
                    if (is != null){
                        try {
                            if (is.available()!=0){
                                do {
                                    is.read(bytes);
                                    dataByte = addByteArray(dataByte, bytes);
                                    bytes = clearArray(bytes);
                                    Thread.sleep(100);//短时间内没数据才退出
                                } while (is.available() != 0);
                                //len = lengthArray(dataByte);
                                if (receptionHex)
                                    str = bytesToHexString(getValidArray(dataByte));
                                else
                                    str = new String(dataByte, 0, dataByte.length, "GB2312");//GB2312编码
                                //str = new String(dataByte,0,len,"GBK");
                                log(str);
                                smsg+=str;
                                fmsg+=str;    //保存收到数据
                                handler.sendMessage(handler.obtainMessage());
                                bytes[0] = 0;
                                dataByte[0] = 0;
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        mListenerThread.start();
    }

    //消息处理队列
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            tv_in.setText(smsg);   //显示数据
            sv.scrollTo(0,tv_in.getMeasuredHeight()); //跳至数据最后一页
            return false;
        }
    });

    //关闭程序掉用处理部分
    public void onDestroy(){
        super.onDestroy();
        if(_socket!=null)  //关闭连接socket
            try{
                _socket.close();
                mListenerThread.interrupt();
                mListenerThread = null;
            }catch(Exception e){}
        //	_bluetooth.disable();  //关闭蓝牙服务
    }

    //菜单处理部分
  /* @Override
    public boolean onCreateOptionsMenu(Menu menu) {//建立菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }*/

  /*  @Override
    public boolean onOptionsItemSelected(MenuItem item) { //菜单响应函数
        switch (item.getItemId()) {
        case R.id.scan:
        	if(_bluetooth.isEnabled()==false){
        		Toast.makeText(this, "Open BT......", Toast.LENGTH_LONG).show();
        		return true;
        	}
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.quit:
            finish();
            return true;
        case R.id.clear:
        	smsg="";
        	ls.setText(smsg);
        	return true;
        case R.id.save:
        	Save();
        	return true;
        }
        return false;
    }*/

    //连接按键响应函数
    public void onConnectButtonClicked(View v){

        _bluetooth.enable();
        if(_bluetooth.isEnabled()==false){  //如果蓝牙服务不可用则提示
            Toast.makeText(this, " 打开蓝牙中...", Toast.LENGTH_LONG).show();
            return;
        }

        if(!isOpenGPS(this)){
            AlertDialog.Builder builder = new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
            builder.setTitle("提示")
                    .setMessage("请前往打开手机的位置权限!")
                    .setCancelable(false)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(intent, 10);
                        }
                    }).show();
            return;
        }


        //如未连接设备则打开DeviceListActivity进行设备搜索
        Button btn = (Button) findViewById(R.id.BtnConnect);
        if(_socket==null){
            Intent serverIntent = new Intent(this, DeviceListActivity.class); //跳转程序设置
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);  //设置返回宏定义
        }
        else{
            //关闭连接socket
            try{
                bRun = false;
                mListenerThread.interrupt();
                mListenerThread = null;
                Thread.sleep(2000);

                is.close();
                _socket.close();
                _socket = null;
                bThread = false;

                btn.setText("连接");
            }catch(IOException e){}
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //保存按键响应函数
    public void onSaveButtonClicked(View v){
        Save();
    }

    //清除按键响应函数
    public void onClearButtonClicked(View v){
        smsg="";
        fmsg="";
        tv_in.setText(smsg);
    }

    //退出按键响应函数
    public void onQuitButtonClicked(View v){

        //---安全关闭蓝牙连接再退出，避免报异常----//
        if(_socket!=null){
            //关闭连接socket
            try{
                bRun = false;
                Thread.sleep(2000);

                is.close();
                _socket.close();
                _socket = null;
            }catch(IOException e){}
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        finish();
    }

    //保存功能实现
    private void Save() {
        //显示对话框输入文件名
        LayoutInflater factory = LayoutInflater.from(BTClient.this);  //图层模板生成器句柄
        final View DialogView =  factory.inflate(R.layout.sname, null);  //用sname.xml模板生成视图模板
        new AlertDialog.Builder(BTClient.this)
                .setTitle("文件名")
                .setView(DialogView)   //设置视图模板
                .setPositiveButton("确定",
                        new DialogInterface.OnClickListener() //确定按键响应函数
                        {
                            public void onClick(DialogInterface dialog, int whichButton){
                                EditText text1 = (EditText)DialogView.findViewById(R.id.sname);  //得到文件名输入框句柄
                                filename = text1.getText().toString();  //得到文件名

                                try{
                                    if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){  //如果SD卡已准备好

                                        filename =filename+".txt";   //在文件名末尾加上.txt
                                        File sdCardDir = Environment.getExternalStorageDirectory();  //得到SD卡根目录
                                        File BuildDir = new File(sdCardDir, "/data");   //打开data目录，如不存在则生成
                                        if(BuildDir.exists()==false)BuildDir.mkdirs();
                                        File saveFile =new File(BuildDir, filename);  //新建文件句柄，如已存在仍新建文档
                                        FileOutputStream stream = new FileOutputStream(saveFile);  //打开文件输入流
                                        stream.write(fmsg.getBytes());
                                        stream.close();
                                        Toast.makeText(BTClient.this, "存储成功！\n\r"+saveFile, Toast.LENGTH_LONG).show();
                                    }else{
                                        Toast.makeText(BTClient.this, "没有存储卡！", Toast.LENGTH_LONG).show();
                                    }

                                }catch(IOException e){
                                    return;
                                }



                            }
                        })
                .setNegativeButton("取消",   //取消按键响应函数,直接退出对话框不做任何处理
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        }).show();  //显示对话框
    }

    private int lengthArray(byte[] as){
        int number = 0;
        for (byte a : as) {
            if (a==0){
                break;
            }
            ++number;
        }
        return number;
    }

    private byte[] clearArray(byte[] as){
        for (int i=0;i<as.length;i++){
            as[i] = 0;
        }
        return as;
    }

    private byte[] addByteArray(byte[] byte_1, byte[] byte_2){
        byte[] byte_3 = new byte[byte_1.length];
        int number = 0;
        for (byte b : byte_1) {
            if (b==0){
                break;
            }
            ++number;
        }
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, number, byte_2.length);
        return byte_3;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_group_reception:
                receptionHex = receptionCheckBox.isChecked();
                break;
            case R.id.button_group_send:
                sendHex = sendCheckBox.isChecked();
                initEdit(sendHex);
                break;
        }
    }

    private void initEdit(boolean isHex){
        if (isHex){
            Toast.makeText(this, "只能输入0到F的字符", Toast.LENGTH_SHORT).show();
            edit0.setVisibility(View.GONE);
            editHex.setVisibility(View.VISIBLE);
            editHex.setText("");
            editHex.setFocusable(true);
            editHex.setFocusableInTouchMode(true);
            editHex.requestFocus();
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            editHex.setSelection(editHex.getText().toString().length());
        }else {
            edit0.setVisibility(View.VISIBLE);
            editHex.setVisibility(View.GONE);
            edit0.setText("");
            edit0.setFocusable(true);
            edit0.setFocusableInTouchMode(true);
            edit0.requestFocus();
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            edit0.setSelection(edit0.getText().toString().length());
        }
    }


    /**
     * 将16进制字符串转换为byte[]
     */
    public static byte[] hexString2ByteArray(String bs) {
        if (bs == null) {
            return null;
        }
        int bsLength = bs.length();
        if (bsLength % 2 != 0) {
            bs = "0"+bs;
            bsLength = bs.length();
        }
        byte[] cs = new byte[bsLength / 2];
        String st;
        for (int i = 0; i < bsLength; i = i + 2) {
            st = bs.substring(i, i + 2);
            cs[i / 2] = (byte) Integer.parseInt(st, 16);
        }
        return cs;
    }

    //byte数组转String
    public static String bytesToHexString(byte[] bArray) {
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < bArray.length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        int length = sb.length();
        if (length == 1||length == 0){
            return sb.toString();
        }
        if (length%2==1){
            sb.insert(length-1," ");
            length= length-1;
        }
        for (int i = length;i>0;i=i-2){
            sb.insert(i," ");
        }
        return sb.toString();
    }

    private byte[] getValidArray(byte[] bytes){
        int length = 0;
        int temp = 0;
        boolean isEnd = false;
        for (byte aByte : bytes) {
            if (aByte!=0){
                ++length;
                if (isEnd){
                    isEnd = false;
                    length = length+temp;
                    temp = 0;
                }
            }else {
                isEnd = true;
                ++temp;
            }
        }
        byte[] validArray = new byte[length];
        System.arraycopy(bytes,0,validArray,0,length);
        return validArray;
    }

    //判断GPS是否开启，GPS或者AGPS开启一个就认为是开启的
    public static boolean isOpenGPS(final Context context) {
        LocationManager locationManager
                = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // GPS定位
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 网络服务定位
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps || network) {
            return true;
        } return false;
    }

    private void log(String log){
        Log.d("AppRunBTC",log);
    }

}
