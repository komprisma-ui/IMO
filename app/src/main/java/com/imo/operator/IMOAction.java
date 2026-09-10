package com.imo.operator;

/** Structured action vocabulary used by IMO's planning/execution loop. */
public final class IMOAction {
    public enum Type {
        OPEN_APP, CLICK, TYPE, READ, BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS,
        POWER_DIALOG, SPLIT_SCREEN, WAIT, SCROLL_DOWN, SCROLL_UP, SWIPE, LONG_CLICK,
        LOCK_SCREEN, WAKE_SCREEN, TORCH_ON, TORCH_OFF,
        OPEN_WIFI_SETTINGS, OPEN_BLUETOOTH_SETTINGS, OPEN_SYSTEM_SETTINGS,
        OPEN_URL, OPEN_SETTINGS_PAGE, VOLUME_UP, VOLUME_DOWN, MUTE, CALL,
        CONFIRM, NONE
    }
    public final Type type;
    public final String value;
    /** Compatibility alias used by fast-intent tests and integrations. */
    public final String parameter;
    public final long waitMs;
    public final boolean sensitive;
    private IMOAction(Type type, String value, long waitMs, boolean sensitive) {
        this.type=type; this.value=value; this.parameter=value; this.waitMs=waitMs; this.sensitive=sensitive;
    }
    public static IMOAction of(Type type,String value){return new IMOAction(type,value,0,isSensitive(type,value));}
    public static IMOAction sensitive(Type type,String value){return new IMOAction(type,value,0,true);}
    public static IMOAction waitFor(long ms){return new IMOAction(Type.WAIT,null,ms,false);}
    public static IMOAction none(){return new IMOAction(Type.NONE,null,0,false);}
    private static boolean isSensitive(Type type,String value){
        if(type==Type.CONFIRM||type==Type.CALL||type==Type.POWER_DIALOG)return true;
        String v=value==null?"":value.toLowerCase();
        return v.contains("kirim")||v.contains("hapus")||v.contains("bayar")||v.contains("transfer")||v.contains("beli")||v.contains("password")||v.contains("kode otp")||v.contains("uninstall");
    }
    @Override public String toString(){return type+(value==null?"":"("+value+")")+(sensitive?" [SENSITIVE]":"");}
}
