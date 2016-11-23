package com.example.android.sunshine.app;

import android.content.res.Resources;
import android.support.annotation.NonNull;

import java.util.Calendar;

public class Utils {
    public static String getAmPmString(Resources resources, int am_pm) {
        return am_pm == Calendar.AM ?
                resources.getString(R.string.am) : resources.getString(R.string.pm);
    }

    public static String getMonthString(Resources resources, int monthInt) {
        switch (monthInt) {
            case Calendar.JANUARY:
                return resources.getString(R.string.january);

            case Calendar.FEBRUARY:
                return resources.getString(R.string.february);

            case Calendar.MARCH:
                return resources.getString(R.string.march);

            case Calendar.APRIL:
                return resources.getString(R.string.april);

            case Calendar.MAY:
                return resources.getString(R.string.may);

            case Calendar.JUNE:
                return resources.getString(R.string.june);

            case Calendar.JULY:
                return resources.getString(R.string.july);

            case Calendar.AUGUST:
                return resources.getString(R.string.august);

            case Calendar.SEPTEMBER:
                return resources.getString(R.string.september);

            case Calendar.OCTOBER:
                return resources.getString(R.string.october);

            case Calendar.NOVEMBER:
                return resources.getString(R.string.november);

            case Calendar.DECEMBER:
                return resources.getString(R.string.december);

            default:
                return "";
        }
    }

    public static String getDayString(Resources resources, int dayInt) {
        switch (dayInt) {
            case Calendar.SUNDAY:
                return resources.getString(R.string.sunday);
            case Calendar.MONDAY:
                return resources.getString(R.string.monday);
            case Calendar.TUESDAY:
                return resources.getString(R.string.tuesday);
            case Calendar.WEDNESDAY:
                return resources.getString(R.string.wednesday);
            case Calendar.THURSDAY:
                return resources.getString(R.string.thursday);
            case Calendar.FRIDAY:
                return resources.getString(R.string.friday);
            case Calendar.SATURDAY:
                return resources.getString(R.string.saturday);
            default:
                return "";
        }
    }

    public static int getWeatherIconResource(int weatherCode) {
        // Weather code data from http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherCode >= 200 && weatherCode <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherCode >= 300 && weatherCode <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherCode >= 500 && weatherCode <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherCode == 511) {
            return R.drawable.ic_snow;
        } else if (weatherCode >= 520 && weatherCode <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherCode >= 600 && weatherCode <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherCode >= 701 && weatherCode <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherCode == 761 || weatherCode == 781) {
            return R.drawable.ic_storm;
        } else if (weatherCode == 800) {
            return R.drawable.ic_clear;
        } else if (weatherCode == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherCode >= 802 && weatherCode <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }

}