package com.example.android.sunshine.app;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;
public class WatchfaceService extends WearableListenerService {
    private static final String TAG = WatchfaceService.class.getSimpleName();
    private static final String WEATHER_URI_PATH = "/weather";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(WEATHER_URI_PATH)) {
                    //Sync with the app's data
                    SunshineSyncAdapter.syncImmediately(this);
                }
            }
        }
    }
}
