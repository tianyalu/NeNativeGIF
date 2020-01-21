package com.sty.ne.nativegif;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final String BASE_FILE_DIR = Environment.getExternalStorageDirectory() + File.separator
            + "sty" + File.separator + "demo.gif";
    private Button btnGlideLoadGif;
    private Button btnJavaLoadGif;
    private Button btnNativeLoadGif;
    private ImageView ivImage;
    private Bitmap bitmap;
    private GifNativeDecoder gifNativeDecoder;
    private GifJavaDecoder gifJavaDecoder;
    private JavaLoadTask mJavaLoadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        requestPermission();
    }

    private void initView() {
        btnGlideLoadGif = findViewById(R.id.btn_glide_load_gif);
        btnJavaLoadGif = findViewById(R.id.btn_java_load_gif);
        btnNativeLoadGif = findViewById(R.id.btn_native_load_gif);
        ivImage = findViewById(R.id.iv_image);

        //内存：63.6M->141.7M (78.1M)->0.75S
        btnGlideLoadGif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Glide.with(MainActivity.this).load(BASE_FILE_DIR).into(ivImage);
            }
        });
        //内存：72.3M->273M (200.7M)->12S
        btnJavaLoadGif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                stopNativeGifPlay();
                javaLoadGif();
            }
        });
        //内存：60.9M->127.2M (66.3M)->2.505S
        btnNativeLoadGif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mJavaLoadTask != null) {
                    mJavaLoadTask.stopTask();
                }
                nativeLoadGif();
            }
        });
    }

    public void javaLoadGif() {
        InputStream is = null;
        try {
            is = new FileInputStream(new File(BASE_FILE_DIR));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        gifJavaDecoder = new GifJavaDecoder();
        int ret = gifJavaDecoder.read(is);
        if(ret == GifJavaDecoder.STATUS_OK) {
            Log.i(TAG, "gif 文件读取成功！");
            mJavaLoadTask = new JavaLoadTask(ivImage, gifJavaDecoder.getFrames());
            mJavaLoadTask.startTask();
            new Thread(mJavaLoadTask).start();
        }else if(ret == GifJavaDecoder.STATUS_FORMAT_ERROR) {
            Log.e(TAG, "gif 文件格式错误！");
        } else {
            Log.e(TAG, "gif 文件读取失败！请检查文件是否存或者确认是否添加SD卡读写权限");
        }
    }

    private class JavaLoadTask implements Runnable {
        int i = 0;
        ImageView iv;
        GifJavaDecoder.GifFrame[] frames;
        int frameLen, oncePlayTime = 0;

        public JavaLoadTask(ImageView iv, GifJavaDecoder.GifFrame[] frames) {
            this.iv = iv;
            this.frames = frames;

            int n = 0;
            frameLen = frames.length;
            while (n < frameLen) {
                oncePlayTime += frames[n].delay;
                n++;
            }
//            Log.i(TAG, "playTime = " + oncePlayTime);
        }

        Handler h2 = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 2:
                        iv.setImageBitmap((Bitmap) msg.obj);
                        break;
                    default:
                        break;
                }
            }
        };

        @Override
        public void run() {
            if(!frames[i].image.isRecycled()) {
                Message m = Message.obtain(h2, 2, frames[i].image);
                m.sendToTarget();
            }
            iv.postDelayed(this, frames[i++].delay);
            i %= frameLen;
        }

        public void startTask() {
            iv.post(this);
        }

        public void stopTask() {
            if(null != iv) {
                iv.removeCallbacks(this);
            }
//            iv = null;
            if(null != frames) {
                for (GifJavaDecoder.GifFrame frame : frames) {
                    if(frame.image != null && !frame.image.isRecycled()) {
                        frame.image.recycle();
                        frame.image = null;
                    }
                }
                frames = null;
            }
        }
    }


    public void nativeLoadGif() {
        new NativeLoadTask().execute();
    }

    class NativeLoadTask extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setTitle("native加载GIF图片中...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            //加载GIF图像
            File file = new File(BASE_FILE_DIR);
            gifNativeDecoder = GifNativeDecoder.load(file.getAbsolutePath());

            int width = gifNativeDecoder.getWidth(gifNativeDecoder.getGifPointer());
            int height = gifNativeDecoder.getHeight(gifNativeDecoder.getGifPointer());
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            //加载到GIF文件信息了
            //渲染图像 delay time
            int nextFrameRenderTime = gifNativeDecoder.updateFrame(bitmap, gifNativeDecoder.getGifPointer());
            mHandler.sendEmptyMessageDelayed(1, nextFrameRenderTime);
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            ivImage.setImageBitmap(bitmap);
            int nextFrameRenderTime = gifNativeDecoder.updateFrame(bitmap, gifNativeDecoder.getGifPointer());
            mHandler.sendEmptyMessageDelayed(1, nextFrameRenderTime);
        }
    };

    private void stopNativeGifPlay() {
        if(mHandler != null) {
            mHandler.removeMessages(1);
        }
    }


    private void requestPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Log.i("sty", "onRequestPermissionResult granted");
                }else {
                    Log.i("sty", "onRequestPermissionResult denied");
                    showWarningDialog();
                }
                break;
            }
            default:
                break;
        }
    }

    private void showWarningDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("警告")
                .setMessage("请前往设置->应用—>PermissionDemo->权限中打开相关权限，否则功能无法正常使用！")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        //finish();
                    }
                }).show();
    }

}
