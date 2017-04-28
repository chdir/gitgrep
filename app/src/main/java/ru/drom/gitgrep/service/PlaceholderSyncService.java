package ru.drom.gitgrep.service;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;

public final class PlaceholderSyncService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new AbstractThreadedSyncAdapter(getBaseContext(), false) {
            @Override
            public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
                // nothing, we don't actually perform background data exchange
            }
        }.getSyncAdapterBinder();
    }
}
