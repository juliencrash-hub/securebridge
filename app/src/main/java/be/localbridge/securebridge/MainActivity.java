package be.localbridge.securebridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private static final String PREFS = "bridge_prefs";
    private static final String PREF_ORIGIN = "bound_origin";
    private static final long NFC_TIMEOUT_MS = 30_000L;
    private static final long PAIR_TIMEOUT_MS = 60_000L;
    private static final int PAIR_CHUNK = 180;
    private enum ReaderMode { NONE, PRIVATE_TAG, PAIR_READER }

    private WebView webView;
    private SecureStore store;
    private NfcAdapter nfcAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingNfcRequest;
    private Runnable nfcTimeout;
    private String boundOrigin;
    private ReaderMode readerMode = ReaderMode.NONE;
    private String pendingPairRequest;
    private byte[] pendingPairPayload;
    private Runnable pairTimeout;
    private CardEmulation cardEmulation;
    private ComponentName hceComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new SecureStore(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boundOrigin = prefs.getString(PREF_ORIGIN, null);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !"securebridge".equalsIgnoreCase(uri.getScheme())) {
            if (boundOrigin != null) openSecureWebView(boundOrigin); else showNotBoundAndFinish();
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ("bind".equals(host)) bindOrigin(uri.getQueryParameter("origin"));
        else if ("open".equals(host)) { if (boundOrigin == null) showNotBoundAndFinish(); else openSecureWebView(boundOrigin); }
        else finish();
    }

    private void bindOrigin(String candidate) {
        if (!SecureBridgeConfig.isConfigured()) { showNotBoundAndFinish(); return; }
        String normalized = SecureBridgeConfig.isAllowedWebCandidate(candidate) ? SecureBridgeConfig.webBaseUrl() : null;
        if (normalized == null) {
            new AlertDialog.Builder(this).setTitle("Adresse invalide").setMessage("SecureBridge accepte uniquement une adresse HTTPS valide.").setPositiveButton("Fermer", (d,w)->finish()).show();
            return;
        }
        if (boundOrigin != null && !boundOrigin.equals(normalized)) {
            new AlertDialog.Builder(this).setTitle("SecureBridge déjà associé").setMessage("Ce composant est déjà lié à un autre site. Effacer les données de SecureBridge détruit également le coffre natif.").setPositiveButton("Fermer", (d,w)->finish()).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Associer ce site ?").setMessage("SecureBridge va être lié à :\n\n"+normalized+"\n\nCette association empêche un autre site d'utiliser le coffre natif.").setNegativeButton("Annuler",(d,w)->finish()).setPositiveButton("Associer",(d,w)->{boundOrigin=normalized;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PREF_ORIGIN,normalized).apply();openSecureWebView(normalized);}).setCancelable(false).show();
    }

    private String normalizeHttpsBase(String value) {
        if (value == null || value.length() > 2048) return null;
        try {
            URI u = new URI(value);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) return null;
            String path = u.getPath() == null ? "/" : u.getPath();
            if (!path.endsWith("/")) { int lastSlash = path.lastIndexOf('/'); path = lastSlash >= 0 ? path.substring(0,lastSlash+1) : "/"; }
            int port = u.getPort();
            String authority = u.getHost().toLowerCase(Locale.ROOT) + (port > 0 ? ":"+port : "");
            return "https://" + authority + path;
        } catch (Exception e) { return null; }
    }

    private boolean isAllowed(Uri uri) {
        return boundOrigin != null
                && SecureBridgeConfig.webBaseUrl().equals(boundOrigin)
                && SecureBridgeConfig.isAllowedWebUri(uri);
    }

    private void openSecureWebView(String base) {
        if (webView != null) return;
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(17,19,21));
        setContentView(webView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(false); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false); s.setDatabaseEnabled(false); s.setGeolocationEnabled(false); s.setSaveFormData(false); s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setJavaScriptCanOpenWindowsAutomatically(false); s.setSupportMultipleWindows(false); s.setMediaPlaybackRequiresUserGesture(true); if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager cm = CookieManager.getInstance(); cm.setAcceptCookie(false); if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(webView,false);
        webView.clearCache(true); webView.clearHistory(); WebView.setWebContentsDebuggingEnabled(false);
        webView.addJavascriptInterface(new BridgeApi(this, store), "SecureBridge");
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){Uri u=request.getUrl();if(isAllowed(u))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(ActivityNotFoundException ignored){}return true;}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){Uri u=Uri.parse(url);if(!isAllowed(u)){view.stopLoading();finish();}}
        });
        webView.loadUrl(base + (base.contains("?")?"&":"?") + "bridge=1");
    }

    boolean confirmNativeDestruction() {
        final CountDownLatch latch=new CountDownLatch(1);final AtomicBoolean accepted=new AtomicBoolean(false);
        runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Détruire le coffre local ?").setMessage("Cette action supprime aussi les clés Android Keystore. Elle est définitive.").setNegativeButton("Annuler",(d,w)->latch.countDown()).setPositiveButton("Détruire",(d,w)->{accepted.set(true);latch.countDown();}).setOnCancelListener(d->latch.countDown()).show());
        try{latch.await(60, TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}return accepted.get();
    }

    void startNfcScan(String requestId) {
        runOnUiThread(() -> {
            if (!ensureNfcReady(requestId, true)) return;
            stopReaderMode();
            readerMode = ReaderMode.PRIVATE_TAG;
            pendingNfcRequest = trimId(requestId);
            int flags = NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
            nfcAdapter.enableReaderMode(this,this,flags,null);
            Toast.makeText(this,"Approchez votre objet NFC personnel",Toast.LENGTH_SHORT).show();
            nfcTimeout=()->{String req=pendingNfcRequest;stopReaderMode();if(req!=null)sendNfcResult(req,null,"Aucun objet NFC détecté.");};handler.postDelayed(nfcTimeout,NFC_TIMEOUT_MS);
        });
    }

    void startPairingHost(String requestId, String payloadB64) {
        runOnUiThread(() -> {
            try {
                if (!ensureNfcReady(requestId, false)) return;
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) { sendPairResult(requestId,null,"HCE NFC indisponible sur ce téléphone."); return; }
                cancelPairingInternal(false);
                pendingPairRequest = trimId(requestId);
                pendingPairPayload = Base64.decode(payloadB64, Base64.NO_WRAP);
                PairingCoordinator.startHost(pendingPairPayload, remote -> handler.post(() -> { String req=pendingPairRequest; if(req!=null){ sendPairResult(req,remote,null); pendingPairRequest=null; } }));
                cardEmulation = CardEmulation.getInstance(nfcAdapter);
                hceComponent = new ComponentName(this, PairingHostService.class);
                if (!cardEmulation.setPreferredService(this,hceComponent)) { sendPairResult(requestId,null,"Impossible d'activer le mode NFC de réception."); cancelPairingInternal(false); return; }
                Toast.makeText(this,"Mode réception NFC actif — rapprochez l'autre téléphone",Toast.LENGTH_LONG).show();
                pairTimeout=()->{String req=pendingPairRequest;cancelPairingInternal(false);if(req!=null)sendPairResult(req,null,"Pairing NFC expiré.");};handler.postDelayed(pairTimeout,PAIR_TIMEOUT_MS);
            } catch (Exception e) { sendPairResult(requestId,null,"Pairing NFC impossible."); cancelPairingInternal(false); }
        });
    }

    void startPairingReader(String requestId, String payloadB64) {
        runOnUiThread(() -> {
            try {
                if (!ensureNfcReady(requestId,false)) return;
                cancelPairingInternal(false);
                readerMode=ReaderMode.PAIR_READER; pendingPairRequest=trimId(requestId); pendingPairPayload=Base64.decode(payloadB64,Base64.NO_WRAP);
                int flags=NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
                nfcAdapter.enableReaderMode(this,this,flags,null);
                Toast.makeText(this,"Rapprochez ce téléphone du téléphone en mode réception",Toast.LENGTH_LONG).show();
                pairTimeout=()->{String req=pendingPairRequest;cancelPairingInternal(false);if(req!=null)sendPairResult(req,null,"Pairing NFC expiré.");};handler.postDelayed(pairTimeout,PAIR_TIMEOUT_MS);
            } catch (Exception e) { sendPairResult(requestId,null,"Pairing NFC impossible."); cancelPairingInternal(false); }
        });
    }

    void cancelPairing() { runOnUiThread(() -> cancelPairingInternal(true)); }

    @Override public void onTagDiscovered(Tag tag) {
        if (readerMode == ReaderMode.PRIVATE_TAG) {
            byte[] id=tag==null?null:tag.getId();final String uid=id==null||id.length==0?null:toHex(id),req=pendingNfcRequest;
            handler.post(()->{stopReaderMode();if(req==null)return;if(uid==null)sendNfcResult(req,null,"Cet objet NFC n'expose pas d'identifiant stable exploitable.");else sendNfcResult(req,uid,null);});
            return;
        }
        if (readerMode == ReaderMode.PAIR_READER) {
            final String req=pendingPairRequest;final byte[] local=pendingPairPayload==null?null:Arrays.copyOf(pendingPairPayload,pendingPairPayload.length);
            try { byte[] remote = exchangeWithHce(tag,local); handler.post(()->{sendPairResult(req,remote,null);cancelPairingInternal(false);}); }
            catch (Exception e) { handler.post(()->{sendPairResult(req,null,"Échange NFC incomplet. Rapprochez les deux téléphones et réessayez.");cancelPairingInternal(false);}); }
        }
    }

    private byte[] exchangeWithHce(Tag tag, byte[] local) throws Exception {
        if (local == null || local.length == 0 || local.length > 4096) throw new IllegalArgumentException();
        IsoDep iso=IsoDep.get(tag); if(iso==null)throw new IllegalStateException("iso");
        iso.connect(); iso.setTimeout(5000);
        try {
            byte[] select=new byte[5+PairingHostService.AID.length];select[0]=0x00;select[1]=(byte)0xA4;select[2]=0x04;select[3]=0x00;select[4]=(byte)PairingHostService.AID.length;System.arraycopy(PairingHostService.AID,0,select,5,PairingHostService.AID.length);checkOk(iso.transceive(select));
            int seq=0;
            for(int off=0;off<local.length;off+=PAIR_CHUNK){int end=Math.min(local.length,off+PAIR_CHUNK);boolean more=end<local.length;byte[] part=Arrays.copyOfRange(local,off,end);byte[] cmd=new byte[5+part.length];cmd[0]=(byte)0x80;cmd[1]=0x10;cmd[2]=(byte)(more?1:0);cmd[3]=(byte)(seq++&0xff);cmd[4]=(byte)part.length;System.arraycopy(part,0,cmd,5,part.length);checkOk(iso.transceive(cmd));}
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            for(int i=0;i<32;i++){byte[] cmd=new byte[]{(byte)0x80,0x20,(byte)(i&0xff),0x00};byte[] res=iso.transceive(cmd);checkOk(res);if(res.length<3)throw new IllegalStateException("short");boolean more=res[0]!=0;out.write(res,1,res.length-3);if(!more)return out.toByteArray();}
            throw new IllegalStateException("chunks");
        } finally { try{iso.close();}catch(Exception ignored){} }
    }

    private void checkOk(byte[] response) throws Exception { if(response==null||response.length<2||response[response.length-2]!=(byte)0x90||response[response.length-1]!=0x00)throw new IllegalStateException("sw"); }

    private boolean ensureNfcReady(String requestId, boolean privateScan) {
        if (nfcAdapter == null) { if(privateScan)sendNfcResult(requestId,null,"NFC indisponible sur ce téléphone.");else sendPairResult(requestId,null,"NFC indisponible sur ce téléphone."); return false; }
        if (!nfcAdapter.isEnabled()) { if(privateScan)sendNfcResult(requestId,null,"Activez le NFC dans Android.");else sendPairResult(requestId,null,"Activez le NFC dans Android.");try{startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));}catch(Exception ignored){}return false; }
        return true;
    }

    void configureBackgroundPolling(String apiUrl, String channelsJson) {
        try {
            if (!SecureBridgeConfig.isAllowedApiUrl(apiUrl)) return;
            WakeManager.configure(this, SecureBridgeConfig.apiUrl(), channelsJson);
        } catch (Exception ignored) {}
    }
    void disableBackgroundPolling() { WakeManager.disable(this); }

    boolean notificationPermissionGranted() { return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED; }
    void requestNotificationPermission() { runOnUiThread(() -> { if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},700); }); }

    private void sendNfcResult(String requestId,String uid,String error){if(webView==null)return;String js="window.__secureBridgeNfcResult("+JSONObject.quote(requestId)+","+(uid==null?"null":JSONObject.quote(uid))+","+(error==null?"null":JSONObject.quote(error))+");";webView.evaluateJavascript(js,null);}
    private void sendPairResult(String requestId,byte[] payload,String error){if(webView==null||requestId==null)return;String b=payload==null?null:Base64.encodeToString(payload,Base64.NO_WRAP);String js="window.__secureBridgePairResult("+JSONObject.quote(requestId)+","+(b==null?"null":JSONObject.quote(b))+","+(error==null?"null":JSONObject.quote(error))+");";webView.evaluateJavascript(js,null);}

    private void stopReaderMode(){if(nfcTimeout!=null)handler.removeCallbacks(nfcTimeout);nfcTimeout=null;pendingNfcRequest=null;if(readerMode!=ReaderMode.NONE&&nfcAdapter!=null)try{nfcAdapter.disableReaderMode(this);}catch(Exception ignored){}readerMode=ReaderMode.NONE;}
    private void cancelPairingInternal(boolean notify){if(pairTimeout!=null)handler.removeCallbacks(pairTimeout);pairTimeout=null;if(readerMode==ReaderMode.PAIR_READER&&nfcAdapter!=null)try{nfcAdapter.disableReaderMode(this);}catch(Exception ignored){}readerMode=ReaderMode.NONE;if(cardEmulation!=null)try{cardEmulation.unsetPreferredService(this);}catch(Exception ignored){}cardEmulation=null;hceComponent=null;PairingCoordinator.clear();pendingPairRequest=null;if(pendingPairPayload!=null)Arrays.fill(pendingPairPayload,(byte)0);pendingPairPayload=null;}
    private String trimId(String s){if(s==null)return"";return s.length()>96?s.substring(0,96):s;}
    private String toHex(byte[] bytes){StringBuilder sb=new StringBuilder(bytes.length*2);for(byte b:bytes)sb.append(String.format(Locale.ROOT,"%02X",b&0xff));return sb.toString();}

    private void showNotBoundAndFinish(){new AlertDialog.Builder(this).setTitle("SecureBridge non associé").setMessage("Ouvrez d'abord votre site dans Chrome puis utilisez « Associer SecureBridge ».").setPositiveButton("Fermer",(d,w)->finish()).setCancelable(false).show();}

    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){stopReaderMode();cancelPairingInternal(false);if(webView!=null){webView.removeJavascriptInterface("SecureBridge");webView.loadUrl("about:blank");webView.clearHistory();webView.clearCache(true);webView.destroy();webView=null;}super.onDestroy();}
}
