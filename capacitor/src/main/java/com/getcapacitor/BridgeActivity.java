package com.getcapacitor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.orhanobut.logger.BuildConfig;
import com.orhanobut.logger.DiskLogAdapter;
import com.orhanobut.logger.Logger;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebView;
import com.getcapacitor.android.R;
import com.getcapacitor.cordova.MockCordovaInterfaceImpl;
import com.getcapacitor.cordova.MockCordovaWebViewImpl;
import com.getcapacitor.plugin.App;

import org.apache.cordova.ConfigXmlParser;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginManager;

import java.util.ArrayList;
import java.util.List;

public class BridgeActivity extends AppCompatActivity {
  protected Bridge bridge;
  private WebView webView;
  protected MockCordovaInterfaceImpl cordovaInterface;
  protected boolean keepRunning = true;
  private ArrayList<PluginEntry> pluginEntries;
  private PluginManager pluginManager;
  private CordovaPreferences preferences;
  private MockCordovaWebViewImpl mockWebView;

  private int activityDepth = 0;

  private String lastActivityPlugin;

  private List<Class<? extends Plugin>> initialPlugins = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    QbSdk.PreInitCallback cb = new QbSdk.PreInitCallback() {

      @Override
      public void onViewInitFinished(boolean arg0) {
        // TODO Auto-generated method stub
        //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
        Log.d("app", " onViewInitFinished is " + arg0);
      }

      @Override
      public void onCoreInitFinished() {
        // TODO Auto-generated method stub
      }
    };
    //x5内核初始化接口
    QbSdk.initX5Environment(getApplicationContext(),  cb);
	
	  //TODO 添加日志文件
    Logger.addLogAdapter(new DiskLogAdapter(SingleDiskLogAdapter.WriteHandler.getFormatStrategy(getApplicationContext(),"logger")){
      @Override public boolean isLoggable(int priority, String tag) {
        return !BuildConfig.DEBUG;
      }
    });
  }

  protected void init(Bundle savedInstanceState, List<Class<? extends Plugin>> plugins) {
    this.initialPlugins = plugins;

    loadConfig(this.getApplicationContext(),this);

    getApplication().setTheme(getResources().getIdentifier("AppTheme_NoActionBar", "style", getPackageName()));
    setTheme(getResources().getIdentifier("AppTheme_NoActionBar", "style", getPackageName()));
    setTheme(R.style.AppTheme_NoActionBar);


    setContentView(R.layout.bridge_layout_main);

    this.load(savedInstanceState);
  }

  /**
   * Load the WebView and create the Bridge
   */
  protected void load(Bundle savedInstanceState) {
    Log.d(LogUtils.getCoreTag(), "Starting BridgeActivity");

    webView = findViewById(R.id.webview);

    cordovaInterface = new MockCordovaInterfaceImpl(this);
    if (savedInstanceState != null) {
      cordovaInterface.restoreInstanceState(savedInstanceState);
    }

    mockWebView = new MockCordovaWebViewImpl(this.getApplicationContext());
    mockWebView.init(cordovaInterface, pluginEntries, preferences, webView);

    pluginManager = mockWebView.getPluginManager();
    cordovaInterface.onCordovaInit(pluginManager);
    bridge = new Bridge(this, webView, initialPlugins, cordovaInterface, pluginManager, preferences);

    Splash.showOnLaunch(this);

    if (savedInstanceState != null) {
      bridge.restoreInstanceState(savedInstanceState);
    }
    this.keepRunning = preferences.getBoolean("KeepRunning", true);
    this.onNewIntent(getIntent());
  }

  public Bridge getBridge() {
    return this.bridge;
  }

  /**
   * Notify the App plugin that the current state changed
   * @param isActive
   */
  private void fireAppStateChanged(boolean isActive) {
    PluginHandle handle = bridge.getPlugin("App");
    if (handle == null) {
      return;
    }

    App appState = (App) handle.getInstance();
    if (appState != null) {
      appState.fireChange(isActive);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    bridge.saveInstanceState(outState);
  }

  @Override
  public void onStart() {
    super.onStart();

    activityDepth++;

    this.bridge.onStart();
    mockWebView.handleStart();

    Log.d(LogUtils.getCoreTag(), "App started");
  }

  @Override
  public void onRestart() {
    super.onRestart();
    this.bridge.onRestart();
    Log.d(LogUtils.getCoreTag(), "App restarted");
  }

  @Override
  public void onResume() {
    super.onResume();

    fireAppStateChanged(true);

    this.bridge.onResume();

    mockWebView.handleResume(this.keepRunning);

    Log.d(LogUtils.getCoreTag(), "App resumed");
  }

  @Override
  public void onPause() {
    super.onPause();

    this.bridge.onPause();
    if (this.mockWebView != null) {
      boolean keepRunning = this.keepRunning || this.cordovaInterface.getActivityResultCallback() != null;
      this.mockWebView.handlePause(keepRunning);
    }

    Log.d(LogUtils.getCoreTag(), "App paused");
  }

  @Override
  public void onStop() {
    super.onStop();

    activityDepth = Math.max(0, activityDepth - 1);
    if (activityDepth == 0) {
      fireAppStateChanged(false);
    }

    this.bridge.onStop();

    if (mockWebView != null) {
      mockWebView.handleStop();
    }

    Log.d(LogUtils.getCoreTag(), "App stopped");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (this.mockWebView != null) {
      mockWebView.handleDestroy();
    }
    Log.d(LogUtils.getCoreTag(), "App destroyed");
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (webView != null) {
      webView.removeAllViews();
      webView.destroy();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (this.bridge == null) {
      return;
    }

    this.bridge.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (this.bridge == null) {
      return;
    }
    this.bridge.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    if (this.bridge == null || intent == null) {
      return;
    }

    this.bridge.onNewIntent(intent);
    mockWebView.onNewIntent(intent);
  }

  @Override
  public void onBackPressed() {
    if (this.bridge == null) {
      return;
    }

    this.bridge.onBackPressed();
  }

  public void loadConfig(Context context, Activity activity) {
    ConfigXmlParser parser = new ConfigXmlParser();
    parser.parse(context);
    preferences = parser.getPreferences();
    preferences.setPreferencesBundle(activity.getIntent().getExtras());
    pluginEntries = parser.getPluginEntries();
  }
}
