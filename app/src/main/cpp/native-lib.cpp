#include <jni.h>
#include <string>
#include "gif_lib.h"
#include <android/log.h>

#define LOG_TAG "native_gif"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define  argb(a, r, g, b) ( ((a) & 0xff) << 24 ) | ( ((b) & 0xff) << 16 ) | ( ((g) & 0xff) << 8 ) | ((r) & 0xff)

typedef struct GifBean {
    //记录每一帧的延时（数组来保存）
    int *delays;
    //记录渲染的当前帧索引(逐帧绘制的控制）
    int current_frame;
    //总帧数
    int total_frames;
};

void drawFrame(GifFileType *pType, GifBean *pBean, AndroidBitmapInfo info, void *pVoid);

extern "C"
JNIEXPORT jlong JNICALL
Java_com_sty_ne_nativegif_GifNativeDecoder_loadGifNative(JNIEnv *env, jclass clazz,
                                                         jstring gif_path_) {
    const char *gif_path = env->GetStringUTFChars(gif_path_, 0);
    int error;
    //1.打开GIF文件，获取GifFileType结构体：保存了GIF所有信息
    GifFileType *pGifFileType = DGifOpenFileName(gif_path, &error);
    //2.初始化，拿到GIF的详细信息
    //调用后GIF相关信息就保存到gifFileType中了
    int ret = DGifSlurp(pGifFileType);
    if(ret != GIF_OK) {
        LOGE("DGifSlurp 失败：%d", ret);
    }

    //3.给GifBean分配内存,GifBean结构体用来保存从GifFileType中读取的信息
    GifBean *gifBean = (GifBean *) malloc(sizeof(GifBean));
    //4.清理内存
    memset(gifBean, 0, sizeof(GifBean));

    //5.给延时时间数组分配内存
    gifBean->delays = (int *) malloc(sizeof(int) * (pGifFileType->ImageCount));
    //6.清理内存
    memset(gifBean->delays, 0, sizeof(int) * pGifFileType->ImageCount);

    //在图形控制扩展块中的第5个字节和第6个字节存放的是每帧的延迟时间，单位是1/100秒，二唯一能标识这是一个图形扩展块
    //的是第二个字节，固定值0xF9
    ExtensionBlock *extensionBlock;
    //7.给结构体赋值
    for (int i = 0; i < pGifFileType->ImageCount; ++i) {
        //取出每一帧图像
        SavedImage frame = pGifFileType->SavedImages[i];
        for (int j = 0; j < frame.ExtensionBlockCount; ++j) {
            if (frame.ExtensionBlocks[j].Function == GRAPHICS_EXT_FUNC_CODE) {
                //图形的控制拓展块
                extensionBlock = &frame.ExtensionBlocks[j];
                break;
            }
        }
        if (extensionBlock) {
            //获取当前帧的图形控制拓展块中的延时时间（单位1/100秒 -> 10ms）
            //Bytes本来是存放图形控制块的，签名的标识、标签等都是固定值，不需要保存，所以这里两个字节表示一个int
            //小端模式：数据的高字节保存在内存的高地址中，数据的低字节保存在内存的低地址中->地址的增长顺序与值的增长顺序相同
            //Bytes[1] 低八位
            //Bytes[2] 高八位
            //Bytes[0] 保留字节
            gifBean->delays[i] = (extensionBlock->Bytes[2] << 8 | extensionBlock->Bytes[1]) * 10;
        }
    }
    //总帧数
    gifBean->total_frames = pGifFileType->ImageCount;
    //这里是设置一下tag，相当于给view设置一个tag，后面获取宽高时会用到
    pGifFileType->UserData = gifBean;


    env->ReleaseStringUTFChars(gif_path_, gif_path);

    return (jlong) (pGifFileType);
}

/**
 * 核心代码
 * @param pGifFileType
 * @param gifBean
 * @param info
 * @param pixels
 */
void drawFrame(GifFileType *pGifFileType, GifBean *gifBean, AndroidBitmapInfo info, void *pixels) {
    //获取当前帧
    SavedImage savedImage = pGifFileType->SavedImages[gifBean->current_frame];
    GifImageDesc imageDesc = savedImage.ImageDesc;

    ColorMapObject *pColorMapObject = imageDesc.ColorMap;
    if (NULL == pColorMapObject) {
        pColorMapObject = pGifFileType->SColorMap;
    }
    //先偏移指针
    int *px = (int *) pixels;  //图像首地址
    //frameInfo.Top： y方向偏移量
    px = (int *) ((char *) px + info.stride * imageDesc.Top);
    int *line; //每一行的首地址
    int pointPixelIndex; //像素点的索引值
    GifByteType gifByteType; //颜色索引值
    GifColorType colorType; //颜色类型
    //遍历列
    for (int y = imageDesc.Top; y < imageDesc.Top + imageDesc.Height; ++y) {
        line = px;
        //遍历行
        for (int x = imageDesc.Left; x < imageDesc.Left + imageDesc.Width; ++x) {
            pointPixelIndex = (y - imageDesc.Top) * imageDesc.Width + (x - imageDesc.Left);
            gifByteType = savedImage.RasterBits[pointPixelIndex];
            //根据索引值到颜色列表中查找
            if (NULL != pColorMapObject) {
                //当前帧的像素数据   压缩  lzw算法
                colorType = pColorMapObject->Colors[gifByteType];
                //给每行每个像素赋予颜色
                line[x] = argb(255, colorType.Red, colorType.Green, colorType.Blue);
            }
        }
        px = (int *) ((char *) px + info.stride);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sty_ne_nativegif_GifNativeDecoder_getWidth(JNIEnv *env, jobject thiz, jlong gif_pointer) {
    GifFileType *pGifFileType = (GifFileType *) gif_pointer;
    return pGifFileType->SWidth;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sty_ne_nativegif_GifNativeDecoder_getHeight(JNIEnv *env, jobject thiz, jlong gif_pointer) {
    GifFileType *pGifFileType = (GifFileType *) gif_pointer;
    return pGifFileType->SHeight;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sty_ne_nativegif_GifNativeDecoder_updateFrame(JNIEnv *env, jobject thiz, jobject bitmap,
                                                       jlong gif_pointer) {
    //强转代表GIF的结构体
    GifFileType *pGifFileType = (GifFileType *) gif_pointer;
    GifBean *gifBean = (GifBean *) pGifFileType->UserData;
    //Android中保存Bitmap信息的结构体
    AndroidBitmapInfo info;
    //通过bitmap获取AndroidBitmapInfo
    AndroidBitmap_getInfo(env, bitmap, &info);
    //指向像素缓冲区的指针
    void *pixels;
    //bitmap 转换成缓冲区：byte[]
    //锁住bitmap, 一幅图片是二维数组
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    //偏移指针
    //渲染绘制一帧图像
    drawFrame(pGifFileType, gifBean, info, pixels);
    gifBean->current_frame += 1; //渲染完当前帧+1
    //当绘制到最后一帧时
    if (gifBean->current_frame >= gifBean->total_frames) {
        gifBean->current_frame = 0;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return gifBean->delays[gifBean->current_frame];
}