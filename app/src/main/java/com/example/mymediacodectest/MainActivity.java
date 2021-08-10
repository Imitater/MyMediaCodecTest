package com.example.mymediacodectest;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.mymediacodectest.utils.EventBusUtils;
import com.example.mymediacodectest.utils.EventCode;
import com.example.mymediacodectest.utils.EventMessage;
import com.example.mymediacodectest.utils.NetWorkUtils;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {
    private SocketService socketService;

    private MediaProjectionManager mediaProjectionManager;

    private MediaProjection mediaProjection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //获取权限
        checkPermission();
        Button bt = (Button) findViewById(R.id.bt);
        EventBusUtils.register(this);
        //开始socket和录屏
        bt.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View view) {
                //创建socket服务
                initSocketService();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initSocketService() {
        Intent intent = new Intent(getApplicationContext(), SocketService.class);
        startForegroundService(intent);
        //通过binder 获取service
        /*通过binder拿到service*/
        ServiceConnection sc = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                SocketService.SocketBinder binder = (SocketService.SocketBinder) iBinder;
                socketService = binder.getService();
             }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
             }
        };
        //绑定服务
         bindService(intent, sc, BIND_AUTO_CREATE);
    }


    //获取权限
    private void checkPermission() {
        if (Build.VERSION.SDK_INT>Build.VERSION_CODES.M&&checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
            }
        }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
          if (resultCode!=RESULT_OK&&resultCode!=1)return;
         //开启屏幕录制
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        CodecLiveH265 codecLiveH265 = new CodecLiveH265(mediaProjection,socketService,MainActivity.this);
        codecLiveH265.startLive();
      }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReceiveEvent(EventMessage event) {
         if (event != null) {
            if (event.getCode() == EventCode.EVENT_A) {
                //开始录屏传输
                mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                Intent intent = mediaProjectionManager.createScreenCaptureIntent();
                startActivityForResult(intent,1);
            }
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBusUtils.unregister(this);
    }

}
