package com.imo.operator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/**
 * Premium animated hologram face for IMO.
 * Generic synthetic face only: no identity or biometric recognition.
 */
public final class IMOHologramView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), stroke=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path face=new Path(); private final RectF oval=new RectF(); private final Random random=new Random(7);
    private final float[] px=new float[90],py=new float[90],ps=new float[90]; private final long start=SystemClock.uptimeMillis();
    private boolean speaking,listening,thinking; private float pulse;
    public IMOHologramView(Context c){super(c);init();} public IMOHologramView(Context c,AttributeSet a){super(c,a);init();}
    private void init(){setLayerType(View.LAYER_TYPE_SOFTWARE,null);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.ROUND);text.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));for(int i=0;i<px.length;i++){px[i]=random.nextFloat();py[i]=random.nextFloat();ps[i]=.35f+random.nextFloat()*1.8f;}setBackgroundColor(Color.rgb(3,6,18));}
    public void setSpeaking(boolean v){speaking=v;invalidate();} public void setListening(boolean v){listening=v;invalidate();} public void setThinking(boolean v){thinking=v;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),t=(SystemClock.uptimeMillis()-start)/1000f;pulse=(float)(.5+.5*Math.sin(t*2.2));
        p.setShader(new RadialGradient(w*.5f,h*.40f,Math.min(w,h)*.58f,new int[]{Color.argb(90,70,210,255),Color.argb(35,40,70,220),Color.TRANSPARENT},new float[]{0,.42f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        drawParticles(c,w,h,t);drawGrid(c,w,h,t);drawOrbit(c,w,h,t);drawFace(c,w,h,t);drawStatus(c,w,h);postInvalidateDelayed(33);
    }
    private void drawParticles(Canvas c,float w,float h,float t){for(int i=0;i<px.length;i++){float x=px[i]*w+(float)Math.sin(t*.45+i)*7f,y=(py[i]*h+t*(7+ps[i]*3))%h;p.setColor(Color.argb((int)(45+70*pulse),90,225,255));c.drawCircle(x,y,ps[i],p);}}
    private void drawGrid(Canvas c,float w,float h,float t){stroke.setStrokeWidth(1);for(int i=0;i<12;i++){float y=h*.72f+i*h*.028f+(float)Math.sin(t+i)*2;stroke.setColor(Color.argb(20,70,220,255));c.drawLine(w*.08f,y,w*.92f,y,stroke);}for(int i=0;i<9;i++){float x=w*.12f+i*w*.095f;stroke.setColor(Color.argb(14,120,110,255));c.drawLine(x,h*.70f,x+w*.08f,h*.98f,stroke);}}
    private void drawOrbit(Canvas c,float w,float h,float t){float cx=w*.5f,cy=h*.43f,rx=w*.36f,ry=h*.30f;stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2.2f);stroke.setColor(Color.argb(100,70,220,255));stroke.setShadowLayer(12,0,0,Color.rgb(40,190,255));oval.set(cx-rx,cy-ry,cx+rx,cy+ry);c.drawOval(oval,stroke);stroke.clearShadowLayer();float a=t*.75f,x=cx+(float)Math.cos(a)*rx,y=cy+(float)Math.sin(a)*ry;p.setColor(Color.argb(220,120,245,255));p.setShadowLayer(14,0,0,Color.rgb(30,190,255));c.drawCircle(x,y,5+pulse*2,p);p.clearShadowLayer();}
    private void drawFace(Canvas c,float w,float h,float t){
        float cx=w*.5f + (float)Math.sin(t*.42f)*2f;
        float cy=h*.40f + (float)Math.sin(t*1.05f)*3f;
        float fw=Math.min(w*.70f,360f), fh=Math.min(h*.78f,520f);
        // Volumetric ghost layers create real depth instead of a flat icon.
        for(int g=5;g>=1;g--) drawFaceLayer(c,cx+(g-3)*2.4f,cy,fw,fh,t,.045f*g);
        drawFaceLayer(c,cx,cy,fw,fh,t,1f);
        drawFaceMesh(c,cx,cy,fw,fh,t);
        float scan=(t*48)%fh;
        stroke.setStrokeWidth(1f);
        stroke.setColor(Color.argb(48,100,240,255));
        for(int i=-3;i<4;i++){
            float y=cy-fh*.49f+scan+i*10;
            c.drawLine(cx-fw*.52f,y,cx+fw*.52f,y,stroke);
        }
    }

    private void drawFaceLayer(Canvas c,float cx,float cy,float fw,float fh,float t,float alpha){
        face.reset();
        face.moveTo(cx,cy-fh*.53f);
        face.cubicTo(cx-fw*.22f,cy-fh*.56f,cx-fw*.44f,cy-fh*.45f,cx-fw*.49f,cy-fh*.17f);
        face.cubicTo(cx-fw*.54f,cy+fh*.12f,cx-fw*.46f,cy+fh*.35f,cx-fw*.29f,cy+fh*.49f);
        face.cubicTo(cx-fw*.16f,cy+fh*.59f,cx-fw*.06f,cy+fh*.63f,cx,cy+fh*.65f);
        face.cubicTo(cx+fw*.06f,cy+fh*.63f,cx+fw*.16f,cy+fh*.59f,cx+fw*.29f,cy+fh*.49f);
        face.cubicTo(cx+fw*.46f,cy+fh*.35f,cx+fw*.54f,cy+fh*.12f,cx+fw*.49f,cy-fh*.17f);
        face.cubicTo(cx+fw*.44f,cy-fh*.45f,cx+fw*.22f,cy-fh*.56f,cx,cy-fh*.53f);
        face.close();

        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx-fw*.05f,cy-fh*.05f,fw*.62f,
                new int[]{Color.argb((int)(54*alpha),95,235,255),Color.argb((int)(24*alpha),35,145,235),Color.argb((int)(3*alpha),80,50,180)},
                new float[]{0,.48f,1f},Shader.TileMode.CLAMP));
        p.setShadowLayer(30,0,0,Color.rgb(20,175,255));
        c.drawPath(face,p); p.clearShadowLayer(); p.setShader(null);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.0f);
        stroke.setColor(Color.argb((int)(185*alpha),105,240,255));
        stroke.setShadowLayer(11,0,0,Color.rgb(20,190,255));
        c.drawPath(face,stroke); stroke.clearShadowLayer();

        float eyeY=cy-fh*.075f;
        float eyeX=fw*.205f;
        drawRealisticEye(c,cx-eyeX,eyeY,fw*.115f,fh*.055f,t,alpha);
        drawRealisticEye(c,cx+eyeX,eyeY,fw*.115f,fh*.055f,t,alpha);

        // Eyebrows and subtle cheek/temple contours.
        stroke.setStrokeWidth(2.2f);
        stroke.setColor(Color.argb((int)(130*alpha),85,225,255));
        Path q=new Path();
        q.moveTo(cx-fw*.315f,eyeY-fh*.095f); q.cubicTo(cx-fw*.22f,eyeY-fh*.145f,cx-fw*.12f,eyeY-fh*.145f,cx-fw*.055f,eyeY-fh*.08f);
        q.moveTo(cx+fw*.055f,eyeY-fh*.08f); q.cubicTo(cx+fw*.12f,eyeY-fh*.145f,cx+fw*.22f,eyeY-fh*.145f,cx+fw*.315f,eyeY-fh*.095f);
        q.moveTo(cx-fw*.43f,eyeY+fh*.09f); q.quadTo(cx-fw*.34f,cy+fh*.02f,cx-fw*.30f,cy+fh*.13f);
        q.moveTo(cx+fw*.43f,eyeY+fh*.09f); q.quadTo(cx+fw*.34f,cy+fh*.02f,cx+fw*.30f,cy+fh*.13f);
        c.drawPath(q,stroke);

        // Nose bridge, tip and nostrils.
        stroke.setStrokeWidth(2.0f);
        Path nose=new Path();
        nose.moveTo(cx-fw*.025f,eyeY+fh*.02f);
        nose.cubicTo(cx-fw*.05f,cy+fh*.08f,cx-fw*.06f,cy+fh*.16f,cx-fw*.11f,cy+fh*.205f);
        nose.quadTo(cx,cy+fh*.25f,cx+fw*.11f,cy+fh*.205f);
        nose.cubicTo(cx+fw*.06f,cy+fh*.16f,cx+fw*.05f,cy+fh*.08f,cx+fw*.025f,eyeY+fh*.02f);
        c.drawPath(nose,stroke);

        // Animated mouth with upper/lower lip and speaking aperture.
        float mouthY=cy+fh*.285f;
        float open=speaking ? .025f+.075f*(.5f+.5f*(float)Math.sin(t*12.5f)) : .010f;
        stroke.setStrokeWidth(2.3f);
        stroke.setColor(Color.argb((int)(190*alpha),115,250,255));
        Path lips=new Path();
        lips.moveTo(cx-fw*.17f,mouthY);
        lips.cubicTo(cx-fw*.09f,mouthY-fh*.018f,cx-fw*.035f,mouthY+fh*.008f,cx,mouthY-fh*.006f);
        lips.cubicTo(cx+fw*.035f,mouthY+fh*.008f,cx+fw*.09f,mouthY-fh*.018f,cx+fw*.17f,mouthY);
        lips.cubicTo(cx+fw*.10f,mouthY+fh*(.035f+open),cx-fw*.10f,mouthY+fh*(.035f+open),cx-fw*.17f,mouthY);
        c.drawPath(lips,stroke);

        // Hairline and digital strands.
        stroke.setStrokeWidth(1.6f);
        stroke.setColor(Color.argb((int)(105*alpha),75,215,255));
        Path hair=new Path();
        hair.moveTo(cx-fw*.43f,cy-fh*.27f);
        hair.cubicTo(cx-fw*.36f,cy-fh*.48f,cx-fw*.17f,cy-fh*.54f,cx-fw*.03f,cy-fh*.48f);
        hair.cubicTo(cx+fw*.12f,cy-fh*.55f,cx+fw*.34f,cy-fh*.47f,cx+fw*.43f,cy-fh*.27f);
        c.drawPath(hair,stroke);
        for(int i=-4;i<=4;i++){
            Path strand=new Path();
            float sx=cx+i*fw*.075f;
            strand.moveTo(sx,cy-fh*.48f);
            strand.cubicTo(sx+fw*.025f,cy-fh*.34f,sx-fw*.02f,cy-fh*.25f,sx+fw*.01f,cy-fh*.14f);
            c.drawPath(strand,stroke);
        }

        // Neck and shoulder holographic silhouette.
        Path neck=new Path();
        neck.moveTo(cx-fw*.16f,cy+fh*.55f); neck.cubicTo(cx-fw*.18f,cy+fh*.68f,cx-fw*.28f,cy+fh*.72f,cx-fw*.40f,cy+fh*.84f);
        neck.moveTo(cx+fw*.16f,cy+fh*.55f); neck.cubicTo(cx+fw*.18f,cy+fh*.68f,cx+fw*.28f,cy+fh*.72f,cx+fw*.40f,cy+fh*.84f);
        c.drawPath(neck,stroke);
    }

    private void drawRealisticEye(Canvas c,float x,float y,float rx,float ry,float t,float alpha){
        stroke.setStrokeWidth(2.2f);
        stroke.setColor(Color.argb((int)(185*alpha),105,240,255));
        RectF lid=new RectF(x-rx,y-ry,x+rx,y+ry);
        c.drawOval(lid,stroke);
        float blink=(float)((t+1.7)%5.4);
        float openness=(blink>.0f&&blink<.16f)?.12f:1f;
        RectF iris=new RectF(x-rx*.34f,y-ry*.34f*openness,x+rx*.34f,y+ry*.34f*openness);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int)(215*alpha),85,230,255));
        p.setShadowLayer(15,0,0,Color.rgb(25,205,255));
        c.drawOval(iris,p); p.clearShadowLayer();
        stroke.setStrokeWidth(1.1f);
        stroke.setColor(Color.argb((int)(120*alpha),185,255,255));
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4.0+t*.18;
            c.drawLine(x+(float)Math.cos(a)*rx*.12f,y+(float)Math.sin(a)*ry*.12f,
                       x+(float)Math.cos(a)*rx*.32f,y+(float)Math.sin(a)*ry*.32f,stroke);
        }
        p.setColor(Color.WHITE); c.drawCircle(x-rx*.08f,y-ry*.08f,Math.max(1.5f,rx*.045f),p);
    }

    private void drawFaceMesh(Canvas c,float cx,float cy,float fw,float fh,float t){
        stroke.setStrokeWidth(.8f);
        stroke.setColor(Color.argb(42,100,235,255));
        for(int i=-5;i<=5;i++){
            float x=cx+i*fw*.075f;
            Path v=new Path();
            v.moveTo(x,cy-fh*.40f);
            v.cubicTo(x-fw*.025f,cy-fh*.15f,x+fw*.025f,cy+fh*.12f,x,cy+fh*.48f);
            c.drawPath(v,stroke);
        }
        for(int i=-3;i<=4;i++){
            float y=cy+i*fh*.115f;
            Path q=new Path();
            q.moveTo(cx-fw*.40f,y);
            q.cubicTo(cx-fw*.18f,y-fh*.025f,cx+fw*.18f,y-fh*.025f,cx+fw*.40f,y);
            c.drawPath(q,stroke);
        }
        float glow=(float)(.5+.5*Math.sin(t*2.6));
        p.setColor(Color.argb((int)(90+80*glow),110,245,255));
        p.setShadowLayer(18,0,0,Color.rgb(40,215,255));
        c.drawCircle(cx,cy-fh*.58f,2.5f+2*glow,p); p.clearShadowLayer();
    }

    private void drawEye(Canvas c,float x,float y,float rx,float scale,float alpha){stroke.setStrokeWidth(2.4f);stroke.setColor(Color.argb((int)(190*alpha),100,240,255));oval.set(x-rx,y-rx*.42f*scale,x+rx,y+rx*.42f*scale);c.drawOval(oval,stroke);p.setColor(Color.argb((int)(235*alpha),120,250,255));p.setShadowLayer(14,0,0,Color.rgb(30,205,255));c.drawCircle(x,y,rx*.22f,p);p.clearShadowLayer();}
    private void drawStatus(Canvas c,float w,float h){
        // Status text is rendered by MainActivity below the hologram.
        // Keep only a subtle breathing glow at the hologram base.
        float t=(SystemClock.uptimeMillis()-start)/1000f;
        float glow=(float)(.5+.5*Math.sin(t*2.4));
        p.setColor(Color.argb((int)(35+35*glow),80,225,255));
        p.setShadowLayer(28,0,0,Color.rgb(20,190,255));
        c.drawOval(new RectF(w*.28f,h*.82f,w*.72f,h*.91f),p);
        p.clearShadowLayer();
    }
}