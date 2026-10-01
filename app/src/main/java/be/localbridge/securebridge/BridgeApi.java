package be.localbridge.securebridge;

import android.webkit.JavascriptInterface;
import org.json.JSONObject;

final class BridgeApi {
    private final MainActivity activity;
    private final SecureStore store;
    BridgeApi(MainActivity activity, SecureStore store){this.activity=activity;this.store=store;}

    @JavascriptInterface public String version(){return "0.5.0-alpha";}
    @JavascriptInterface public boolean isNativeVault(){return true;}
    @JavascriptInterface public String loadKvJson(){try{return store.loadJson();}catch(Exception e){return "{}";}}
    @JavascriptInterface public boolean saveKvJson(String json){try{store.saveJson(json);return true;}catch(Exception e){return false;}}
    @JavascriptInterface public boolean clearKv(){try{store.clearBundle();return true;}catch(Exception e){return false;}}
    @JavascriptInterface public boolean destroyAll(){try{if(!activity.confirmNativeDestruction())return false;store.destroyAll();return true;}catch(Exception e){return false;}}
    @JavascriptInterface public String hmacDevice(String value){try{return store.hmac(value==null?"":value);}catch(Exception e){return "";}}
    @JavascriptInterface public long storedBytes(){return store.storedBytes();}
    @JavascriptInterface public boolean hasBundle(){return store.hasBundle();}
    @JavascriptInterface public void startNfcScan(String requestId){activity.startNfcScan(requestId==null?"":requestId);}
    @JavascriptInterface public void startPairingHost(String requestId,String payloadB64){activity.startPairingHost(requestId==null?"":requestId,payloadB64==null?"":payloadB64);}
    @JavascriptInterface public void startPairingReader(String requestId,String payloadB64){activity.startPairingReader(requestId==null?"":requestId,payloadB64==null?"":payloadB64);}
    @JavascriptInterface public void cancelPairing(){activity.cancelPairing();}
    @JavascriptInterface public void requestNotificationPermission(){activity.requestNotificationPermission();}
    @JavascriptInterface public boolean notificationPermissionGranted(){return activity.notificationPermissionGranted();}
    @JavascriptInterface public void configureBackgroundPolling(String apiUrl,String channelsJson){activity.configureBackgroundPolling(apiUrl==null?"":apiUrl,channelsJson==null?"[]":channelsJson);}
    @JavascriptInterface public void disableBackgroundPolling(){activity.disableBackgroundPolling();}
    @JavascriptInterface public String argon2id(String value,String saltB64,int memoryKb,int iterations,int parallelism,int outLen){try{return CryptoSuite.argon2id(value,saltB64,memoryKb,iterations,parallelism,outLen);}catch(Exception e){return "";}}
    @JavascriptInterface public String x25519KeyPair(){try{return CryptoSuite.x25519KeyPair();}catch(Exception e){return "";}}
    @JavascriptInterface public String x25519Shared(String privateB64,String publicB64){try{return CryptoSuite.x25519Shared(privateB64,publicB64);}catch(Exception e){return "";}}
    @JavascriptInterface public String ed25519KeyPair(){try{return CryptoSuite.ed25519KeyPair();}catch(Exception e){return "";}}
    @JavascriptInterface public String ed25519Sign(String privateB64,String messageB64){try{return CryptoSuite.ed25519Sign(privateB64,messageB64);}catch(Exception e){return "";}}
    @JavascriptInterface public boolean ed25519Verify(String publicB64,String messageB64,String signatureB64){return CryptoSuite.ed25519Verify(publicB64,messageB64,signatureB64);}
    @JavascriptInterface public void closeSecureView(){activity.runOnUiThread(activity::finishAndRemoveTask);}
    @JavascriptInterface public String statusJson(){try{JSONObject j=new JSONObject();j.put("version",version());j.put("native",true);j.put("bytes",store.storedBytes());j.put("hasBundle",store.hasBundle());j.put("backgroundPolling",true);return j.toString();}catch(Exception e){return "{\"native\":true}";}}
}
