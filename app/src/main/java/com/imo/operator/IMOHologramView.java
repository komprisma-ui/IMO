package com.imo.operator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Base64;
import java.io.InputStream;
import android.view.View;
import java.util.Random;

/**
 * IMO live holographic avatar.
 *
 * The approved 600x600 avatar is rendered as a continuously animated
 * holographic surface: breathing/parallax, horizontal wave distortion,
 * chromatic ghosting, scan shimmer, moving HUD orbit and state-reactive glow.
 * This is intentionally visible even while idle; speaking/listening/thinking
 * amplify the motion. No biometric identification is performed.
 */
public final class IMOHologramView extends View {
    private static final String[] TARGET_CHUNKS={"imo_target_00","imo_target_01","imo_target_02","imo_target_03","imo_target_04","imo_target_05","imo_target_06","imo_target_07","imo_target_08","imo_target_09"};
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Random random=new Random(19);
    private Bitmap master;
    private final float[] px=new float[90],py=new float[90],ps=new float[90];
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
                if(id==0)throw new IllegalStateException("missing hologram chunk: "+name);
                InputStream in=getResources().openRawResource(id);
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))>0)b64.append(new String(buf,0,n,java.nio.charset.StandardCharsets.US_ASCII));
                in.close();
            }
            byte[] data=Base64.decode(b64.toString(),Base64.DEFAULT);
            master=BitmapFactory.decodeByteArray(data,0,data.length);
        }catch(Exception ignored){master=null;}
        for(int i=0;i<px.length;i++){
            px[i]=random.nextFloat();py[i]=random.nextFloat();
            ps[i]=.7f+random.nextFloat()*2.7f;
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
        final float pulse=.5f+.5f*(float)Math.sin(t*2.4f);
        c.drawColor(Color.rgb(1,5,16));

        p.setStyle(Paint.Style.FILL);
        p.setShader(new android.graphics.RadialGradient(w*.5f,h*.42f,Math.min(w,h)*.64f,
                new int[]{Color.argb(95,20,130,255),Color.argb(34,10,70,190),Color.TRANSPARENT},
                new float[]{0,.46f,1},android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);

        drawParticles(c,w,h,t,pulse);
        drawAvatar(c,w,h,t,pulse);
        drawInterfaceRings(c,w,h,t,pulse);
        drawScan(c,w,h,t);
        drawStateGlow(c,w,h,t,pulse);
        postInvalidateDelayed(28);
    }

    private void drawParticles(Canvas c,float w,float h,float t,float pulse){
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<px.length;i++){
            float x=px[i]*w+(float)Math.sin(t*(.55f+ps[i]*.08f)+i)*7f;
            float y=(py[i]*h+t*(7f+ps[i]*3.2f))%h;
            int a=(int)(28+105*pulse);
            p.setColor(Color.argb(a,75,215,255));
            c.drawCircle(x,y,ps[i]*(.75f+.45f*pulse),p);
        }
    }

    private RectF avatarRect(float w,float h,float t){
        float maxW=w*.96f;
        float maxH=Math.min(h*.82f,maxW*.897f);
        float bw=Math.min(maxW,maxH/.897f);
        float bh=bw*.897f;
        float breathe=1f+.012f*(float)Math.sin(t*1.55f);
        bw*=breathe;bh*=breathe;
        float drift=(float)Math.sin(t*.9f)*5.0f;
        float top=Math.max(0,h*.018f)+(float)Math.sin(t*1.15f)*3.0f;
        return new RectF((w-bw)/2f+drift,top,(w+bw)/2f+drift,top+bh);
    }

    private void drawAvatar(Canvas c,float w,float h,float t,float pulse){
        if(master==null)return;
        RectF dst=avatarRect(w,h,t);

        // Wide cyan aura.
        glow.setAlpha((int)(42+35*pulse));
        glow.setColor(Color.rgb(20,180,255));
        glow.setShadowLayer(38+16*pulse,0,0,Color.rgb(0,165,255));
        c.drawBitmap(master,null,dst,glow);
        glow.clearShadowLayer();

        // Moving chromatic ghost edges make the hologram visibly flicker.
        float ghost=(float)Math.sin(t*5.5f)*3.5f;
        p.setAlpha(42);
        c.drawBitmap(master,null,new RectF(dst.left-ghost,dst.top,dst.right-ghost,dst.bottom),p);
        p.setAlpha(35);
        c.drawBitmap(master,null,new RectF(dst.left+ghost,dst.top+1,dst.right+ghost,dst.bottom+1),p);

        // Main avatar is drawn in animated horizontal slices. Each slice
        // moves differently, producing a visible holographic wave rather than
        // a merely static picture.
        p.setAlpha(245);
        final int slices=42;
        float sliceH=dst.height()/slices;
        for(int i=0;i<slices;i++){
            float f=(float)i/slices;
            float wave=(float)Math.sin(t*3.4f+f*22f)*((speaking?5.0f:3.6f)+(listening?1.8f:0));
            float shimmer=(float)Math.sin(t*8.0f+i*.9f)*1.0f;
            float top=dst.top+i*sliceH;
            float bottom=(i==slices-1)?dst.bottom:top+sliceH+.6f;
            Rect src=new Rect(
                    0,
                    Math.max(0,(int)(master.getHeight()*f)),
                    master.getWidth(),
                    Math.min(master.getHeight(),Math.max(1,(int)(master.getHeight()*(i+1f)/slices)))
            );
            RectF d=new RectF(dst.left+wave+shimmer,top,dst.right+wave+shimmer,bottom);
            c.drawBitmap(master,src,d,p);
        }
        p.setAlpha(255);

        // A soft moving vertical energy curtain crosses the face.
        float bandX=dst.left+((t*58f)%(Math.max(1,dst.width()+120)))-60f;
        p.setShader(new android.graphics.LinearGradient(
                bandX-32,0,bandX+32,0,
                new int[]{Color.TRANSPARENT,Color.argb(55,110,240,255),Color.TRANSPARENT},
                null,android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(dst.left,dst.top,dst.right,dst.bottom,p);
        p.setShader(null);

        // Speaking visualizer around the mouth/chin area.
        float mouthPulse=.5f+.5f*(float)Math.sin(t*(speaking?13f:4f));
        if(speaking){
            float mx=(dst.left+dst.right)*.5f;
            float my=dst.top+dst.height()*.60f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.3f+1.5f*mouthPulse);
            p.setColor(Color.argb((int)(45+85*mouthPulse),90,235,255));
            p.setShadowLayer(12,0,0,Color.rgb(40,210,255));
            c.drawOval(new RectF(mx-25-6*mouthPulse,my-5,mx+25+6*mouthPulse,my+7+5*mouthPulse),p);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.FILL);
        }

        glow.setAlpha((int)(22+25*pulse));
        glow.setColor(Color.rgb(70,230,255));
        glow.setShadowLayer(25,0,0,Color.rgb(20,190,255));
        c.drawBitmap(master,null,new RectF(dst.left-1,dst.top-1,dst.right+1,dst.bottom+1),glow);
        glow.clearShadowLayer();glow.setAlpha(255);
    }

    private void drawInterfaceRings(Canvas c,float w,float h,float t,float pulse){
        float cx=w*.5f,cy=h*.55f;
        float rx=w*.41f,ry=Math.min(h*.29f,w*.39f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f);
        p.setColor(Color.argb((int)(50+55*pulse),40,185,255));
        p.setShadowLayer(14,0,0,Color.rgb(20,175,255));
        c.drawOval(new RectF(cx-rx,cy-ry,cx+rx,cy+ry),p);
        p.clearShadowLayer();

        // Two rotating orbit markers.
        for(int j=0;j<2;j++){
            float a=t*(.75f+j*.22f)+j*3.14f;
            float x=cx+(float)Math.cos(a)*rx;
            float y=cy+(float)Math.sin(a)*ry;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(235,115,245,255));
            p.setShadowLayer(16,0,0,Color.rgb(20,205,255));
            c.drawCircle(x,y,3.8f+2.2f*pulse,p);
            p.clearShadowLayer();
        }

        p.setColor(Color.argb((int)(38+45*pulse),40,210,255));
        p.setShadowLayer(28,0,0,Color.rgb(20,190,255));
        c.drawOval(new RectF(w*.25f,h*.73f,w*.75f,h*.80f),p);
        p.clearShadowLayer();
    }

    private void drawScan(Canvas c,float w,float h,float t){
        float y=(h*.08f+(t*90f)%(h*.72f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(30,100,235,255));
        c.drawRect(w*.04f,y,w*.96f,y+3f,p);
        p.setColor(Color.argb(13,80,210,255));
        for(int i=0;i<11;i++)c.drawRect(w*.09f,y+i*5,w*.91f,y+i*5+1,p);

        // Fine persistent scanlines, slowly phase-shifted.
        p.setColor(Color.argb(10,100,220,255));
        int phase=(int)(t*18f)%6;
        for(float yy=phase;yy<h*.76f;yy+=6f)c.drawRect(w*.07f,yy,w*.93f,yy+1f,p);
    }

    private void drawStateGlow(Canvas c,float w,float h,float t,float pulse){
        float cx=w*.5f,cy=h*.55f;
        int base=listening?110:(speaking?135:(thinking?85:42));
        float wave=.5f+.5f*(float)Math.sin(t*(speaking?11f:3.5f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int)(base+55*wave),50,225,255));
        p.setShadowLayer(34,0,0,Color.rgb(20,195,255));
        c.drawCircle(cx,cy,3+2.5f*pulse,p);
        p.clearShadowLayer();
    }
}