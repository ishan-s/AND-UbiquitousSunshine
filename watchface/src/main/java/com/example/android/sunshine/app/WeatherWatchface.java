/* Ishan Shrivastava: Code referred and borrowed from WatchFace sample from AOSP,
 * particularly the DigitalWatchFaceService Class. Modified and fitted to suit the requirements here.
 */
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherWatchface extends CanvasWatchFaceService {
    private static String TAG = WeatherWatchface.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WeatherWatchface.Engine> mWeakReference;

        public EngineHandler(WeatherWatchface.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WeatherWatchface.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        private static final String WEATHER_URI_PATH = "/weather";
        private static final String WEATHER_URI_INFO_PATH = "/weather-info";
        private static final String DATA_PARAM_UUID = "uuid";
        private static final String DATA_PARAM_HIGH = "high";
        private static final String DATA_PARAM_LOW = "low";
        private static final String DATA_PARAM_WEATHER_ID = "weatherId";

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredReceiver = false;
        Resources mResources;

        Paint mTimePaint;
        Paint mTimeSecPaint;

        Paint mDayPaint;
        Paint mDatePaint;

        Paint mTempHiPaint;
        Paint mTempLoPaint;

        Paint mDayAmbientPaint;
        Paint mDateAmbientPaint;
        Paint mTempLoAmbientPaint;

        Paint mBgPaint;

        Bitmap mWeatherIcon;
        String mWeatherHi;
        String mWeatherLo;

        boolean mAmbient;
        private Calendar mCalendar;

        float mTimeYOffset;
        float mDayYOffset;
        float mDateYOffset;
        float mDividerYOffset;
        float mWeatherYOffset;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchface.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        /* Broadcast receiver for handling timezone changes */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                long now = System.currentTimeMillis();
                mCalendar.setTimeInMillis(now);
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchface.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mResources = WeatherWatchface.this.getResources();
            mCalendar = Calendar.getInstance();
            mTimeYOffset = mResources.getDimension(R.dimen.time_y_offset);
            mBgPaint = new Paint();
            mBgPaint.setColor(mResources.getColor(R.color.digital_background));

            paintTextElements();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private void paintTextElements() {
            mTimePaint = new Paint();
            mTimePaint.setColor(Color.WHITE);
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);

            mTimeSecPaint = new Paint();
            mTimeSecPaint.setColor(Color.WHITE);
            mTimeSecPaint.setTypeface(NORMAL_TYPEFACE);
            mTimeSecPaint.setAntiAlias(true);


            mDayPaint = new Paint();
            mDayPaint.setColor(mResources.getColor(R.color.primary_light));
            mDayPaint.setTypeface(NORMAL_TYPEFACE);
            mDayPaint.setAntiAlias(true);

            mDatePaint = new Paint();
            mDatePaint.setColor(mResources.getColor(R.color.primary_light));
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);

            mTempHiPaint = new Paint();
            mTempHiPaint.setColor(Color.WHITE);
            mTempHiPaint.setTypeface(BOLD_TYPEFACE);
            mTempHiPaint.setAntiAlias(true);

            mTempLoPaint = new Paint();
            mTempLoPaint.setColor(mResources.getColor(R.color.primary_light));
            mTempLoPaint.setTypeface(NORMAL_TYPEFACE);
            mTempLoPaint.setAntiAlias(true);

            /* for ambient mode, change colored elements to white */
            mDayAmbientPaint = new Paint();
            mDayAmbientPaint.setColor(Color.WHITE);
            mDayAmbientPaint.setTypeface(NORMAL_TYPEFACE);
            mDayAmbientPaint.setAntiAlias(false);

            mDateAmbientPaint = new Paint();
            mDateAmbientPaint.setColor(Color.WHITE);
            mDateAmbientPaint.setTypeface(NORMAL_TYPEFACE);
            mDateAmbientPaint.setAntiAlias(false);

            mTempLoAmbientPaint = new Paint();
            mTempLoAmbientPaint.setColor(Color.WHITE);
            mTempLoAmbientPaint.setTypeface(NORMAL_TYPEFACE);
            mTempLoAmbientPaint.setAntiAlias(false);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                long now = System.currentTimeMillis();
                mCalendar.setTimeInMillis(now);
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherWatchface.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            WeatherWatchface.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WeatherWatchface.this.getResources();
            boolean isRound = insets.isRound();

            mDayYOffset = resources.getDimension(isRound
                    ? R.dimen.day_y_offset_round : R.dimen.date_y_offset);
            mDateYOffset = resources.getDimension(isRound
                    ? R.dimen.date_y_offset_round : R.dimen.date_y_offset);
            mDividerYOffset = resources.getDimension(isRound
                    ? R.dimen.divider_y_offset_round : R.dimen.divider_y_offset);
            mWeatherYOffset = resources.getDimension(isRound
                    ? R.dimen.weather_y_offset_round : R.dimen.weather_y_offset);

            float timeSize = resources.getDimension(isRound
                    ? R.dimen.time_text_size_round : R.dimen.time_text_size);
            float daySize = resources.getDimension(isRound ? R.dimen.day_text_size : R.dimen.day_text_size_round);
            float dateSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            float tempSize = resources.getDimension(isRound
                    ? R.dimen.temp_text_size_round : R.dimen.temp_text_size);
            float secSize = (float) (timeSize * 0.40); //Seconds would be 40% of the time size

            mTimePaint.setTextSize(timeSize);
            mTimeSecPaint.setTextSize(secSize);
            mDayPaint.setTextSize(daySize);
            mDatePaint.setTextSize(dateSize);

            mTempHiPaint.setTextSize(tempSize);
            mTempLoPaint.setTextSize(tempSize);

            mDayAmbientPaint.setTextSize(daySize);
            mDateAmbientPaint.setTextSize(dateSize);
            mTempLoAmbientPaint.setTextSize(tempSize);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDayPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);

                    mTempHiPaint.setAntiAlias(!inAmbientMode);
                    mTempLoPaint.setAntiAlias(!inAmbientMode);

                    mTempLoAmbientPaint.setAntiAlias(!inAmbientMode);
                    mDateAmbientPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            Resources resources = getResources();

            //background
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBgPaint);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            boolean is24Hour = DateFormat.is24HourFormat(WeatherWatchface.this);
            int minute = mCalendar.get(Calendar.MINUTE);
            int second = mCalendar.get(Calendar.SECOND);
            int am_pm = mCalendar.get(Calendar.AM_PM);

            //time text
            String timeText;
            if (is24Hour) {
                int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
                timeText = String.format("%02d:%02d", hour, minute);
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                timeText = String.format("%d:%02d", hour, minute);
            }

            String secondsText = String.format("%02d", second);
            String amPmText = Utils.getAmPmString(getResources(), am_pm);
            float timeTextLen = mTimePaint.measureText(timeText);
            float xOffsetTime = timeTextLen / 2;
            if (mAmbient) {
                if (!is24Hour) {
                    xOffsetTime = xOffsetTime + (mTimeSecPaint.measureText(amPmText) / 2);
                }
            } else {
                xOffsetTime = xOffsetTime + (mTimeSecPaint.measureText(secondsText) / 2);
            }
            float xOffsetTimeFromCenter = bounds.centerX() - xOffsetTime;
            canvas.drawText(timeText, xOffsetTimeFromCenter, mTimeYOffset, mTimePaint);
            if (mAmbient) {
                if (!is24Hour) {
                    canvas.drawText(amPmText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeYOffset, mTimeSecPaint);
                }
            } else {
                canvas.drawText(secondsText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeYOffset, mTimeSecPaint);
            }

            Paint datePaint = mAmbient ? mDateAmbientPaint : mDatePaint;

            // day and date
            Paint dayPaint = mAmbient ? mDayAmbientPaint : mDayPaint;
            String dayOfWeekString = Utils.getDayString(resources, mCalendar.get(Calendar.DAY_OF_WEEK));

            float xOffsetDay = dayPaint.measureText(dayOfWeekString) / 2;
            canvas.drawText(dayOfWeekString, bounds.centerX() - xOffsetDay, mDayYOffset, dayPaint);

            String monthOfYearString = Utils.getMonthString(resources, mCalendar.get(Calendar.MONTH));
            int dayOfMonthInt = mCalendar.get(Calendar.DAY_OF_MONTH);
            int yearInt = mCalendar.get(Calendar.YEAR);

            String dateText = String.format("%d %s %d", dayOfMonthInt, monthOfYearString, yearInt);
            float xOffsetDate = datePaint.measureText(dateText) / 2;
            canvas.drawText(dateText, bounds.centerX() - xOffsetDate, mDateYOffset, datePaint);

            //draw divider only when weather information is present
            if (mWeatherHi != null && mWeatherLo != null && mWeatherIcon != null) {
                canvas.drawLine(bounds.centerX() - 100, mDividerYOffset, bounds.centerX() + 100, mDividerYOffset, datePaint);
                float highTextLen = mTempHiPaint.measureText(mWeatherHi);
                if (mAmbient) {
                    float lowTextLen = mTempLoAmbientPaint.measureText(mWeatherLo);
                    float xOffset = bounds.centerX() - ((highTextLen + lowTextLen + 20) / 2);
                    canvas.drawText(mWeatherHi, xOffset, mWeatherYOffset, mTempHiPaint);
                    canvas.drawText(mWeatherLo, xOffset + highTextLen + 20, mWeatherYOffset, mTempLoAmbientPaint);
                } else {
                    float xOffset = bounds.centerX() - (highTextLen / 2);
                    canvas.drawText(mWeatherHi, xOffset, mWeatherYOffset, mTempHiPaint);
                    canvas.drawText(mWeatherLo, bounds.centerX() + (highTextLen / 2) + 20, mWeatherYOffset, mTempLoPaint);
                    float iconXOffset = bounds.centerX() - ((highTextLen / 2) + mWeatherIcon.getWidth() + 30);
                    canvas.drawBitmap(mWeatherIcon, iconXOffset, mWeatherYOffset - mWeatherIcon.getHeight(), null);
                }
            }
        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            fetchWeatherData();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap weatherDataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.d(TAG, "weatherDataUriPath=" + path);

                    if (path.equals(WEATHER_URI_INFO_PATH)) {
                        if (weatherDataMap.containsKey(DATA_PARAM_HIGH))
                            mWeatherHi = weatherDataMap.getString(DATA_PARAM_HIGH);
                        else
                            Log.d(TAG, "[!] High Temperature not fetched");

                        if (weatherDataMap.containsKey(DATA_PARAM_LOW))
                            mWeatherLo = weatherDataMap.getString(DATA_PARAM_LOW);
                        else
                            Log.d(TAG, "[!] Low Temperature not fetched");
                    }

                    if (weatherDataMap.containsKey(DATA_PARAM_WEATHER_ID)) {
                        int weatherId = weatherDataMap.getInt(DATA_PARAM_WEATHER_ID);
                        Drawable b = getResources().getDrawable(Utils.getWeatherIconResource(weatherId));
                        Bitmap icon = ((BitmapDrawable) b).getBitmap();

                        float scaledWidth = (mTempHiPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
                        mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTempHiPaint.getTextSize(), true);

                    } else
                        Log.d(TAG, "[!] Weather not fetched");

                    invalidate();
                }
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }

        public void fetchWeatherData() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_URI_PATH);
            putDataMapRequest.getDataMap().putString(DATA_PARAM_UUID, UUID.randomUUID().toString());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess())
                                Log.d(TAG, "[!] Weather data not fetched from phone");
                        }
                    });
        }
    }
}
