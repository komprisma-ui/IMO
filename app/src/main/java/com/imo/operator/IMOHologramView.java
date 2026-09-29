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
import android.view.View;
import java.util.Random;

/**
 * IMO premium holographic avatar.
 * Uses the approved IMO visual master as the avatar surface and adds live
 * holographic motion layers: scan, particles, parallax, breathing glow and
 * speaking/listening/thinking states. No biometric identification.
 */
public final class IMOHologramView extends View {
    private static final String MASTER_B64="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";
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
            byte[] data=Base64.decode(MASTER_B64,Base64.DEFAULT);
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
        float maxW=w*.92f;
        float maxH=Math.min(h*.88f,maxW*0.943f);
        float bw=Math.min(maxW,maxH/0.943f);
        float bh=bw*0.943f;
        float drift=(float)Math.sin(t*.55f)*2.2f;
        float top=Math.max(0,h*.015f)+(float)Math.sin(t*.8f)*1.5f;
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