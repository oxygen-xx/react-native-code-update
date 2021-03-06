package wang.wind.rn.codeupdate;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.View;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.JSBundleLoader;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import wang.wind.rn.codeupdate.DownloadUtil;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.params.ClientPNames;
//import okhttp3.internal.huc.DelegatingHttpsURLConnection;

/**
 * Created by wangduo on 2016/12/16.
 */

public class RCTUpdateManager extends ReactContextBaseJavaModule {
    private static final String TAG = "RCTUpdateManager";
    private Thread downLoadThread;
    private ProgressDialog progressDialog;
    private Callback callback;
    private Context mContext;
    private VersionUpdate update;
    private final PackageInfo pInfo;

    private Handler mHandler = null;
    /**
     * {
     * lastVersion: 0,
     * currentVersion: 1.1.1
     * }
     */


    public static final String JS_BUNDLE_LOCAL_FILE = "index.android.bundle";

    private static String APPID = "undefined";
    private static String APPNAME = "undefined";
    private static String CHECK_HOST = "/";
    private static String FILE_BASE_PATH = Environment.getExternalStorageDirectory().toString() + File.separator + APPNAME;
    private static String LAST_JS_BUNDLE_LOCAL_PATH = FILE_BASE_PATH + File.separator + "js_bundle";
    private static String JS_BUNDLE_LOCAL_PATH = FILE_BASE_PATH + File.separator + ".js_bundle";
    private static String APK_SAVED_LOCAL_PATH = FILE_BASE_PATH + File.separator + "download_apk";

    private static final String REACT_APPLICATION_CLASS_NAME = "com.facebook.react.ReactApplication";
    private static final String REACT_NATIVE_HOST_CLASS_NAME = "com.facebook.react.ReactNativeHost";

    private static final String JS_BUNDLE_VERSION = "JS_BUNDLE_VERSION";
    private static final String JS_BUNDLE_VERSION_CODE = "JS_BUNDLE_VERSION_CODE";
    private static final String JS_BUNDLE_PATH = "JS_BUNDLE_PATH";
    private static final String UPDATED_APP_VERSION_CODE = "UPDATED_APP_VERSION";
    private static String PREF_NAME = "creativelocker.pref";
    private static boolean sIsAtLeastGB;

    private static AsyncHttpClient mClient;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            sIsAtLeastGB = true;
        }
    }

    /**
     * 读取application 节点  meta-data 信息
     */
    private static String readMetaDataFromApplication(Application application) {
        try {
            ApplicationInfo appInfo = application.getPackageManager()
                    .getApplicationInfo(application.getPackageName(),
                            PackageManager.GET_META_DATA);
            String updateURI = appInfo.metaData.getString("react.native.update.uri");
            return updateURI;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void init(String appName, String appId, String checkHost, Application application) {
        APPID = appId;
        APPNAME = appName;
        CHECK_HOST = checkHost;
//        FILE_BASE_PATH = Environment.getExternalStorageDirectory().toString() + File.separator + APPNAME;
// NOTE: fix android 10 文件不允许随意创建文件夹
        int version = android.os.Build.VERSION.SDK_INT;
        int android10VersionCode = Build.VERSION_CODES.Q;
        if(version < android10VersionCode){
            FILE_BASE_PATH = Environment.getExternalStorageDirectory().toString() + File.separator + application.getPackageName();
        }else{
            File filePath = application.getApplicationContext().getExternalFilesDir(null);
            if(filePath ==  null){
                filePath = application.getApplicationContext().getExternalFilesDir(null);
                Log.d(TAG, filePath.toString());
            }
            FILE_BASE_PATH = filePath.toString();
        }

        // check FILE_BASE_PATH exist
        File file = new File(FILE_BASE_PATH);
        boolean isFileExist = file.exists();
        if (!isFileExist) {
            file.mkdirs();
        }

        LAST_JS_BUNDLE_LOCAL_PATH = FILE_BASE_PATH + File.separator + "js_bundle";
        JS_BUNDLE_LOCAL_PATH = FILE_BASE_PATH + File.separator + ".js_bundle";
        APK_SAVED_LOCAL_PATH = FILE_BASE_PATH + File.separator + "download_apk";

        File last_bundle_path = new File(LAST_JS_BUNDLE_LOCAL_PATH);
        boolean is_last_bundle_path_exist = last_bundle_path.exists();
        boolean last_bool = true;
        if (!is_last_bundle_path_exist) {
            last_bool = last_bundle_path.mkdirs();
        }

        File bundle_path = new File(JS_BUNDLE_LOCAL_PATH);
        boolean is_bundle_path_exist = bundle_path.exists();
        boolean bundle_bool = true;
        if (!is_bundle_path_exist) {
            bundle_bool = bundle_path.mkdirs();
        }

        File apk_saved_path = new File(APK_SAVED_LOCAL_PATH);
        boolean is_apk_path_exist = bundle_path.exists();
        boolean apk_bool = true;
        if (!is_apk_path_exist) {
            apk_bool = apk_saved_path.mkdirs();
        }

//        Log.d(TAG, "LAST_JS_BUNDLE_LOCAL_PATH:" + LAST_JS_BUNDLE_LOCAL_PATH + "," + "is_exist: " + last_bundle_path.exists() + "," + "create_status:" + last_bool + ";");
//        Log.d(TAG, "JS_BUNDLE_LOCAL_PATH:" + JS_BUNDLE_LOCAL_PATH + "," + "is_exist: " + bundle_path.exists() + "," + "create_status:" + bundle_bool + ";");
//        Log.d(TAG, "APK_SAVED_LOCAL_PATH:" + APK_SAVED_LOCAL_PATH + "," + "is_exist: " + apk_saved_path.exists() + "," + "create_status:" + apk_bool + ";");

        mClient = new AsyncHttpClient();
        mClient.addHeader("Accept-Language", Locale.getDefault().toString());
        mClient.addHeader("Host", checkHost.replace("https://",""));
        mClient.addHeader("Connection", "Keep-Alive");
        mClient.getHttpClient().getParams()
                .setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);;

    }
    public static void initUpdate(String appName, String appId, String checkHost, Application application) {
        init(appName,appId,checkHost,application);
    }

    public RCTUpdateManager(ReactApplicationContext reactContext) {
        super(reactContext);
        mContext = reactContext.getApplicationContext();
        pInfo = getPackageInfo(mContext);
    }

    private Runnable mdownApkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!haveNew()) {
                return;
            }
            File saveFile = null;
            String dirPath = null;
            String saveFileName = null;
            if (update.getUpdateType() == 1) {

                dirPath = APK_SAVED_LOCAL_PATH;
                saveFileName = dirPath + File.separator + update.getVersionCode() + ".apk";

            } else {
//                dirPath =JS_BUNDLE_LOCAL_PATH+File.separator+update.getJsBundleVersionCode();
                dirPath = JS_BUNDLE_LOCAL_PATH;
                if (update.getDownloadUrl().endsWith(".zip")) {
                    saveFileName = dirPath + File.separator + "update.zip";
                } else {
                    saveFileName = dirPath + File.separator + JS_BUNDLE_LOCAL_FILE;
                }


            }
            File file = new File(dirPath);
            if (!file.exists()) {
                file.mkdirs();
            }
            saveFile = new File(saveFileName);
            try {
                String downloadUrl = update.getDownloadUrl();
                // TEST: 测试使用
                // downloadUrl = "https://cdn.yimei360.cn/genecell/app_packages/306/skins_0.3.3_1603939384.apk";

                if(downloadUrl.contains("https")){
                    downloadUpdateFileWithHttps(downloadUrl, saveFile);
                }else{
                    downloadUpdateFile(downloadUrl, saveFile);
                }
            } catch (Exception e) {
                mHandler.sendEmptyMessage(0);
                e.printStackTrace();
            }

        }
    };

    // 取消下载按钮的监听器类
    class CancelDownloadListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // 点击“确定”按钮取消对话框
            dialog.cancel();
            if (downLoadThread != null && !downLoadThread.isInterrupted()) {
                downLoadThread.interrupt();
            }
        }
    }

    private boolean haveNew() {
        return update != null && update.getSuccess() && update.getUpdateType() > 0;
    }

    private void startUpdate() {
        try {
            if (progressDialog == null) {
//            progressDialog = new YProgressDialog(mContext,"下载中...");
                final Activity currentActivity = getCurrentActivity();
                if (currentActivity == null) {
                    // The currentActivity can be null if it is backgrounded / destroyed, so we simply
                    // no-op to prevent any null pointer exceptions.
                    return;
                }
                progressDialog = DialogHelp.getProgressDialog(currentActivity, "下载中...");
                progressDialog.setCancelable(false);
                CharSequence title = "取消下载";
//                progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, title, new CancelDownloadListener());

            }
            if(update.getSlight() != true) {
                progressDialog.show();
            }
            downLoadThread = new Thread(mdownApkRunnable);
            mHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    // TODO Auto-generated method stub
                    super.handleMessage(msg);
//                    AlertDialog.Builder dialog;
                    switch (msg.what) {
                        case 0:
                            //下载失败
                            if(update.getSlight() != true) {
                                progressDialog.hide();
                            }

                            if (callback != null) {
                                callback.invoke();
                                callback = null;
                            }
                        case 1:
                            int rate = msg.arg1;
                            if(update.getSlight() != true) {
                                if (rate < 100) {
                                    progressDialog.setProgress(rate);
                                } else {
                                    // 下载完毕后变换通知形式
                                    progressDialog.hide();
                                }
                            }

//                    mNotificationManager.notify(NOTIFY_ID, mNotification);
                            break;
                        case 2:
                            if (callback != null) {
                                callback.invoke("true");
                                callback = null;
                            }
                            break;
                        //js下载完毕
                        // 取消通知
                        case 3:
                            restartReact(getReactApplicationContext());
//                    mNotificationManager.cancel(NOTIFY_ID);
                            break;

                    }
                }
            };
            downLoadThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public String getName() {
        return "UpdateManager";
    }

    @ReactMethod
    public void checkUpdate(final Callback cb) {
        //隐藏标题栏

        final Context context = getReactApplicationContext();
        Log.d(TAG, "hasInternet:" + hasInternet());
        if (hasInternet()) {

            String version = pInfo.versionName;
            int versionCode = pInfo.versionCode;

            int updatedAppVersionCode = getUpdatedAppVersionCode(mContext);
            if (updatedAppVersionCode != versionCode) {
                setJsBundlePath(null, mContext);
                setJsBundleVersionCode(0, mContext);
                setUpdatedAppVersionCode(versionCode, mContext);
            }
            context.getSystemService(Context.DOWNLOAD_SERVICE);
            AsyncHttpResponseHandler mCheckUpdateHandle = new AsyncHttpResponseHandler() {

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    cb.invoke();
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    try {
                        String jsonString = new String(responseBody, "UTF-8");
                        WritableMap params = null;
                        JSONObject mainObject = new JSONObject(jsonString);
                        if (mainObject != null) {
                            BundleJSONConverter bjc = new BundleJSONConverter();
                            Bundle bundle = bjc.convertToBundle(mainObject);
                            params = Arguments.fromBundle(bundle);
                        }
                        cb.invoke(params);
                    } catch (Exception e) {
                        e.printStackTrace();
                        cb.invoke();

                    }
                }
            };
            checkUpdate(pInfo, mCheckUpdateHandle);
        } else {
//            MainApplication.showToast(R.string.tip_network_error);
            final Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                // The currentActivity can be null if it is backgrounded / destroyed, so we simply
                // no-op to prevent any null pointer exceptions.
                return;
            }
            AlertDialog.Builder dialog = DialogHelp.getMessageDialog(currentActivity, mContext.getString(R.string.tip_network_error));
//            dialog.setTitle();
            dialog.show();

        }

    }

    @ReactMethod
    public void doUpdate(ReadableMap options, final Callback cb) {
        update = new VersionUpdate(options);
        if (update.getUpdateType() == 1) {
            AlertDialog.Builder dialog;
            String changeLog = update.getChangeLog();
            String toastMessage = (update.getChangeLog() == null ? "" : (changeLog));
            final Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                // The currentActivity can be null if it is backgrounded / destroyed, so we simply
                // no-op to prevent any null pointer exceptions.
                return;
            }
            dialog = DialogHelp.getMessageDialog(currentActivity, toastMessage, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    callback = cb;
                    startUpdate();
                }
            });
            dialog.setTitle("发现新版本");
            dialog.setMessage("马上会为您下载并安装。");
            dialog.setCancelable(false);
            dialog.show();
        } else {
            callback = cb;
            startUpdate();
        }


    }

    private void checkUpdate(PackageInfo packageInfo, AsyncHttpResponseHandler mCheckUpdateHandle) {
        int bundleVersionCode = getJsBundleVersionCode(mContext);
        Log.d(TAG, "checkUpdate");
        mClient.get(CHECK_HOST + "/app/checkUpdate?platform=android&app_id=" + APPID +
                "&app_version_code=" + packageInfo.versionCode +
                "&js_version_code=" + bundleVersionCode, mCheckUpdateHandle);
    }

    public void downloadUpdateFileWithHttps(String downloadUrl, File saveFile) throws Exception{
        DownloadUtil.get().download(downloadUrl, saveFile, new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) throws IOException {
                if (downLoadThread != null && !downLoadThread.isInterrupted()) {
                    downLoadThread.interrupt();
                }

                if (update.getUpdateType() == 2) {
                    if (file.getAbsolutePath().endsWith(".zip")) {
                        ZipUtils.unZipFile(file.getAbsolutePath(), JS_BUNDLE_LOCAL_PATH);
                    }
                    setJsBundlePath(JS_BUNDLE_LOCAL_PATH + File.separator + JS_BUNDLE_LOCAL_FILE, mContext);
                    setJsBundleVersionCode(update.getJsBundleVersionCode(), mContext);
                    setUpdatedAppVersionCode(pInfo.versionCode, mContext);
                    mHandler.sendEmptyMessage(2);
                } else {
                    File apkFile = file;
                    if (apkFile.exists()) {
                        setJsBundlePath(null, mContext);
                        setUpdatedAppVersionCode(0, mContext);
                        setJsBundleVersionCode(0, mContext);
                        installAPK(getReactApplicationContext(), apkFile);
                    }

                }
            }

            @Override
            public void onDownloading(long progress, long total) {
                Log.d(TAG,"progress is:"+progress+"%");
//                if ((downloadCount == 0) || (int) (totalSize * 100 / updateTotalSize) - 5 >= downloadCount) {
//                    downloadCount += 5;
//                }
                if (update.getUpdateType() == 1 || update.getUpdateType() == 2) {
                    // 更新进度
                    Message msg = mHandler.obtainMessage();
                    msg.what = 1;
                    msg.arg1 = (int)(progress);
                    mHandler.sendMessage(msg);

                }
            }

            @Override
            public void onDownloadFailed() {
                Log.d(TAG,"下载失败");
                if (callback != null) {
                    callback.invoke();
                    callback = null;
                }
            }
        });
    }

    public long downloadUpdateFile(String downloadUrl, File saveFile) throws Exception {

        int downloadCount = 0;
        int currentSize = 0;
        long totalSize = 0;
        int updateTotalSize = 0;
        HttpURLConnection connection = null;

        InputStream is = null;
        FileOutputStream fos = null;

        try {
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "PacificHttpClient");
            if (currentSize > 0) {
                connection.setRequestProperty("RANGE", "bytes=" + currentSize + "-");
            }
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            updateTotalSize = connection.getContentLength();
            if (connection.getResponseCode() == 404) {
                throw new Exception("fail!");
            }
            is = connection.getInputStream();
            fos = new FileOutputStream(saveFile, false);
            byte buffer[] = new byte[1024];
            int readSize = 0;
            while (!downLoadThread.isInterrupted() && (readSize = is.read(buffer)) > 0) {
                fos.write(buffer, 0, readSize);
                totalSize += readSize;
                // 为了防止频繁的通知导致应用吃紧，百分比增加10才通知一次
                if ((downloadCount == 0) || (int) (totalSize * 100 / updateTotalSize) - 5 >= downloadCount) {
                    downloadCount += 5;
                    if (update.getUpdateType() == 1 || update.getUpdateType() == 2) {
                        // 更新进度
                        Message msg = mHandler.obtainMessage();
                        msg.what = 1;
                        msg.arg1 = downloadCount;
                        mHandler.sendMessage(msg);

                    }
                }
            }
            if (downLoadThread.isInterrupted()) {
                is.close();
                fos.close();
                saveFile.delete();
                if (callback != null) {
                    callback.invoke();
                    callback = null;
                }
                return 0l;
            }
            if (downLoadThread != null && !downLoadThread.isInterrupted()) {
                downLoadThread.interrupt();
            }

            if (update.getUpdateType() == 2) {
                if (saveFile.getAbsolutePath().endsWith(".zip")) {
                    ZipUtils.unZipFile(saveFile.getAbsolutePath(), JS_BUNDLE_LOCAL_PATH);
                }
                setJsBundlePath(JS_BUNDLE_LOCAL_PATH + File.separator + JS_BUNDLE_LOCAL_FILE, mContext);
                setJsBundleVersionCode(update.getJsBundleVersionCode(), mContext);
                setUpdatedAppVersionCode(pInfo.versionCode, mContext);
                mHandler.sendEmptyMessage(2);
            } else {
                File apkFile = saveFile;
                if (apkFile.exists()) {
                    setJsBundlePath(null, mContext);
                    setUpdatedAppVersionCode(0, mContext);
                    setJsBundleVersionCode(0, mContext);
                    installAPK(getReactApplicationContext(), apkFile);
                }

            }
            if (connection != null) {
                connection.disconnect();
            }
        } catch(Exception exception){
            exception.printStackTrace();
            if (connection != null) {
                connection.disconnect();
            }
            throw exception;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (is != null) {
                is.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
        return totalSize;
    }

    @ReactMethod
    public void askForReload() {
        mHandler.sendEmptyMessage(3);
//        final AlertDialog.Builder dialog = DialogHelp.getConfirmDialog(getCurrentActivity(), "自动更新已经完成是否重新启动应用?", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialogInterface, int i) {
//                mHandler.sendEmptyMessage(3);
//            }
//        });
//        dialog.setTitle("自动更新完成");
//        dialog.show();
    }


    private void restartReact(ReactContext context) {
        if (context == null) {
            restart(context);
            return;
        }
        loadBundle();
    }

    public static void restart(Context context) {
        Intent i = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(i);
    }


    private void loadBundleLegacy() {
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            // The currentActivity can be null if it is backgrounded / destroyed, so we simply
            // no-op to prevent any null pointer exceptions.
            return;
        }
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentActivity.recreate();
            }
        });
    }

    // Use reflection to find and set the appropriate fields on ReactInstanceManager. See #556 for a proposal for a less brittle way
    // to approach this.
    private void setJSBundle(ReactInstanceManager instanceManager, String latestJSBundleFile) throws IllegalAccessException {
        try {
            JSBundleLoader latestJSBundleLoader;
            if (latestJSBundleFile.toLowerCase().startsWith("assets://")) {
                latestJSBundleLoader = JSBundleLoader.createAssetLoader(getReactApplicationContext(), latestJSBundleFile, false);
            } else {
                latestJSBundleLoader = JSBundleLoader.createFileLoader(latestJSBundleFile);
            }

            Field bundleLoaderField = instanceManager.getClass().getDeclaredField("mBundleLoader");
            bundleLoaderField.setAccessible(true);
            bundleLoaderField.set(instanceManager, latestJSBundleLoader);
        } catch (Exception e) {
            throw new IllegalAccessException("Could not setJSBundle");
        }
    }

    private void loadBundle() {
        try {
            // #1) Get the ReactInstanceManager instance, which is what includes the
            //     logic to reload the current React context.
            final ReactInstanceManager instanceManager = resolveInstanceManager();
            if (instanceManager == null) {
                return;
            }

            String latestJSBundleFile = getJsBundlePath(getReactApplicationContext());

            // #2) Update the locally stored JS bundle file path
            setJSBundle(instanceManager, latestJSBundleFile);

            // #3) Get the context creation method and fire it on the UI thread (which RN enforces)
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // We don't need to resetReactRootViews anymore
                        // due the issue https://github.com/facebook/react-native/issues/14533
                        // has been fixed in RN 0.46.0
                        //resetReactRootViews(instanceManager);

                        instanceManager.recreateReactContextInBackground();
                    } catch (Exception e) {
                        // The recreation method threw an unknown exception
                        // so just simply fallback to restarting the Activity (if it exists)
                        loadBundleLegacy();
                    }
                }
            });

        } catch (Exception e) {
            // Our reflection logic failed somewhere
            // so fall back to restarting the Activity (if it exists)
            loadBundleLegacy();
        }
    }
    // Use reflection to find the ReactInstanceManager. See #556 for a proposal for a less brittle way to approach this.
    private ReactInstanceManager resolveInstanceManager() throws NoSuchFieldException, IllegalAccessException {
        ReactInstanceManager instanceManager = RCTUpdatePackage.getReactInstanceManager();
        if (instanceManager != null) {
            return instanceManager;
        }

        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            return null;
        }

        ReactApplication reactApplication = (ReactApplication) currentActivity.getApplication();
        instanceManager = reactApplication.getReactNativeHost().getReactInstanceManager();
        RCTUpdatePackage.setReactInstanceManager(instanceManager);


        return instanceManager;
    }
    // This workaround has been implemented in order to fix https://github.com/facebook/react-native/issues/14533
    // resetReactRootViews allows to call recreateReactContextInBackground without any exceptions
    // This fix also relates to https://github.com/Microsoft/react-native-code-push/issues/878
    private void resetReactRootViews(ReactInstanceManager instanceManager) throws NoSuchFieldException, IllegalAccessException {
        Field mAttachedRootViewsField = instanceManager.getClass().getDeclaredField("mAttachedRootViews");
        mAttachedRootViewsField.setAccessible(true);
        List<ReactRootView> mAttachedRootViews = (List<ReactRootView>)mAttachedRootViewsField.get(instanceManager);
        for (ReactRootView reactRootView : mAttachedRootViews) {
            reactRootView.removeAllViews();
            reactRootView.setId(View.NO_ID);
        }
        mAttachedRootViewsField.set(instanceManager, mAttachedRootViews);
    }
    private Class tryGetClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private boolean hasInternet() {
        boolean flag;
        if (((ConnectivityManager) getReactApplicationContext().getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null)
            flag = true;
        else
            flag = false;
        return flag;
    }

    private void installAPK(Context context, File file) {
        if (file == null || !file.exists())
            return;
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_VIEW);

        //判读版本是否在7.0以上
        if (Build.VERSION.SDK_INT >= 24) {
            //provider authorities
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName()+".fileprovider", file);
            //Granting Temporary Permissions to a URI
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(file),
                    "application/vnd.android.package-archive");
        }

        context.startActivity(intent);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static SharedPreferences getPreferences(Context context) {
        SharedPreferences pre = context.getSharedPreferences(PREF_NAME,
                Context.MODE_MULTI_PROCESS);
        return pre;
    }

    public static String getJsBundlePath(Context context) {
        PackageInfo pInfo = getPackageInfo(context);
        String version = pInfo.versionName;
        int versionCode = pInfo.versionCode;

        int updatedAppVersionCode = getUpdatedAppVersionCode(context);
        if (updatedAppVersionCode != versionCode) {
            setJsBundlePath(null, context);
            setJsBundleVersionCode(0, context);
            setUpdatedAppVersionCode(versionCode, context);
            return null;
        }
        return getPreferences(context).getString(
                JS_BUNDLE_PATH, null);
    }

    public static void setJsBundlePath(String bundlePath, Context context) {
        Log.d(TAG, "====================setJsBundlePath:" + bundlePath);
        if (bundlePath == null) {
            Log.d(TAG, "====================delete old file begin");
            try {
                File temp = new File(JS_BUNDLE_LOCAL_PATH);
                if (temp.exists() && temp.isDirectory()) {
                    deleteDir(temp);
                }
                File temp2 = new File(LAST_JS_BUNDLE_LOCAL_PATH);
                if (temp2.exists() && temp2.isDirectory()) {
                    deleteDir(temp2);
                }
            } catch (Exception e) {
                Log.d(TAG, "delete old file failed" + e.getMessage());
            }
            Log.d(TAG, "====================delete old file end");
        }
        set(JS_BUNDLE_PATH, bundlePath, context);
    }


    public static int getJsBundleVersionCode(Context context) {
        return getPreferences(context).getInt(
                JS_BUNDLE_VERSION_CODE, 0);
    }

    public static void setJsBundleVersionCode(int bundleVersionCode, Context context) {
        set(
                JS_BUNDLE_VERSION_CODE, bundleVersionCode, context);
    }

    public static int getUpdatedAppVersionCode(Context context) {
        return getPreferences(context).getInt(
                UPDATED_APP_VERSION_CODE, 0);
    }

    public static void setUpdatedAppVersionCode(int updatedAppVersionCode, Context context) {
        set(
                UPDATED_APP_VERSION_CODE, updatedAppVersionCode, context);
    }

    /**
     * 获取App安装包信息
     *
     * @return
     */
    public static PackageInfo getPackageInfo(Context context) {
        PackageInfo info = null;
        try {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace(System.err);
        }
        if (info == null)
            info = new PackageInfo();
        return info;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static void apply(SharedPreferences.Editor editor) {
        if (sIsAtLeastGB) {
            editor.apply();
        } else {
            editor.commit();
        }
    }

    public static void set(String key, int value, Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putInt(key, value);
        apply(editor);
    }

    public static void set(String key, boolean value, Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putBoolean(key, value);
        apply(editor);
    }

    public static void set(String key, String value, Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(key, value);
        apply(editor);
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            //递归删除目录中的子目录下
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // 目录此时为空，可以删除
        return dir.delete();
    }

}
