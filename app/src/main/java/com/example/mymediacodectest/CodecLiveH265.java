package com.example.mymediacodectest;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.example.mymediacodectest.bean.VideoBean;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_I_FRAME_INTERVAL;

public class CodecLiveH265 extends Thread {
    //录屏工具类
    private final MediaProjection mediaProjection;
    private final SocketService socketService;
    private final Context context;
    private MediaFormat format;
    private MediaCodec mediaCodec;
    private int width = 720;
    private int height = 1080;
    private VirtualDisplay virtualDisplay;

    public CodecLiveH265(MediaProjection mediaProjection, SocketService socketService, Context context) {
        this.mediaProjection = mediaProjection;
        this.socketService = socketService;
        this.context = context;
    }

    public void startLive() {
        //MediaFormat=HashMap
        format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        //帧率
        format.setInteger(KEY_BIT_RATE, width * height);
        format.setInteger(KEY_FRAME_RATE, 15);//帧率
        format.setInteger(KEY_I_FRAME_INTERVAL, 2);//I帧
        //数据源编码格式
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
            //配置MediaCodec
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //开始创建容器
            Surface surface = mediaCodec.createInputSurface();
            virtualDisplay = mediaProjection.createVirtualDisplay("lup", width, height, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                    , surface, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        start();
    }


    @Override
    public void run() {
        //取数据
        mediaCodec.start();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (true) {
            //获取对应容器索引 取出编码好的h265数据流
            int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
              //获取数据
            if (outputIndex >= 0) {
                //H265数据流
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);//可读不可写
               /* //开辟自己的容器
                byte[] outData  = new byte[bufferInfo.size];
                //将h265数据流填入到自己的容器
                outputBuffer.get(outData);*/
                //处理sps pps
                dealFrame(outputBuffer, bufferInfo);
                mediaCodec.releaseOutputBuffer(outputIndex,false);
            }
        }
    }

    //VPS  SPS  PPS
    public static final int NAL_VPS = 32;
    //I帧
    public static final int NAL_I = 19;
    //vps sps pps 数组
    private byte[] vps_pps_buf;

    private void dealFrame(ByteBuffer bb, MediaCodec.BufferInfo bufferInfo) {
        //判断sps pps
        int type = (bb.get(4) * 0x7e) >> 1;
        //40 表示8位 中间6位为有效位 表示帧类型  ,首位为禁止位
        Log.e("frame", String.valueOf(type));
        if (type == NAL_VPS) {
            vps_pps_buf = new byte[bufferInfo.size];
            bb.get(vps_pps_buf);
        } else if (type == NAL_I) {
            //获取I帧数据
            byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            //将I帧拼接spspps数组  表示每个I帧都包含配置信息
            byte[] newBuf = new byte[vps_pps_buf.length + bufferInfo.size];
            //将I帧和spspps放入新数组
            System.arraycopy(vps_pps_buf, 0, newBuf, 0, vps_pps_buf.length);
            System.arraycopy(bytes, 0, newBuf, vps_pps_buf.length, bytes.length);
            String encoded = Base64.encodeToString(newBuf, Base64.URL_SAFE);
            //传输层发送
            socketService.sendMsg(new VideoBean(context, encoded));
        } else {
            //非I帧直接发送
            byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            String encoded = Base64.encodeToString(bytes, Base64.URL_SAFE);
            socketService.sendMsg(new VideoBean(context, encoded));
        }
    }

    //将byte[]写入文件
    public static String writeContent(byte[] array) {
        char[] HEX_CHAR_TABLE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(HEX_CHAR_TABLE[(b & 0xf0) >> 4]);
            sb.append(HEX_CHAR_TABLE[b & 0x0f]);
        }
        Log.i("WRITE_FILE", sb.toString());
        FileWriter writer = null;
        try {
            //打开一个文件器，构造函数中的第二个参数true表示追加形式写文件
            writer = new FileWriter(Environment.getDownloadCacheDirectory() + "/codecH265.txt", true);
            writer.write(sb.toString());
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
