package com.szzaq.jointdefence.utils;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import com.szzaq.jointdefence.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

/**
 * 用于版本更新的工具类
 */
public class UpdateManager {
    private static final int DOWNLOAD = 1;
    private static final int DOWNLOAD_FINISH = 2;
    HashMap<String, Object> mHashMap;
    private String mSavePath;
    private int progress;
    private boolean cancelUpdate = false;
    private Context mContext;
    private ProgressBar mProgress;
    private Dialog mDownloadDialog;


    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DOWNLOAD:
                    mProgress.setProgress(progress);
                    break;
                case DOWNLOAD_FINISH:
                    installApk();
                    break;
                default:
                    break;
            }
        }

    };

    public UpdateManager(Context context, HashMap<String, Object> map) {
        this.mContext = context;
        this.mHashMap = map;
    }

    /**
     * 检测软件更新
     */
    public void checkUpdate(int versionCode) {
        if (isUpdate(versionCode)) {
            showNoticeDialog();
        }/* else {
            //Toast.makeText(mContext, "没有新版本", Toast.LENGTH_LONG).show();
        }*/
    }

    /**
     * 检查软件是否有更新版本
     *
     * @return
     */
    private boolean isUpdate(int serviceCode) {
        int versionCode = getVersionCode(mContext);
        return serviceCode > versionCode;
    }

    /**
     * 获取软件版本号
     *
     * @param context
     */
    private int getVersionCode(Context context) {
        int versionCode = 0;
        try {
            versionCode = context.getPackageManager().getPackageInfo("com.szzaq.jointdefence", 0).versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    /**
     * 显示软件更新对话框
     */
    private void showNoticeDialog() {

        AlertDialog.Builder builder = new Builder(mContext);
        builder.setTitle("软件升级  ");
        String message = "有新版本，请尽快升级！";
        message += "\n" + mHashMap.get("desc").toString();
        builder.setMessage(message);
        // 更新
        builder.setPositiveButton("升级", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showDownloadDialog();
            }
        });

        builder.setNegativeButton("稍后升级", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        Dialog noticeDialog = builder.create();
        try {
            noticeDialog.show();
        } catch (Exception ignored) {

        }
    }

    /**
     * 显示软件下载对话框
     */
    private void showDownloadDialog() {
        // 构造软件下载对话框
        AlertDialog.Builder builder = new Builder(mContext);
        builder.setTitle("软件下载");
        // 给下载对话框增加进度条
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        View v = inflater.inflate( R.layout.softupdate_progress, null);
        mProgress = (ProgressBar) v.findViewById( R.id.update_progress);
        builder.setView(v);
                // 取消更新。
                builder.setNegativeButton("取消", new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        // 设置取消状态
                        cancelUpdate = true;
            }
        });
        mDownloadDialog = builder.create();
        mDownloadDialog.show();
        // 下载文件
        downloadApk();
    }

    /**
     * 下载apk文件
     */
    private void downloadApk() {
        // 启动新线程下载软件
        new downloadApkThread().start();
    }

    /**
     * 下载文件线程
     */
    private class downloadApkThread extends Thread {
        @Override
        public void run() {
            try {
                // 判断SD卡是否存在，并且是否具有读写权限
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    // 获得存储卡的路径
                    String sdpath = Environment.getExternalStorageDirectory() + "/";
                    mSavePath = sdpath + "download";
                    URL url = new URL(mHashMap.get("url").toString());
                    // 创建连接
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.connect();
                    // 获取文件大小
                    int length = conn.getContentLength();
                    // 创建输入流
                    InputStream is = conn.getInputStream();

                    File file = new File(mSavePath);
                    // 判断文件目录是否存在
                    if (!file.exists()) {
                        file.mkdir();
                    }
                    File apkFile = new File(mSavePath, "fpplus" + mHashMap.get("versionCode").toString() + ".apk");
                    FileOutputStream fos = new FileOutputStream(apkFile);
                    int count = 0;
                    // 缓存
                    byte buf[] = new byte[1024];
                    // 写入到文件中
                    do {
                        int numread = is.read(buf);
                        count += numread;
                        // 计算进度条位置
                        progress = (int) (((float) count / length) * 100);
                        // 更新进度
                        mHandler.sendEmptyMessage(DOWNLOAD);
                        if (numread <= 0) {
                            // 下载完成
                            mHandler.sendEmptyMessage(DOWNLOAD_FINISH);
                            break;
                        }
                        // 写入文件
                        fos.write(buf, 0, numread);
                    } while (!cancelUpdate);// 点击取消就停止下载.
                    fos.close();
                    is.close();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 取消下载对话框显示
            mDownloadDialog.dismiss();
        }
    }

    /**
     * 安装APK文件
     */
    private void installApk() {
        File apkfile = new File(mSavePath, "fpplus" + mHashMap.get("versionCode").toString() + ".apk");
        if (!apkfile.exists()) {
            return;
        }
        // 通过Intent安装APK文件
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse("file://" + apkfile.toString()), "application/vnd.android.package-archive");
        mContext.startActivity(i);
    }
}