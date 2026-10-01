package be.localbridge.securebridge;

import android.net.Uri;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

final class SecureBridgeConfig {
    private static final Pattern DEPLOYMENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{16,128}");
    private SecureBridgeConfig() {}

    static boolean isConfigured() {
        if (!BuildConfig.SECUREBRIDGE_CONFIGURED) return false;
        try {
            return configuredOrigin() != null
                    && configuredPathPrefix() != null
                    && configuredApiUri() != null
                    && DEPLOYMENT_ID_PATTERN.matcher(BuildConfig.SECUREBRIDGE_DEPLOYMENT_ID).matches();
        } catch (Exception e) {
            return false;
        }
    }

    static String deploymentId() { return isConfigured() ? BuildConfig.SECUREBRIDGE_DEPLOYMENT_ID : ""; }

    static String webBaseUrl() {
        if (!isConfigured()) return "";
        try {
            return canonicalOrigin(configuredOrigin()) + configuredPathPrefix();
        } catch (Exception e) {
            return "";
        }
    }

    static String apiUrl() {
        if (!isConfigured()) return "";
        try {
            return canonicalHttpsUrl(configuredApiUri());
        } catch (Exception e) {
            return "";
        }
    }

    static boolean isAllowedOrigin(Uri candidate) {
        if (!isConfigured() || candidate == null) return false;
        try {
            URI c = checkedHttpsUri(candidate.toString(), false);
            return sameOrigin(configuredOrigin(), c);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isAllowedWebUri(Uri candidate) {
        if (!isConfigured() || candidate == null) return false;
        try {
            URI c = checkedHttpsUri(candidate.toString(), true).normalize();
            if (!sameOrigin(configuredOrigin(), c)) return false;
            String path = c.getPath();
            if (path == null || path.isEmpty()) path = "/";
            return path.startsWith(configuredPathPrefix());
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isAllowedWebCandidate(String candidate) {
        if (candidate == null || candidate.length() > 2048) return false;
        try { return isAllowedWebUri(Uri.parse(candidate)); }
        catch (Exception e) { return false; }
    }

    static boolean isAllowedApiUrl(String candidate) {
        if (!isConfigured() || candidate == null || candidate.length() > 2048) return false;
        try {
            URI c = checkedHttpsUri(candidate, true).normalize();
            URI expected = configuredApiUri();
            return sameOrigin(configuredOrigin(), c)
                    && canonicalHttpsUrl(c).equals(canonicalHttpsUrl(expected));
        } catch (Exception e) {
            return false;
        }
    }

    private static URI configuredOrigin() throws Exception {
        URI origin = checkedHttpsUri(BuildConfig.SECUREBRIDGE_ALLOWED_ORIGIN, false);
        if (origin.getQuery() != null || origin.getFragment() != null) throw new IllegalArgumentException();
        return origin;
    }

    private static String configuredPathPrefix() throws Exception {
        String value = BuildConfig.SECUREBRIDGE_ALLOWED_PATH;
        if (value == null || value.isEmpty() || value.length() > 512 || !value.startsWith("/")) throw new IllegalArgumentException();
        rejectEncodedTraversal(value);
        URI normalized = new URI(null, null, value, null).normalize();
        String path = normalized.getPath();
        if (path == null || !path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException();
        if (!path.endsWith("/")) path += "/";
        return path;
    }

    private static URI configuredApiUri() throws Exception {
        URI api = checkedHttpsUri(BuildConfig.SECUREBRIDGE_API_URL, true).normalize();
        if (api.getQuery() != null || api.getFragment() != null) throw new IllegalArgumentException();
        if (!sameOrigin(configuredOrigin(), api)) throw new IllegalArgumentException();
        return api;
    }

    private static URI checkedHttpsUri(String value, boolean allowPath) throws Exception {
        if (value == null || value.isEmpty() || value.length() > 2048) throw new IllegalArgumentException();
        rejectEncodedTraversal(value);
        URI uri = new URI(value).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        if (uri.getRawUserInfo() != null) throw new IllegalArgumentException();
        if (!allowPath) {
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) throw new IllegalArgumentException();
        }
        return uri;
    }

    private static void rejectEncodedTraversal(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c") || value.contains("\\")) {
            throw new IllegalArgumentException();
        }
    }

    private static boolean sameOrigin(URI a, URI b) {
        return "https".equalsIgnoreCase(a.getScheme())
                && "https".equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private static int effectivePort(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }

    private static String canonicalOrigin(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = effectivePort(uri);
        return "https://" + host + (port == 443 ? "" : ":" + port);
    }

    private static String canonicalHttpsUrl(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        return canonicalOrigin(uri) + path;
    }
}
