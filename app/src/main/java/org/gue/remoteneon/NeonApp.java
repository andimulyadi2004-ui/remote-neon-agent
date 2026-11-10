package org.gue.remoteneon;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

public class NeonApp extends Application {
    private Thread.UncaughtExceptionHandler defaultHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();

                File crashFile = new File(dir, "crash.txt");
                FileWriter writer = new FileWriter(crashFile, false);
                writer.write("Waktu: " + new Date() + "\n");
                writer.write("Thread: " + thread.getName() + "\n\n");
                writer.write(Log.getStackTraceString(throwable));
                writer.close();
            } catch (Exception ignored) {
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
