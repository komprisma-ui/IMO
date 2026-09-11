package com.imo.operator;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.List;
import java.util.Locale;

/** Android-side capability layer. Keeps device operations deterministic and bounded. */
public final class DeviceExecutor {
    private final Context context;
    public DeviceExecutor(Context context){this.context=context.getApplicationContext();}

    public boolean openApp(String packageName,String label){
        try{
            String pkg=(packageName==null||packageName.trim().isEmpty())?resolvePackage(label):packageName.trim();
            if(pkg==null||pkg.isEmpty())return false;
            Intent i=context.getPackageManager().getLaunchIntentForPackage(pkg);
            if(i==null)return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);return true;
        }catch(Exception e){return false;}
    }

    private String resolvePackage(String label){
        if(label==null)return null;
        String x=normalize(label);
        if(x.isEmpty())return null;
        String[] aliases={
            "whatsapp","wa","youtube","yt","chrome","browser","telegram","facebook","fb","instagram","ig",
            "maps","google maps","peta","gmail","email","google","drive","google drive","photos","google photos",
            "camera","kamera","settings","pengaturan","phone","telepon","dialer","messages","pesan","sms",
            "spotify","tiktok","play store","playstore","google play"
        };
        String[] pkgs={
            "com.whatsapp","com.whatsapp","com.google.android.youtube","com.google.android.youtube","com.android.chrome","com.android.chrome","org.telegram.messenger","com.facebook.katana","com.facebook.katana","com.instagram.android","com.instagram.android",
            "com.google.android.apps.maps","com.google.android.apps.maps","com.google.android.gm","com.google.android.gm","com.google.android.googlequicksearchbox","com.google.android.apps.docs","com.google.android.apps.docs","com.google.android.apps.photos","com.google.android.apps.photos",
            "com.android.camera2","com.android.camera2","com.android.settings","com.android.settings","com.google.android.dialer","com.google.android.dialer","com.google.android.apps.messaging","com.google.android.apps.messaging","com.google.android.apps.messaging",
            "com.spotify.music","com.zhiliaoapp.musically","com.android.vending","com.android.vending"
        };
        for(int i=0;i<aliases.length;i++)if(x.equals(aliases[i])||x.contains(aliases[i]))return pkgs[i];
        try{
            PackageManager pm=context.getPackageManager();
            List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String best=null;int bestScore=0;
            for(ApplicationInfo app:apps){
                if(!isLaunchable(pm,app.packageName))continue;
                CharSequence labelText=pm.getApplicationLabel(app);
                if(labelText==null)continue;
                String name=normalize(labelText.toString());
                if(name.isEmpty())continue;
                int score=0;
                if(name.equals(x))score=100;
                else if(name.startsWith(x))score=80;
                else if(name.contains(x))score=60;
                if(x.contains(name)&&name.length()>=4)score=Math.max(score,50);
                if(score>bestScore){bestScore=score;best=app.packageName;}
            }
            return best;
        }catch(Exception ignored){return null;}
    }

    private boolean isLaunchable(PackageManager pm,String pkg){return pm.getLaunchIntentForPackage(pkg)!=null;}
    private String normalize(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u00c0-\\u024f ]"," ").replaceAll("\\s+"," ").trim();}

    public boolean openUrl(String url){
        try{
            if(url==null||url.trim().isEmpty())return false;
            String u=url.trim();
            if(!u.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))u="https://"+u;
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(u));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return true;
        }catch(Exception e){return false;}
    }

    public boolean openSettings(String section){
        try{
            String s=normalize(section==null?"":section);Intent i=new Intent();
            if(s.contains("wifi")||s.contains("wi fi"))i.setAction(Settings.ACTION_WIFI_SETTINGS);
            else if(s.contains("bluetooth"))i.setAction(Settings.ACTION_BLUETOOTH_SETTINGS);
            else if(s.contains("display")||s.contains("layar"))i.setAction(Settings.ACTION_DISPLAY_SETTINGS);
            else if(s.contains("sound")||s.contains("suara")||s.contains("volume"))i.setAction(Settings.ACTION_SOUND_SETTINGS);
            else if(s.contains("accessibility")||s.contains("aksesibilitas"))i.setAction(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            else if(s.contains("app")||s.contains("aplikasi"))i.setAction(Settings.ACTION_APPLICATION_SETTINGS);
            else if(s.contains("battery")||s.contains("baterai"))i.setAction(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            else if(s.contains("notification")||s.contains("notifikasi"))i.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            else i.setAction(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return true;
        }catch(Exception e){return false;}
    }

    public boolean setVolume(int level){
        try{
            AudioManager a=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);if(a==null)return false;
            int max=a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);int v=Math.max(0,Math.min(100,level))*max/100;
            a.setStreamVolume(AudioManager.STREAM_MUSIC,v,0);return true;
        }catch(Exception e){return false;}
    }
    public boolean mute(){return setVolume(0);}
    public int getVolumePercent(){
        try{AudioManager a=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);if(a==null)return -1;int max=a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);if(max<=0)return 0;return Math.round(a.getStreamVolume(AudioManager.STREAM_MUSIC)*100f/max);}catch(Exception e){return -1;}
    }
    public void delay(long ms){SystemClock.sleep(Math.min(Math.max(ms,0),5000));}
}