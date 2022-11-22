package com.hpl.h264decode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 自定义一个H264播放器
 */
public class H264Player implements Runnable {

    private String path;
    //    解码器
    MediaCodec mediaCodec;

    public H264Player(String path, Surface surface) {
        this.path = path;
        try {
            // 创建解码器的类型，video 开头为视屏解码器，audio开头为音频解码器；第二个是编码名称
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            // 设置参数，然后把这些参数 传到 SDP
            MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc",
                    364, 368);
            // 配置解码器，第二个参数是输出数据的地方
            mediaCodec.configure(mediaformat, surface, null, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        当前支持  硬解H264
        Log.i("hpl", "支持: path=" + path);
    }

    public void play() {
        mediaCodec.start();
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            decodeH264();
        } catch (Exception e) {
            Log.i("hpl", "run: " + e.toString());
        }
    }

    /**
     * 解码h264
     */
    private void decodeH264() {
        byte[] bytes = null;
        try {
            // 读取 文件数据
            bytes = getBytes(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int startIndex = 0;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            // 找到下一帧的起点位置
            int nextFrameStart = findByFrame(bytes, startIndex + 2, bytes.length);
            // 获取容器索引，找到一个可用 的容器；传入超时时间
            int inIndex = mediaCodec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                // 通过索引，拿到 ByteBuffer，后面要做的事情，就是往buffer 里面写数据了
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inIndex);
                //
                int length = nextFrameStart - startIndex;
                Log.i("hpl", "decodeH264: 输入  " + length);
                // 一次 放入一帧的数据
                byteBuffer.put(bytes, startIndex, length);
                // 通知DPS 处理数据，需要告诉DSP 容器的索引 即可；这时候 数据从CPU 传到了DSP
                mediaCodec.queueInputBuffer(inIndex, 0, length, 0, 0);
                startIndex = nextFrameStart;
            }
            // 获取DSP 解码后的数据
            int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
            if (outIndex >= 0) {
                try {
                    // 播放太快了，延迟一下
                    Thread.sleep(33);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //第二个参数 是 true,配置了 Surface 就把数据输出到 Surface
                mediaCodec.releaseOutputBuffer(outIndex, true);
            }
        }

    }

    /**
     * 获取下一帧开始的位置：通过分隔符
     *
     * @param bytes
     * @param start
     * @param totalSize
     * @return
     */
    private int findByFrame(byte[] bytes, int start, int totalSize) {
        for (int i = start; i <= totalSize - 4; i++) {
            if (((bytes[i] == 0x00) && (bytes[i + 1] == 0x00) && (bytes[i + 2] == 0x00) && (bytes[i + 3] == 0x01))
                    || ((bytes[i] == 0x00) && (bytes[i + 1] == 0x00) && (bytes[i + 2] == 0x01))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取一个文件 的字节
     *
     * @param path
     * @return
     * @throws IOException
     */
    public byte[] getBytes(String path) throws IOException {
        InputStream is = new DataInputStream(new FileInputStream(new File(path)));
        int len;
        int size = 1024;
        byte[] buf;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buf = new byte[size];
        while ((len = is.read(buf, 0, size)) != -1)
            bos.write(buf, 0, len);
        buf = bos.toByteArray();
        return buf;
    }
}
