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
    private void drawFace(Canvas c,float w,float h,float t){float cx=w*.5f,bob=(float)Math.sin(t*1.15)*4,cy=h*.42f+bob,fw=Math.min(w*.58f,310),fh=Math.min(h*.56f,390);for(int g=3;g>=1;g--)drawFaceLayer(c,cx+g*2,cy,fw,fh,t,.09f/g);drawFaceLayer(c,cx,cy,fw,fh,t,1);float scan=(t*55)%fh;stroke.setStrokeWidth(1);stroke.setColor(Color.argb(75,90,240,255));for(int i=-2;i<3;i++)c.drawLine(cx-fw*.58f,cy-fh*.48f+scan+i*13,cx+fw*.58f,cy-fh*.48f+scan+i*13,stroke);}
    private void drawFaceLayer(Canvas c,float cx,float cy,float fw,float fh,float t,float alpha){face.reset();face.moveTo(cx,cy-fh*.50f);face.cubicTo(cx-fw*.35f,cy-fh*.52f,cx-fw*.54f,cy-fh*.22f,cx-fw*.48f,cy+fh*.15f);face.cubicTo(cx-fw*.40f,cy+fh*.43f,cx-fw*.18f,cy+fh*.53f,cx,cy+fh*.56f);face.cubicTo(cx+fw*.18f,cy+fh*.53f,cx+fw*.40f,cy+fh*.43f,cx+fw*.48f,cy+fh*.15f);face.cubicTo(cx+fw*.54f,cy-fh*.22f,cx+fw*.35f,cy-fh*.52f,cx,cy-fh*.50f);face.close();p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(cx-fw*.5f,cy-fh*.5f,cx+fw*.5f,cy+fh*.55f,Color.argb((int)(32*alpha),40,180,255),Color.argb((int)(5*alpha),130,80,255),Shader.TileMode.CLAMP));p.setShadowLayer(25,0,0,Color.rgb(25,180,255));c.drawPath(face,p);p.clearShadowLayer();p.setShader(null);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2.4f);stroke.setColor(Color.argb((int)(180*alpha),95,235,255));stroke.setShadowLayer(12,0,0,Color.rgb(25,185,255));c.drawPath(face,stroke);stroke.clearShadowLayer();
        float blink=((t+1.7)%5.4);float blinkScale=(blink>0&&blink<.18)? .12f:1f,eyeY=cy-fh*.08f,eyeX=fw*.19f;drawEye(c,cx-eyeX,eyeY,fw*.105f,blinkScale,alpha);drawEye(c,cx+eyeX,eyeY,fw*.105f,blinkScale,alpha);
        stroke.setStrokeWidth(2);stroke.setColor(Color.argb((int)(120*alpha),80,225,255));Path q=new Path();q.moveTo(cx-fw*.30f,eyeY-fh*.10f);q.quadTo(cx-eyeX,eyeY-fh*.15f,cx-fw*.07f,eyeY-fh*.08f);q.moveTo(cx+fw*.30f,eyeY-fh*.10f);q.quadTo(cx+eyeX,eyeY-fh*.15f,cx+fw*.07f,eyeY-fh*.08f);q.moveTo(cx,eyeY+fh*.02f);q.quadTo(cx-fw*.045f,cy+fh*.09f,cx+fw*.035f,cy+fh*.14f);c.drawPath(q,stroke);
        float mouthY=cy+fh*.29f,open=speaking ? .025f+.075f*(.5f+.5f*(float)Math.sin(t*12)):.012f;stroke.setStrokeWidth(2.2f);stroke.setColor(Color.argb((int)(175*alpha),105,245,255));Path m=new Path();m.moveTo(cx-fw*.16f,mouthY);m.quadTo(cx,mouthY+fh*(open+.025f),cx+fw*.16f,mouthY);m.quadTo(cx,mouthY+fh*(open-.008f),cx-fw*.16f,mouthY);c.drawPath(m,stroke);Path n=new Path();n.moveTo(cx-fw*.15f,cy+fh*.50f);n.lineTo(cx-fw*.28f,cy+fh*.62f);n.moveTo(cx+fw*.15f,cy+fh*.50f);n.lineTo(cx+fw*.28f,cy+fh*.62f);c.drawPath(n,stroke);
    }
    private void drawEye(Canvas c,float x,float y,float rx,float scale,float alpha){stroke.setStrokeWidth(2.4f);stroke.setColor(Color.argb((int)(190*alpha),100,240,255));oval.set(x-rx,y-rx*.42f*scale,x+rx,y+rx*.42f*scale);c.drawOval(oval,stroke);p.setColor(Color.argb((int)(235*alpha),120,250,255));p.setShadowLayer(14,0,0,Color.rgb(30,205,255));c.drawCircle(x,y,rx*.22f,p);p.clearShadowLayer();}
    private void drawStatus(Canvas c,float w,float h){String s=listening?"MENDENGARKAN":thinking?"MEMAHAMI":speaking?"BERBICARA":"IMO • ONLINE";text.setTextSize(Math.max(13,w*.035f));text.setTextAlign(Paint.Align.CENTER);text.setColor(Color.argb(210,145,245,255));c.drawText(s,w*.5f,h*.86f,text);text.setTextSize(Math.max(10,w*.025f));text.setColor(Color.argb(105,170,215,255));c.drawText("NEURAL HOLOGRAPHIC INTERFACE",w*.5f,h*.91f,text);}
}