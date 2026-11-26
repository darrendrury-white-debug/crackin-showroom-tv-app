package uk.co.crackinevents.showroomtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        SharedPreferences p = c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("done", false) || !p.getBoolean("boot", true)) return;
        Intent s = new Intent(c, MainActivity.class);
        s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(s);
    }
}
