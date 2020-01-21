package com.sty.ne.nativegif;

import android.graphics.Bitmap;

public class GifNativeDecoder {
    static {
        System.loadLibrary("native-lib");
    }
    private long gifPointer;

    public GifNativeDecoder(long gifPointer) {
        this.gifPointer = gifPointer;
    }

    public static GifNativeDecoder load(String gifPath) {
        long gifPointer = loadGifNative(gifPath);
        GifNativeDecoder nativeDecoder = new GifNativeDecoder(gifPointer);
        return nativeDecoder;
    }

    //native function
    /**
     * 加载GIF文件
     * @param gifPath
     * @return 就是 FifFileType*
     */
    public static native long loadGifNative(String gifPath);

    public long getGifPointer() {
        return gifPointer;
    }

    public native int getWidth(long gifPointer);
    public native int getHeight(long gifPointer);

    public native int updateFrame(Bitmap bitmap, long gifPointer);
}
