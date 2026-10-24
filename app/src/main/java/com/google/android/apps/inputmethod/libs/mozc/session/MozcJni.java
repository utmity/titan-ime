package com.google.android.apps.inputmethod.libs.mozc.session;

/** Names and signatures required by upstream Mozc's RegisterNatives bridge. */
public final class MozcJni {
    private MozcJni() {}
    static {
        System.loadLibrary("mozc");
        if (!initialize()) throw new IllegalStateException("Mozc JNI registration failed");
    }
    private static native boolean initialize();
    public static native boolean onPostLoad(String profileDirectory, String dataFile);
    public static native byte[] evalCommand(byte[] command);
    public static native String getDataVersion();
}
