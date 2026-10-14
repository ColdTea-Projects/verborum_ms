package de.coldtea.verborum.msnotification.devicetoken.entity;

/**
 * The push channel a token belongs to. Web is absent on purpose: the web client refreshes on tab focus
 * and does not use FCM (roadmap P4N-10, decided 2026-10-14).
 */
public enum DevicePlatform {
    ANDROID,
    IOS
}
