package com.bondhub.authservice.util;

/**
 * Lightweight User-Agent string parser.
 * <p>
 * Extracts the operating-system name and the browser / app name from a raw
 * {@code User-Agent} header value without requiring any external library.
 * Patterns are intentionally kept simple and ordered from most-specific to
 * least-specific so the first match wins.
 * </p>
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    /**
     * Returns a human-readable OS name derived from the User-Agent string.
     *
     * @param userAgent raw {@code User-Agent} header value, may be {@code null}
     * @return OS name, never {@code null}; falls back to {@code "Unknown OS"}
     */
    public static String parseOs(String userAgent) {
        return parseOs(userAgent, null);
    }

    /**
     * Returns a human-readable OS name derived from the User-Agent string,
     * with {@code deviceType} used as a fallback hint when the UA alone is not
     * enough.
     *
     * @param userAgent  raw {@code User-Agent} header value, may be {@code null}
     * @param deviceType optional hint ("MOBILE" / "WEB"), may be {@code null}
     * @return OS name, never {@code null}
     */
    public static String parseOs(String userAgent, String deviceType) {
        if (userAgent == null || userAgent.isBlank()) {
            return mobileOrUnknown(deviceType);
        }

        String ua = userAgent.toLowerCase();

        // OkHttp is Android-only — must come before generic "linux" check
        if (ua.contains("okhttp"))
            return "Android";

        // --- Mobile / tablet first (more specific) ---
        if (ua.contains("ipad"))
            return "iPadOS";
        if (ua.contains("iphone"))
            return "iOS";
        if (ua.contains("android")) {
            // Extract version when available, e.g. "Android 13"
            int idx = ua.indexOf("android ");
            if (idx >= 0) {
                String rest = userAgent.substring(idx + "android ".length());
                String version = rest.split("[;)\\s]")[0];
                return "Android " + version;
            }
            return "Android";
        }
        if (ua.contains("windows phone"))
            return "Windows Phone";
        if (ua.contains("harmony") || ua.contains("huawei"))
            return "HarmonyOS";
        // iOS native HTTP client (CFNetwork) without a browser UA
        if (ua.contains("cfnetwork"))
            return "iOS";

        // --- Desktop ---
        if (ua.contains("windows nt 10"))
            return "Windows 10/11";
        if (ua.contains("windows nt 6.3"))
            return "Windows 8.1";
        if (ua.contains("windows nt 6.2"))
            return "Windows 8";
        if (ua.contains("windows nt 6.1"))
            return "Windows 7";
        if (ua.contains("windows"))
            return "Windows";
        if (ua.contains("mac os x")) {
            int idx = ua.indexOf("mac os x ");
            if (idx >= 0) {
                String rest = userAgent.substring(idx + "mac os x ".length());
                String version = rest.split("[;)\\s]")[0].replace('_', '.');
                return "macOS " + version;
            }
            return "macOS";
        }
        if (ua.contains("ubuntu"))
            return "Ubuntu";
        if (ua.contains("chromeos") || ua.contains("cros"))
            return "ChromeOS";
        if (ua.contains("linux"))
            return "Linux";

        return mobileOrUnknown(deviceType);
    }

    // When the UA gives no clue, fall back based on deviceType hint
    private static String mobileOrUnknown(String deviceType) {
        if ("MOBILE".equalsIgnoreCase(deviceType))
            return "Android";
        return "Unknown OS";
    }

    /**
     * Returns a human-readable browser / app name derived from the User-Agent
     * string.
     *
     * @param userAgent raw {@code User-Agent} header value, may be {@code null}
     * @return browser/app name, never {@code null}; falls back to
     *         {@code "Unknown Browser"}
     */
    public static String parseBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Browser";
        }

        String ua = userAgent.toLowerCase();

        // Mobile app (Expo / React Native typically sends a custom UA)
        if (ua.contains("expo"))
            return "Expo App";
        if (ua.contains("okhttp"))
            return "Android App (OkHttp)";
        if (ua.contains("cfnetwork") && !ua.contains("safari"))
            return "iOS App";
        if (ua.contains("dart"))
            return "Flutter App";

        // Browsers — order matters: specific tokens first
        if (ua.contains("edg/") || ua.contains("edge/"))
            return "Microsoft Edge";
        if (ua.contains("opr/") || ua.contains("opera"))
            return "Opera";
        if (ua.contains("samsungbrowser"))
            return "Samsung Browser";
        if (ua.contains("ucbrowser"))
            return "UC Browser";
        if (ua.contains("yabrowser"))
            return "Yandex Browser";
        if (ua.contains("firefox"))
            return "Firefox";
        if (ua.contains("chrome") && !ua.contains("chromium"))
            return "Chrome";
        if (ua.contains("chromium"))
            return "Chromium";
        if (ua.contains("safari"))
            return "Safari";
        if (ua.contains("msie") || ua.contains("trident"))
            return "Internet Explorer";

        return userAgent.length() > 60
                ? userAgent.substring(0, 60) + "…"
                : userAgent;
    }
}
