package com.i2max.mcpushtest2;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.GoogleApiAvailability;
import com.i2max.mcpushtest2.ui.OpenDirectActivity;
import com.salesforce.marketingcloud.InitializationStatus;
import com.salesforce.marketingcloud.MCLogListener;
import com.salesforce.marketingcloud.MarketingCloudConfig;
import com.salesforce.marketingcloud.MarketingCloudSdk;
import com.salesforce.marketingcloud.messages.RegionMessageManager;
import com.salesforce.marketingcloud.messages.geofence.GeofenceMessageResponse;
import com.salesforce.marketingcloud.messages.proximity.ProximityMessageResponse;
import com.salesforce.marketingcloud.notifications.NotificationManager;
import com.salesforce.marketingcloud.notifications.NotificationMessage;
import com.salesforce.marketingcloud.registration.Attribute;
import com.salesforce.marketingcloud.registration.Registration;
import com.salesforce.marketingcloud.registration.RegistrationManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class AndroidApp extends Application implements MarketingCloudSdk.InitializationListener,
        RegistrationManager.RegistrationEventListener, RegionMessageManager.GeofenceMessageResponseListener,
        RegionMessageManager.ProximityMessageResponseListener, NotificationManager.NotificationBuilder {


    /**
     * Push Notification
     * Set to true to test how notifications can send your app customers to
     * different web pages.
     */
    public static final boolean CLOUD_PAGES_ENABLED = true;
    /**
     * Salesforce Analytics
     * Set to true to show how Salesforce analytics will save statistics for
     * how your customers use the app.
     */
    public static final boolean ANALYTICS_ENABLED = false;
    /**
     * Set to true to show how Predictive Intelligence analytics (PIAnalytics) will
     * save statistics for how your customers use the app (by invitation at this point).
     */
    public static final boolean PI_ENABLED = false;
    /**
     * Beacons
     * Set to true to show how beacons messages works within the SDK.
     */
    public static final boolean PROXIMITY_ENABLED = false;
    /**
     * GPS MAP
     * Set to true to show how geo fencing works within the SDK.
     */
    public static final boolean LOCATION_ENABLED = false;

    private static final String TAG = "~#AndroidApp";
    private SharedPreferences sharedPreferences;



    @Override
    public void onCreate() {
        super.onCreate();

        sharedPreferences = getSharedPreferences("AndroidLearningApp", Context.MODE_PRIVATE);

        /** Register to receive push notifications. */
        MarketingCloudSdk.setLogLevel(BuildConfig.DEBUG ? Log.VERBOSE : Log.ERROR);
        MarketingCloudSdk.setLogListener(new MCLogListener.AndroidLogListener());
        MarketingCloudSdk.init(this, MarketingCloudConfig.builder()
                .setApplicationId(getString(R.string.app_id))
                .setAccessToken(getString(R.string.access_token))
                .setGcmSenderId(getString(R.string.gcm_sender_id))
                .setAnalyticsEnabled(ANALYTICS_ENABLED)
                .setGeofencingEnabled(LOCATION_ENABLED)
                .setPiAnalyticsEnabled(PI_ENABLED)
                .setCloudPagesEnabled(CLOUD_PAGES_ENABLED)
                .setProximityEnabled(PROXIMITY_ENABLED)
                .setNotificationSmallIconResId(R.drawable.ic_stat_app_logo_transparent)
                .setNotificationBuilder(this)
                .setNotificationChannelName("Marketing Notifications")
                .setOpenDirectRecipient(OpenDirectActivity.class).build(), this);

        MarketingCloudSdk.requestSdk(new MarketingCloudSdk.WhenReadyListener() {
            @Override
            public void ready(MarketingCloudSdk sdk) {
                sdk.getRegistrationManager().registerForRegistrationEvents(AndroidApp.this);
                //sdk.getRegionMessageManager().registerGeofenceMessageResponseListener(AndroidApp.this); //GPS Location
                //sdk.getRegionMessageManager().registerProximityMessageResponseListener(AndroidApp.this); //Beacon
            }
        });
    }



    @Override
    public void complete(@NonNull InitializationStatus initializationStatus) {
        if (!initializationStatus.isUsable()) {
            Log.e(TAG, "Marketing Cloud Sdk init failed.", initializationStatus.unrecoverableException());
        } else {
            MarketingCloudSdk cloudSdk = MarketingCloudSdk.getInstance();
            cloudSdk.getAnalyticsManager().trackPageView("data://ReadyAimFireCompleted", "Marketing Cloud SDK Initialization Complete");

            if (initializationStatus.locationsError()) {
                final GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
                Log.i(TAG, String.format(Locale.ENGLISH, "Google Play Services Availability: %s", googleApiAvailability.getErrorString(initializationStatus.locationPlayServicesStatus())));
                if (googleApiAvailability.isUserResolvableError(initializationStatus.locationPlayServicesStatus())) {
                    googleApiAvailability.showErrorNotification(AndroidApp.this, initializationStatus.locationPlayServicesStatus());
                }
            }
        }
    }



    @Override
    public NotificationCompat.Builder setupNotificationBuilder(@NonNull Context context, @NonNull NotificationMessage notificationMessage) {
        NotificationCompat.Builder builder = NotificationManager.setupNotificationBuilder(context, notificationMessage);

        Map<String, String> customKeys = notificationMessage.customKeys();
        if (!customKeys.containsKey("category") || !customKeys.containsKey("sale_date")) {
            return builder;
        }

        if ("sale".equalsIgnoreCase(customKeys.get("category"))) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            simpleDateFormat.setTimeZone(TimeZone.getDefault());
            try {
                Date saleDate = simpleDateFormat.parse(customKeys.get("sale_date"));
                Intent intent = new Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, saleDate.getTime())
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, saleDate.getTime())
                        .putExtra(CalendarContract.Events.TITLE, customKeys.get("event_title"))
                        .putExtra(CalendarContract.Events.DESCRIPTION, customKeys.get("alert"))
                        .putExtra(CalendarContract.Events.HAS_ALARM, 1)
                        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, R.id.interactive_notification_reminder, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                builder.addAction(android.R.drawable.ic_menu_my_calendar, getString(R.string.in_btn_add_reminder), pendingIntent);
            } catch (ParseException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return builder;
    }

    @Override
    public void onRegistrationReceived(@NonNull Registration registration) {
        MarketingCloudSdk.getInstance().getAnalyticsManager().trackPageView("data://RegistrationEvent", "Registration Event Completed");
        if (MarketingCloudSdk.getLogLevel() <= Log.DEBUG) {
            Log.d(TAG, "Marketing Cloud update occurred.");
            Log.d(TAG, "Device ID:" + registration.deviceId());
            Log.d(TAG, "Device Token:" + registration.systemToken());
            Log.d(TAG, "Subscriber key:" + registration.contactKey());
            for (Attribute attribute : registration.attributes()) {
                Log.d(TAG, "Attribute " + (attribute).key() + ": [" + (attribute).value() + "]");
            }
            Log.d(TAG, "Tags: " + registration.tags());
            Log.d(TAG, "Language: " + registration.locale());
            Log.d(TAG, String.format("Last sent: %1$d", System.currentTimeMillis()));
        }
    }

    /**
     * GPS Location
     * @param geofenceMessageResponse
     */
    @Override
    public void onGeofenceMessageResponse(@NonNull GeofenceMessageResponse geofenceMessageResponse) {

    }

    /**
     * Beacon
     * @param proximityMessageResponse
     */
    @Override
    public void onProximityMessageResponse(@NonNull ProximityMessageResponse proximityMessageResponse) {

    }
}