package net.patzleiner.nettyquicdemo;

import android.app.Application;
import android.os.Build;
import org.slf4j.impl.HandroidLoggerAdapter;

public class QuicApplication extends Application {

    public QuicApplication() {
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG;
        HandroidLoggerAdapter.ANDROID_API_LEVEL = Build.VERSION.SDK_INT;
        HandroidLoggerAdapter.APP_NAME = "Quic Demo";
    }
}
