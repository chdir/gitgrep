package ru.drom.gitgrep.service;

import android.accounts.AbstractAccountAuthenticator;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class AuthService extends Service {
    AbstractAccountAuthenticator authenticator;

    @Override
    public void onCreate() {
        super.onCreate();

        authenticator = new GithubAuthenticator(getApplication());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return authenticator.getIBinder();
    }
}
