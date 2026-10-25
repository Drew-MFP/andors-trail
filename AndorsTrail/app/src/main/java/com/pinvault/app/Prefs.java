package com.pinvault.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores the PIN (salted + hashed, never in plain text), the vault's app list, and lockout state. */
final class Prefs {
    private Prefs() {}

    static final int MAX_FAILS = 5;
    static final long LOCKOUT_MS = 30_000;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("vault", Context.MODE_PRIVATE);
    }

    static boolean hasPin(Context c) {
        return sp(c).contains("hash");
    }

    static void setPin(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = Base64.encodeToString(salt, Base64.NO_WRAP);
        sp(c).edit().putString("salt", s).putString("hash", hash(s, pin)).apply();
    }

    static boolean checkPin(Context c, String pin) {
        String salt = sp(c).getString("salt", "");
        String stored = sp(c).getString("hash", "");
        return MessageDigest.isEqual(hash(salt, pin).getBytes(), stored.getBytes());
    }

    private static String hash(String salt, String pin) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(),
                    Base64.decode(salt, Base64.NO_WRAP), 20_000, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Set<String> getApps(Context c) {
        return new HashSet<>(sp(c).getStringSet("apps", new HashSet<>()));
    }

    static void setApps(Context c, Set<String> apps) {
        sp(c).edit().putStringSet("apps", new HashSet<>(apps)).apply();
    }

    /** Records a wrong PIN. Returns true if this triggered a lockout. */
    static boolean recordFail(Context c) {
        int fails = sp(c).getInt("fails", 0) + 1;
        if (fails >= MAX_FAILS) {
            sp(c).edit().putInt("fails", 0)
                    .putLong("lockUntil", System.currentTimeMillis() + LOCKOUT_MS).apply();
            return true;
        }
        sp(c).edit().putInt("fails", fails).apply();
        return false;
    }

    static int failsLeft(Context c) {
        return MAX_FAILS - sp(c).getInt("fails", 0);
    }

    static void clearFails(Context c) {
        sp(c).edit().putInt("fails", 0).putLong("lockUntil", 0).apply();
    }

    static long lockRemainingMs(Context c) {
        return Math.max(0, sp(c).getLong("lockUntil", 0) - System.currentTimeMillis());
    }

}
