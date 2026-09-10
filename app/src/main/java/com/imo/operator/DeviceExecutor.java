package com.imo.operator;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import java.util.List;
import java.util.Locale;

public final class DeviceExecutor {
    private final Context context;
    public DeviceExecutor(Context context){this.context=context.getApplicationContext();}

    public boolean openApp(String packageName,String label){
        try{
            String pkg=(packageName==null||packageName.trim().isEmpty())?resolvePackage(label):packageName.trim();
            if(pkg==null||pkg.isEmpty())return false;
            Intent i=context.getPackageManager().getLaunchIntentForPackage(pkg); if(i==null)return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); context.startActivity(i); return true;
        }catch(Exception e){return false;}
    }

    private String resolvePackage(String label){
        if(label==null)return null; String x=label.toLowerCase(Locale.ROOT).trim();
        String[] aliases={"whatsapp","youtube","chrome","telegram","facebook","instagram","maps","google maps","gmail","google","drive","photos","camera","settings","pengaturan","phone","telepon","messages","pesan","spotify","tiktok"};
        String[] pkgs={"com.whatsapp","com.google.android.youtube","com.android.chrome","org.telegram.messenger","com.facebook.katana","com.instagram.android","com.google.android.apps.maps","com.google.android.gm","com.google.android.googlequicksearchbox","com.google.android.apps.docs","com.google.android.apps.photos","com.android.camera2","com.android.settings","com.android.settings","com.google.android.dialer","com.google.android.apps.messaging","com.spotify.music","com.zhiliaoapp.musically"};
        for(int i=0;i<aliases.length;i++)if(x.contains(aliases[i]))return pkgs[i];
        try{
            PackageManager pm=context.getPackageManager(); List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for(ApplicationInfo app:apps){String name=pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT); if(name.equals(x)||name.contains(x)||x.contains(name))return app.packageName;}
        }catch(Exception ignored){}
        return null;
    }

    public boolean openUrl(String url){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return true;}catch(Exception e){return false;}}
    public void delay(long ms){SystemClock.sleep(Math.min(Math.max(ms,0),5000));}
}
