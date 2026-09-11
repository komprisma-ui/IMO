package com.imo.operator;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** LUNA Android Operator: goal -> observe -> reason -> act -> verify -> recover -> complete. */
public final class LunaAgent {
 public interface Callback { void status(String text); void speak(String text); boolean confirm(JSONObject plan); void finished(); }
 private final MainActivity activity; private final LunaAccessibilityService service; private final SecureKeyStore keyStore; private final Callback callback;
 private static final ConversationMemory MEMORY=new ConversationMemory(); private static final int MAX_STEPS=24; private static final long MAX_RUNTIME=150000;
 public LunaAgent(MainActivity a,LunaAccessibilityService s,SecureKeyStore k,Callback c){activity=a;service=s;keyStore=k;callback=c;}
 public void run(String command){
  OperatorState session=new OperatorState(command); String previousTree="",previousAction="";
  try{
   if(command==null||command.trim().isEmpty()){callback.status("Katakan atau ketik tujuan Anda.");return;}
   String goal=command.trim(); MEMORY.addUser(goal);
   if(service==null){String x="Accessibility LUNA belum aktif. Aktifkan Accessibility agar saya dapat mengoperasikan Android.";callback.status("⚠ "+x);callback.speak(x);MEMORY.addAssistant(x);return;}
   if(runLocalCommand(goal))return;
   String key=keyStore.load(); if(key==null||key.trim().isEmpty())throw new IllegalStateException("API key Gemini belum diatur. Tekan GEMINI API KEY untuk menyimpan key.");
   GeminiClient ai=new GeminiClient(key); String failure="";
   for(int step=1;step<=MAX_STEPS&&!session.timedOut(MAX_RUNTIME);step++){
    if(service.isStopped())return;
    callback.status("👁 LUNA mengamati layar • langkah "+step+"/"+MAX_STEPS);
    String image=capture(); String tree=service.snapshot(); String pkg=service.currentPackage(); session.observed(pkg,tree);
    String prompt=operatorPrompt(goal,session,pkg,failure);
    JSONObject plan=new JSONObject(ai.plan(prompt,tree,image,MEMORY.prompt())); JSONArray actions=plan.optJSONArray("actions"); boolean done=plan.optBoolean("done",false);
    if(done && (actions==null||actions.length()==0)){
     String answer=plan.optString("speak","").trim(); if(answer.isEmpty())answer="Tujuan sudah tercapai.";
     callback.status("✓ Terverifikasi: "+answer); callback.speak(answer); MEMORY.addAssistant(answer); return;
    }
    if(actions==null||actions.length()==0){failure="model belum membuktikan tujuan selesai";session.recovered();callback.status("↻ LUNA belum mendapat bukti tujuan selesai; saya mengamati lagi.");if(!pause(250))return;continue;}
    JSONObject action=actions.optJSONObject(0); if(action==null){failure="invalid action";session.recovered();continue;}
    String sig=ActionVerifier.signature(action);
    if(sig.equals(previousAction)&&ActionVerifier.sameScreen(previousTree,tree)){failure="loop "+sig;session.recovered();callback.status("↻ LUNA mendeteksi pengulangan. Mengganti strategi.");if(!pause(450))return;continue;}
    if(plan.optBoolean("confirm",false)||isSensitive(action)){
     if(!callback.confirm(plan)){String x="Baik, tindakan saya batalkan.";callback.status("Tindakan dibatalkan.");callback.speak(x);MEMORY.addAssistant(x);return;}
    }
    callback.status("⚙ "+describe(action)); session.acted(sig); previousAction=sig; previousTree=tree;
    boolean accepted=execute(action);
    if(!accepted){failure="execution failed: "+sig;session.recovered();callback.status("↻ Tindakan belum berhasil. Mencoba pendekatan lain…");if(!pause(350))return;continue;}
    if(!pause(700))return;
    String after=service.snapshot();
    if(!ActionVerifier.looksSuccessful(tree,after)){failure="no screen change: "+sig;session.recovered();callback.status("↻ Belum ada perubahan layar; saya tidak mengulang secara buta.");if(!pause(250))return;}
    else{failure="";callback.status("✓ Layar berubah. Saya verifikasi tujuan…");}
   }
   String x="Saya belum bisa memastikan tujuan selesai, jadi saya berhenti dengan aman.";callback.status("■ LUNA berhenti dengan aman.");callback.speak(x);MEMORY.addAssistant(x);
  }catch(Exception e){String x=friendlyError(e);callback.status("LUNA: "+x);callback.speak(x);MEMORY.addAssistant(x);}finally{callback.finished();}
 }
 private String operatorPrompt(String goal,OperatorState s,String pkg,String failure){return "LUNA ADALAH OPERATOR ANDROID, BUKAN CHATBOT.\nTUJUAN: "+goal+"\nPACKAGE AKTIF: "+pkg+"\nTINDAKAN SEBELUMNYA: "+s.lastAction()+"\nGAGAL: "+failure+"\nSESSION: "+s.summary()+"\n\nKERJAKAN TUJUAN PENGGUNA SAMPAI SELESAI. Gunakan accessibility tree dan screenshot sebagai sensor. Pilih tepat satu action jika masih ada pekerjaan. Setelah action, amati layar lagi. Jangan menebak target; gunakan text, content description, resource id atau koordinat hanya bila perlu. Jika target belum terlihat, scroll/swipe/cari. Jika action gagal, ganti strategi. Jika tujuan sudah tercapai, kembalikan done=true, actions=[] dan speak=hasil yang singkat. done=true WAJIB didasarkan pada bukti layar saat ini, bukan asumsi bahwa action terakhir berhasil. Tindakan sensitif seperti kirim, hapus, beli, bayar, transfer, OTP/PIN/password wajib confirm=true. Percakapan biasa: done=true dan actions=[].";}
 private boolean isSensitive(JSONObject a){String t=a.optString("type","").toUpperCase(Locale.ROOT),v=(a.optString("value","")+" "+a.optString("label","")+" "+a.optString("direction","")).toLowerCase(Locale.ROOT);return t.contains("DELETE")||t.contains("SEND")||t.contains("PURCHASE")||t.contains("TRANSFER")||t.contains("PAY")||(t.equals("TYPE")&&(v.contains("otp")||v.contains("password")||v.contains("pin")||v.contains("kode verifikasi")))||(t.startsWith("CLICK")&&(v.contains("kirim")||v.contains("hapus")||v.contains("beli")||v.contains("bayar")||v.contains("transfer")||v.contains("setuju")||v.contains("pesan sekarang")));}
 private boolean runLocalCommand(String raw){String c=raw.toLowerCase(Locale.ROOT).trim();DeviceExecutor d=new DeviceExecutor(activity);try{if(c.equals("home")||c.equals("beranda")||c.contains("ke beranda")){if(service.globalHome()){done("Beranda dibuka.");return true;}}if(c.equals("back")||c.equals("kembali")){if(service.globalBack()){done("Kembali.");return true;}}if(c.contains("notifikasi")){if(service.globalNotifications()){done("Notifikasi dibuka.");return true;}}if(c.contains("pengaturan cepat")){if(service.globalQuickSettings()){done("Pengaturan cepat dibuka.");return true;}}if(c.equals("pengaturan")||c.equals("settings")||c.contains("buka pengaturan")){if(d.openSettings("")){done("Pengaturan dibuka.");return true;}}if(c.contains("mute")||c.contains("senyap")){if(d.mute()){done("Suara dimatikan.");return true;}}if(c.matches(".*volume\\s+\\d+.*")){int n=Integer.parseInt(c.replaceAll("[^0-9]",""));if(d.setVolume(Math.max(0,Math.min(100,n)))){done("Volume diatur ke "+n+" persen.");return true;}}if(c.startsWith("buka ")||c.startsWith("bukakan ")){String target=c.replaceFirst("^buka(?:kan)?\\s+","").trim();if(target.startsWith("http://")||target.startsWith("https://")){if(d.openUrl(target)){done("Alamat web dibuka.");return true;}}else if(d.openApp("",target)){done(target+" dibuka.");return true;}}}catch(Exception ignored){}return false;}
 private void done(String x){callback.status("✓ "+x);callback.speak(x);MEMORY.addAssistant(x);}
 private boolean execute(JSONObject a){String t=a.optString("type");DeviceExecutor d=new DeviceExecutor(activity);if(t.equals("OPEN_APP"))return d.openApp(a.optString("package"),a.optString("label"));if(t.equals("CLICK_TEXT"))return service.clickText(a.optString("value"));if(t.equals("CLICK_DESC"))return service.clickDescription(a.optString("value"));if(t.equals("CLICK_ID"))return service.clickId(a.optString("value"));if(t.equals("CLICK_POINT"))return service.clickPoint((float)a.optDouble("x"),(float)a.optDouble("y"));if(t.equals("LONG_CLICK_TEXT"))return service.longClickText(a.optString("value"));if(t.equals("LONG_CLICK_DESC"))return service.longClickDescription(a.optString("value"));if(t.equals("LONG_CLICK_ID"))return service.longClickId(a.optString("value"));if(t.equals("LONG_CLICK_POINT"))return service.longClickPoint((float)a.optDouble("x"),(float)a.optDouble("y"));if(t.equals("TYPE"))return service.typeText(a.optString("value"));if(t.equals("SCROLL"))return service.scroll(a.optString("direction"));if(t.equals("SWIPE"))return service.swipe(a.optString("direction"),a.optInt("distance",700),a.optInt("durationMs",500));if(t.equals("BACK"))return service.globalBack();if(t.equals("HOME"))return service.globalHome();if(t.equals("RECENTS"))return service.globalRecents();if(t.equals("NOTIFICATIONS"))return service.globalNotifications();if(t.equals("QUICK_SETTINGS"))return service.globalQuickSettings();if(t.equals("OPEN_URL"))return d.openUrl(a.optString("value"));if(t.equals("OPEN_SETTINGS"))return d.openSettings(a.optString("value"));if(t.equals("SET_VOLUME"))return d.setVolume(a.optInt("level",50));if(t.equals("MUTE"))return d.mute();if(t.equals("WAIT")){d.delay(Math.min(5000,Math.max(100,a.optLong("delayMs",500))));return !service.isStopped();}return false;}
 private boolean pause(long ms){long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(service.isStopped())return false;try{Thread.sleep(50);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}}return true;}
 private String capture()throws InterruptedException{AtomicReference<String> r=new AtomicReference<>();CountDownLatch l=new CountDownLatch(1);service.captureScreen(Runnable::run,b->{r.set(b);l.countDown();});l.await(8,TimeUnit.SECONDS);return r.get();}
 private String describe(JSONObject a){String t=a.optString("type");if(t.contains("CLICK"))return t+" → "+a.optString("value");if(t.equals("TYPE"))return "Mengetik → "+a.optString("value");if(t.equals("OPEN_APP"))return "Membuka "+a.optString("label",a.optString("package"));if(t.equals("SWIPE")||t.equals("SCROLL"))return t+" "+a.optString("direction");if(t.equals("SET_VOLUME"))return "Volume → "+a.optInt("level",50)+"%";return t;}
 private String friendlyError(Exception e){String m=e.getMessage()==null?"kendala tidak diketahui":e.getMessage();if(m.contains("API key Gemini")||m.contains("Batas penggunaan Gemini")||m.contains("Permintaan Gemini")||m.contains("Server Gemini"))return m;if(m.contains("Gemini HTTP 401")||m.contains("Gemini HTTP 403"))return "API key Gemini ditolak.";return "Terjadi kendala: "+m;}
}
