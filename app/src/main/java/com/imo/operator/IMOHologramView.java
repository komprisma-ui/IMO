package com.imo.operator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Base64;
import java.io.InputStream;
import android.view.View;
import java.util.Random;

/**
 * IMO premium holographic avatar.
 * Uses the approved IMO visual master as the avatar surface and adds live
 * holographic motion layers: scan, particles, parallax, breathing glow and
 * speaking/listening/thinking states. No biometric identification.
 */
public final class IMOHologramView extends View {
    private static final String[] TARGET_CHUNKS={"imo_target_00","imo_target_01","imo_target_02","imo_target_03","imo_target_04","imo_target_05","imo_target_06","imo_target_07","imo_target_08","imo_target_09"};
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random=new Random(19);
    private Bitmap master;
    private final float[] px=new float[70], py=new float[70], ps=new float[70];
    private final long start=SystemClock.uptimeMillis();
    private boolean speaking,listening,thinking;

    public IMOHologramView(Context c){super(c);init();}
    public IMOHologramView(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        try{
            StringBuilder b64=new StringBuilder(52000);
            for(String name:TARGET_CHUNKS){
                int id=getResources().getIdentifier(name,"raw",getContext().getPackageName());
                if(id==0) throw new IllegalStateException("missing hologram chunk: "+name);
                InputStream in=getResources().openRawResource(id);
                byte[] buf=new byte[4096]; int n;
                while((n=in.read(buf))>0) b64.append(new String(buf,0,n,java.nio.charset.StandardCharsets.US_ASCII));
                in.close();
            }
            byte[] data=Base64.decode(b64.toString(),Base64.DEFAULT);
            master=BitmapFactory.decodeByteArray(data,0,data.length);
        }catch(Exception ignored){master=null;}
        for(int i=0;i<px.length;i++){
            px[i]=random.nextFloat(); py[i]=random.nextFloat();
            ps[i]=.6f+random.nextFloat()*2.4f;
        }
        setBackgroundColor(Color.rgb(1,5,16));
    }

    public void setSpeaking(boolean v){speaking=v;invalidate();}
    public void setListening(boolean v){listening=v;invalidate();}
    public void setThinking(boolean v){thinking=v;invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        final float w=getWidth(),h=getHeight();
        final float t=(SystemClock.uptimeMillis()-start)/1000f;
        final float pulse=.5f+.5f*(float)Math.sin(t*2.2f);
        c.drawColor(Color.rgb(1,5,16));

        // Deep blue atmospheric bloom behind the avatar.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new android.graphics.RadialGradient(w*.5f,h*.42f,Math.min(w,h)*.62f,
                new int[]{Color.argb(80,20,125,255),Color.argb(30,10,70,180),Color.TRANSPARENT},
                new float[]{0,.48f,1},android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(null);

        drawParticles(c,w,h,t,pulse);
        drawAvatar(c,w,h,t,pulse);
        drawInterfaceRings(c,w,h,t,pulse);
        drawScan(c,w,h,t);
        drawStateGlow(c,w,h,t,pulse);
        postInvalidateDelayed(33);
    }

    private void drawParticles(Canvas c,float w,float h,float t,float pulse){
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<px.length;i++){
            float x=px[i]*w+(float)Math.sin(t*.7+i)*5f;
            float y=(py[i]*h+t*(5f+ps[i]*2.5f))%h;
            int a=(int)(30+85*pulse);
            p.setColor(Color.argb(a,80,210,255));
            c.drawCircle(x,y,ps[i],p);
        }
    }

    private RectF avatarRect(float w,float h,float t){
        float maxW=w*.96f;
        float maxH=Math.min(h*.82f,maxW*0.897f);
        float bw=Math.min(maxW,maxH/0.897f);
        float bh=bw*0.897f;
        float drift=(float)Math.sin(t*.55f)*2.2f;
        float top=Math.max(0,h*.018f)+(float)Math.sin(t*.8f)*1.5f;
        return new RectF((w-bw)/2f+drift,top,(w+bw)/2f+drift,top+bh);
    }

    private void drawAvatar(Canvas c,float w,float h,float t,float pulse){
        if(master==null)return;
        RectF dst=avatarRect(w,h,t);

        // Soft hologram echo gives the static master image depth.
        glow.setAlpha((int)(38+24*pulse));
        glow.setColor(Color.rgb(20,180,255));
        glow.setShadowLayer(34+12*pulse,0,0,Color.rgb(0,170,255));
        c.drawBitmap(master,null,dst,glow);
        glow.clearShadowLayer();

        float sx=(float)Math.sin(t*.42f)*1.6f;
        RectF shifted=new RectF(dst.left+sx,dst.top,dst.right+sx,dst.bottom);
        p.setAlpha(245);
        c.drawBitmap(master,null,shifted,p);
        p.setAlpha(255);

        // Fine cyan edge aura.
        glow.setAlpha((int)(18+18*pulse));
        glow.setColor(Color.rgb(70,230,255));
        glow.setShadowLayer(22,0,0,Color.rgb(20,190,255));
        c.drawBitmap(master,null,new RectF(dst.left-1,dst.top-1,dst.right+1,dst.bottom+1),glow);
        glow.clearShadowLayer();
        glow.setAlpha(255);
    }

    private void drawInterfaceRings(Canvas c,float w,float h,float t,float pulse){
        float cx=w*.5f;
        float cy=h*.55f;
        float rx=w*.41f;
        float ry=Math.min(h*.29f,w*.39f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(Color.argb((int)(45+45*pulse),40,180,255));
        p.setShadowLayer(12,0,0,Color.rgb(20,170,255));
        RectF r=new RectF(cx-rx,cy-ry,cx+rx,cy+ry);
        c.drawOval(r,p);
        p.clearShadowLayer();

        float a=t*.55f;
        float x=cx+(float)Math.cos(a)*rx;
        float y=cy+(float)Math.sin(a)*ry;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(220,110,245,255));
        p.setShadowLayer(15,0,0,Color.rgb(20,200,255));
        c.drawCircle(x,y,3.5f+2f*pulse,p);
        p.clearShadowLayer();

        // Hologram platform glow under the shoulders.
        p.setColor(Color.argb((int)(35+35*pulse),40,210,255));
        p.setShadowLayer(25,0,0,Color.rgb(20,190,255));
        c.drawOval(new RectF(w*.27f,h*.73f,w*.73f,h*.79f),p);
        p.clearShadowLayer();
    }

    private void drawScan(Canvas c,float w,float h,float t){
        float y=(h*.10f+(t*55f)%(h*.67f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(22,100,230,255));
        c.drawRect(w*.06f,y,w*.94f,y+2f,p);
        p.setColor(Color.argb(12,80,210,255));
        for(int i=0;i<7;i++) c.drawRect(w*.12f,y+i*4,w*.88f,y+i*4+1,p);
    }

    private void drawStateGlow(Canvas c,float w,float h,float t,float pulse){
        float cx=w*.5f, cy=h*.55f;
        int base=listening?95:(speaking?115:(thinking?75:35));
        float wave=.5f+.5f*(float)Math.sin(t*(speaking?9.5f:3.0f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int)(base+45*wave),50,220,255));
        p.setShadowLayer(32,0,0,Color.rgb(20,190,255));
        c.drawCircle(cx,cy,3+2*pulse,p);
        p.clearShadowLayer();
    }
}