package com.onnu0802.tcp_clientsocket;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Handler;

public class MainActivity extends AppCompatActivity {
    public ImageView powerImage;
    public ImageView rpmImage;
    public TextView rpmText;
    public Button button;
    public ImageView alertImage;
    public TextView alertText;
    private Socket socket;
    // fixme: TAG
    String TAG = "집진기 제어 App";
    String ip = "192.168.1.254";
    String sIp = "";

    final int STATUS_DISCONNECTED = 0;
    final int STATUS_CONNECTED = 1;

    int STATUS_START = 1;
    Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        ConnButton = findViewById(R.id.button1);Button = findViewById(R.id.button2);
        powerImage = findViewById(R.id.power);
        rpmImage = findViewById(R.id.rpm);
        rpmText = findViewById(R.id.rpm_text);
        button = findViewById(R.id.button);
        alertImage = findViewById(R.id.alert_image);
        alertText = findViewById(R.id.alert_text);

//      올바른 WIFI인지 체크
        Wifi();

        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }


        // fixme: 버튼 ClickListener
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"운전상태");
            }
        });

    }

    private void Connect(){
        //      Socket Connect
        Toast.makeText(getApplicationContext(), "Connect 시도", Toast.LENGTH_SHORT).show();
        ConnectThread thread = new ConnectThread(ip);
        thread.start();
    }
    private void WifiCheck(){
        if(!sIp.equals("192.168.1")){
            AlertDialog alertDialog = new AlertDialog.Builder(this).setTitle("집진기 제어 APP")
                    .setMessage("올바른 기기의 WIFI를 잡아주세요.")
                    .create();

            alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    int ipAddress = wifiInfo.getIpAddress();
                    String sIp = String.format("%d.%d.%d",
                            (ipAddress & 0xff),
                            (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff));

                    if(!sIp.equals("192.168.1")){
                        finish();
                        overridePendingTransition(0,0);
                    }else{
                        Connect();
                    }
                }
            });
            alertDialog.show();
        }else{
            //      Socket Connect
            Connect();
        }
    }
    private void Wifi() {
        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        sIp = String.format("%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff));

        if( wifiManager.isWifiEnabled()){
            Log.d(TAG,"ON");
            WifiCheck();
        }else{
            Log.d(TAG, "OFF");
            AlertDialog alertDialog = new AlertDialog.Builder(this).setTitle("집진기 제어 APP")
                    .setMessage("WIFI를 연결해주세요.")
                    .create();

            alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                    if( !(wifiManager.isWifiEnabled())) {
                        finish();
                        overridePendingTransition(0,0);
                    }else{
                        WifiCheck();
                    }
                }
            });
            alertDialog.show();
        }
    }

    class StartThread extends Thread{
        int bytes;
        String Dtmp;
        int dlen;
        public StartThread(){

        }
        public String byteArrayToHex(byte[] a) {
            StringBuilder sb = new StringBuilder();
            for(final byte b: a)
                sb.append(String.format("%02x ", b&0xff));
            return sb.toString();
        }

        public void run(){

            // 데이터 송신

            TimerTask myTask = new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "Test");
                    try {
                       String OutData = "AT+START\n";
//                     STATUS _START IF
                       byte[] data = {0x02, 0x41, 0x10, 0x00, 0x00, 0x03, 0x00};
                       byte bcc = data[0];
                       for (int i = 1; i < 6; i++) {
                           bcc = (byte) (bcc ^ data[i]);
                       }

                       data[6] = bcc;

                       OutputStream output = socket.getOutputStream();
                       output.write(data);
                       Log.d(TAG, "AT+START\\n COMMAND 송신");
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d(TAG, "데이터 송신 오류");
                    }
                };
            };
            timer = new Timer();
            timer.schedule(myTask, 300,300);

            try {
                Log.d(TAG, "데이터 수신 준비");

                //TODO:수신 데이터(프로토콜) 처리
                while (true) {
                    if(socket != null){
                        byte[] buffer = new byte[1024];
                        InputStream input = socket.getInputStream();
                        bytes = input.read(buffer);
                        if ( bytes != -1 ) {
                            Log.d(TAG, "bytes = " + bytes);
                            //바이트 헥사(String)로 바꿔서 Dtmp String에 저장.
                            Dtmp = byteArrayToHex(buffer);
                            Log.d(TAG, Dtmp);
                            Dtmp = Dtmp.substring(0, bytes * 3);
                            Log.d(TAG, Dtmp);

                            //프로토콜 나누기
                            String[] DSplit = Dtmp.split("a5 5a"); // sync(2byte) 0xA5, 0x5A
                            Dtmp = "";
                            for (int i = 1; i < DSplit.length - 1; i++) { // 제일 처음과 끝은 잘림. 데이터 버린다.
                                Dtmp = Dtmp + DSplit[i] + "\n";
                            }
                            dlen = DSplit.length - 2;
                        }
                    }

                }
            }catch(IOException e){
                e.printStackTrace();
                Log.e(TAG,"수신 에러");
            }
        }

    }

    // fixme: Socket Connect.
    class ConnectThread extends Thread {
        String hostname;
        public ConnectThread(String addr) {
            hostname = addr;
        }
        public void run() {
            try { //클라이언트 소켓 생성
                int port = 11000;
                socket = new Socket(hostname, port);
                Log.d(TAG, "Socket 생성, 연결.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InetAddress addr = socket.getInetAddress();
                        String tmp = addr.getHostAddress();
                        Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();
                    }
                });
                StartThread sthread = new StartThread();
                sthread.start();
            } catch (UnknownHostException uhe) { // 소켓 생성 시 전달되는 호스트(www.unknown-host.com)의 IP를 식별할 수 없음.
                Log.e(TAG, " 생성 Error : 호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)");
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error : 호스트의 IP 주소를 식별할 수 없음.(잘못된 주소 값 또는 호스트 이름 사용)", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException ioe) { // 소켓 생성 과정에서 I/O 에러 발생.
                Log.e(TAG, " 생성 Error : 네트워크 응답 없음");
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error : 네트워크 응답 없음", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (SecurityException se) { // security manager에서 허용되지 않은 기능 수행.
                Log.e(TAG, " 생성 Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)");
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error : 보안(Security) 위반에 대해 보안 관리자(Security Manager)에 의해 발생. (프록시(proxy) 접속 거부, 허용되지 않은 함수 호출)", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IllegalArgumentException le) { // 소켓 생성 시 전달되는 포트 번호(65536)이 허용 범위(0~65535)를 벗어남.
                Log.e(TAG, " 생성 Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)");
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), " Error : 메서드에 잘못된 파라미터가 전달되는 경우 발생.(0~65535 범위 밖의 포트 번호 사용, null 프록시(proxy) 전달)", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
    @Override
    protected void onStop() {  //앱 종료시
        super.onStop();
        Log.d(TAG,"test");
        try {
            if(socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

