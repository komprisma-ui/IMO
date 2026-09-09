package com.imo.operator;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
import java.util.Set;

public final class VoiceHelper {
    private VoiceHelper(){}
    public static void applyBest(TextToSpeech tts,Locale locale){if(tts==null)return;Set<Voice>voices=tts.getVoices();if(voices==null)return;Voice best=null;int scoreBest=Integer.MIN_VALUE;for(Voice v:voices){if(v==null||v.getLocale()==null||!v.getLocale().getLanguage().equals(locale.getLanguage()))continue;String n=v.getName()==null?"":v.getName().toLowerCase(Locale.US);int s=v.getQuality()*10;if(v.getQuality()>=Voice.QUALITY_VERY_HIGH)s+=100;if(v.getLatency()<=Voice.LATENCY_NORMAL)s+=20;if(n.contains("male")||n.contains("man")||n.contains("pria")||n.contains("laki")||n.contains("#m"))s+=250;if(n.contains("female")||n.contains("woman")||n.contains("girl")||n.contains("wanita")||n.contains("perempuan")||n.contains("#f"))s-=250;if(v.isNetworkConnectionRequired())s+=15;if(s>scoreBest){scoreBest=s;best=v;}}if(best!=null)tts.setVoice(best);}
}
