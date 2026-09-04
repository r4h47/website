package com.maeen.carromaim;

import android.graphics.PointF;
import android.graphics.RectF;
import java.util.*;

public class FrameAnalyzer {
    public enum Side { WHITE, BLACK, UNKNOWN }
    public static class Shot {
        public final PointF striker, ghost, coin, pocket;
        public final float score; public final boolean clear;
        Shot(PointF s, PointF g, PointF c, PointF p, float sc, boolean cl){striker=s;ghost=g;coin=c;pocket=p;score=sc;clear=cl;}
    }
    public static class Result {
        public final RectF board; public final PointF striker; public final List<PointF> myCoins, allCoins;
        public final PointF[] pockets; public final List<Shot> shots; public final Side side; public final float confidence;
        Result(RectF b,PointF s,List<PointF> m,List<PointF>a,PointF[]p,List<Shot>sh,Side si,float cf){board=b;striker=s;myCoins=m;allCoins=a;pockets=p;shots=sh;side=si;confidence=cf;}
    }
    private Side lastSide=Side.UNKNOWN;

    public Result analyze(int[] px,int w,int h){
        if(px==null||px.length<w*h||w<200||h<300)return null;
        BoardGuess bg=detectBoard(px,w,h); RectF b=bg.board; float r=Math.max(8f,b.width()*0.0235f), sr=r*1.23f;
        List<PointF> black=detectCoins(px,w,h,b,r,false), white=detectCoins(px,w,h,b,r,true), red=detectRed(px,w,h,b,r);
        Side inferred=inferSide(px,w,h,b); if(inferred!=Side.UNKNOWN)lastSide=inferred;
        Side side=inferred!=Side.UNKNOWN?inferred:lastSide;
        if(side==Side.UNKNOWN)side=white.size()>=black.size()?Side.WHITE:Side.BLACK;
        List<PointF> mine=side==Side.WHITE?white:black; List<PointF> all=new ArrayList<>(); all.addAll(white);all.addAll(black);all.addAll(red);
        PointF striker=detectStriker(px,w,h,b,sr,r,all);
        List<Shot> shots=striker==null?new ArrayList<>():plan(striker,mine,all,bg.pockets,r,sr,b);
        float conf=Math.min(1f,bg.conf*.40f+(striker!=null?.25f:0)+(mine.size()>0?.20f:0)+(inferred!=Side.UNKNOWN?.15f:.04f));
        return new Result(b,striker,mine,all,bg.pockets,shots,side,conf);
    }

    private static class BoardGuess{RectF board;PointF[]pockets;float conf;BoardGuess(RectF b,PointF[]p,float c){board=b;pockets=p;conf=c;}}
    private BoardGuess detectBoard(int[]px,int w,int h){
        float y0=h*.15f,y1=h*.52f,r=Math.max(10f,w*.032f);
        PointF tl=findDark(px,w,h,w*.01f,w*.24f,y0,y1,r), tr=findDark(px,w,h,w*.76f,w*.99f,y0,y1,r);
        RectF b;PointF[]p=new PointF[4];float cf=.35f;
        if(tl!=null&&tr!=null&&Math.abs(tl.y-tr.y)<w*.10f&&tr.x-tl.x>w*.62f){
            float span=tr.x-tl.x,bw=span/.88f,l=tl.x-bw*.06f,rr=tr.x+bw*.06f,t=(tl.y+tr.y)*.5f-bw*.055f,bt=t+bw;
            if(l<0){rr-=l;l=0;} if(rr>w){float d=rr-w;l-=d;rr=w;} if(bt>h){float d=bt-h;t-=d;bt=h;}
            b=new RectF(Math.max(0,l),Math.max(0,t),Math.min(w,rr),Math.min(h,bt));
            float py=(tl.y+tr.y)*.5f,dy=py-b.top; p[0]=new PointF(tl.x,py);p[1]=new PointF(tr.x,py);p[2]=new PointF(tl.x,b.bottom-dy);p[3]=new PointF(tr.x,b.bottom-dy);cf=.95f;
        }else{
            float l=w*.025f,rr=w*.975f,bw=rr-l,t=Math.min(h*.33f,Math.max(h*.19f,h*.25f));if(t+bw>h*.94f)t=h*.94f-bw;
            b=new RectF(l,Math.max(0,t),rr,Math.min(h,t+bw));float d=b.width()*.048f;p[0]=new PointF(b.left+d,b.top+d);p[1]=new PointF(b.right-d,b.top+d);p[2]=new PointF(b.left+d,b.bottom-d);p[3]=new PointF(b.right-d,b.bottom-d);
        }
        return new BoardGuess(b,p,cf);
    }
    private PointF findDark(int[]px,int w,int h,float x0,float x1,float y0,float y1,float r){float best=0;PointF bp=null;int st=Math.max(5,Math.round(r*.45f));for(int y=(int)y0;y<y1;y+=st)for(int x=(int)x0;x<x1;x+=st){float s=darkScore(px,w,h,x,y,r);if(s>best){best=s;bp=new PointF(x,y);}}return best>=.58f?bp:null;}
    private float darkScore(int[]px,int w,int h,float cx,float cy,float r){int d=0,n=0;for(int k=0;k<16;k++){double a=k*Math.PI*2/16;for(float rr:new float[]{0,r*.45f,r*.8f}){int x=Math.round(cx+(float)Math.cos(a)*rr),y=Math.round(cy+(float)Math.sin(a)*rr);if(in(x,y,w,h)){if(luma(px[y*w+x])<72)d++;n++;}}}return n==0?0:(float)d/n;}

    private List<PointF> detectCoins(int[]px,int w,int h,RectF b,float r,boolean white){
        List<Cand>cs=new ArrayList<>();int st=Math.max(7,Math.round(r*.55f)),m=Math.round(r*2);
        for(int y=(int)b.top+m;y<b.bottom-m;y+=st)for(int x=(int)b.left+m;x<b.right-m;x+=st){if(nearCorner(x,y,b,r*4))continue;float s=coinScore(px,w,h,x,y,r,white);if(s>.53f)cs.add(new Cand(x,y,s));}
        cs.sort((a,c)->Float.compare(c.s,a.s));List<PointF>out=new ArrayList<>();float md=r*1.5f;for(Cand c:cs){boolean ok=true;for(PointF q:out)if(dist(q.x,q.y,c.x,c.y)<md){ok=false;break;}if(ok)out.add(new PointF(c.x,c.y));if(out.size()>=12)break;}return out;
    }
    private float coinScore(int[]px,int w,int h,float cx,float cy,float r,boolean white){int good=0;float center=lumAt(px,w,h,cx,cy);for(int k=0;k<16;k++){double a=k*Math.PI*2/16;float li=lumAt(px,w,h,cx+(float)Math.cos(a)*r*.62f,cy+(float)Math.sin(a)*r*.62f),lo=lumAt(px,w,h,cx+(float)Math.cos(a)*r*1.42f,cy+(float)Math.sin(a)*r*1.42f);if(white?(li>lo+20&&li>155&&center>150):(li<lo-22&&li<125&&center<130))good++;}float radial=good/16f,surr=0;for(int k=0;k<8;k++){double a=k*Math.PI*2/8;surr+=lumAt(px,w,h,cx+(float)Math.cos(a)*r*1.55f,cy+(float)Math.sin(a)*r*1.55f);}surr/=8f;float con=white?center-surr:surr-center;return radial*.78f+clamp((con-10)/55f)*.22f;}
    private List<PointF> detectRed(int[]px,int w,int h,RectF b,float r){List<Cand>cs=new ArrayList<>();int st=Math.max(7,Math.round(r*.6f));for(int y=(int)(b.top+r*2);y<b.bottom-r*2;y+=st)for(int x=(int)(b.left+r*2);x<b.right-r*2;x+=st){int c=px[y*w+x],rr=(c>>16)&255,g=(c>>8)&255,bb=c&255;if(rr>150&&rr>g*1.35f&&rr>bb*1.35f&&sat(c)>.35f){int n=0;for(int k=0;k<8;k++){double a=k*Math.PI*2/8;int cc=pixel(px,w,h,x+(float)Math.cos(a)*r*.55f,y+(float)Math.sin(a)*r*.55f),r2=(cc>>16)&255,g2=(cc>>8)&255,b2=cc&255;if(r2>135&&r2>g2*1.25f&&r2>b2*1.25f)n++;}if(n>=5)cs.add(new Cand(x,y,n/8f));}}cs.sort((a,c)->Float.compare(c.s,a.s));List<PointF>o=new ArrayList<>();for(Cand c:cs){boolean ok=true;for(PointF q:o)if(dist(q.x,q.y,c.x,c.y)<r*1.5f){ok=false;break;}if(ok)o.add(new PointF(c.x,c.y));if(o.size()>=2)break;}return o;}

    private Side inferSide(int[]px,int w,int h,RectF b){float y0=Math.max(0,b.top-h*.17f),y1=Math.max(y0+10,b.top-h*.015f),r=Math.max(8,w*.025f),bw=0,bb=0;int st=Math.max(6,Math.round(r*.55f));for(int y=(int)y0;y<y1;y+=st)for(int x=(int)(w*.05f);x<w*.34f;x+=st){bw=Math.max(bw,hudScore(px,w,h,x,y,r,true));bb=Math.max(bb,hudScore(px,w,h,x,y,r,false));}if(bw>.60f&&bw>bb+.07f)return Side.WHITE;if(bb>.60f&&bb>bw+.07f)return Side.BLACK;return Side.UNKNOWN;}
    private float hudScore(int[]px,int w,int h,float cx,float cy,float r,boolean white){int good=0,n=0;for(int k=0;k<12;k++){double a=k*Math.PI*2/12;for(float rr:new float[]{0,r*.45f,r*.78f}){int c=pixel(px,w,h,cx+(float)Math.cos(a)*rr,cy+(float)Math.sin(a)*rr);float l=luma(c),s=sat(c);if(white?(l>185&&s<.35f):(l<90))good++;n++;}}return n==0?0:(float)good/n;}

    private PointF detectStriker(int[]px,int w,int h,RectF b,float sr,float cr,List<PointF>coins){float bw=b.width(),yb=b.bottom-bw*.115f,y0=yb-bw*.038f,y1=yb+bw*.038f,x0=b.left+bw*.18f,x1=b.right-bw*.18f,best=-1;PointF bp=null;int st=Math.max(5,Math.round(cr*.4f));for(int y=(int)y0;y<y1;y+=st)for(int x=(int)x0;x<x1;x+=st){boolean close=false;for(PointF q:coins)if(dist(x,y,q.x,q.y)<cr*1.35f){close=true;break;}if(close)continue;float s=strikerScore(px,w,h,x,y,sr);if(s>best){best=s;bp=new PointF(x,y);}}return best>=.34f?bp:null;}
    private float strikerScore(int[]px,int w,int h,float cx,float cy,float r){float sym=0,out=0;for(int k=0;k<16;k++){double a=k*Math.PI*2/16;float li=lumAt(px,w,h,cx+(float)Math.cos(a)*r*.55f,cy+(float)Math.sin(a)*r*.55f),lo=lumAt(px,w,h,cx+(float)Math.cos(a)*r*1.35f,cy+(float)Math.sin(a)*r*1.35f);sym+=Math.min(80,Math.abs(li-lo));out+=lo;}sym/=16;out/=16;float con=Math.abs(lumAt(px,w,h,cx,cy)-out);return clamp((sym-10)/45f)*.65f+clamp((con-8)/60f)*.35f;}

    private List<Shot> plan(PointF s,List<PointF>mine,List<PointF>all,PointF[]pockets,float r,float sr,RectF b){List<Shot>o=new ArrayList<>();for(PointF c:mine)for(PointF p:pockets){float dx=p.x-c.x,dy=p.y-c.y,len=(float)Math.hypot(dx,dy);if(len<r*4)continue;float ux=dx/len,uy=dy/len;PointF g=new PointF(c.x-ux*(r+sr),c.y-uy*(r+sr));if(!b.contains(g.x,g.y))continue;float ax=g.x-s.x,ay=g.y-s.y,alen=(float)Math.hypot(ax,ay);if(alen<1)continue;float cos=(ax/alen)*ux+(ay/alen)*uy;cos=Math.max(-1,Math.min(1,cos));float cut=(float)Math.toDegrees(Math.acos(cos));if(cut>82)continue;boolean clear=segClear(s,g,all,c,r*1.75f)&&segClear(c,p,all,c,r*1.85f);float sc=1-.50f*(cut/82f)-.22f*Math.min(1,(alen+len)/(b.width()*1.65f))-(clear?0:.38f);o.add(new Shot(new PointF(s.x,s.y),g,new PointF(c.x,c.y),new PointF(p.x,p.y),sc,clear));}o.sort((a,c)->Float.compare(c.score,a.score));List<Shot>top=new ArrayList<>();for(Shot sh:o){if(sh.score<.05f)continue;top.add(sh);if(top.size()>=6)break;}return top;}
    private boolean segClear(PointF a,PointF b,List<PointF>obs,PointF ignore,float clear){for(PointF p:obs){if(ignore!=null&&dist(p.x,p.y,ignore.x,ignore.y)<clear*.55f)continue;if(pointSeg(p,a,b)<clear)return false;}return true;}
    private float pointSeg(PointF p,PointF a,PointF b){float vx=b.x-a.x,vy=b.y-a.y,wx=p.x-a.x,wy=p.y-a.y,c1=vx*wx+vy*wy;if(c1<=0)return dist(p.x,p.y,a.x,a.y);float c2=vx*vx+vy*vy;if(c2<=c1)return dist(p.x,p.y,b.x,b.y);float t=c1/c2;return dist(p.x,p.y,a.x+t*vx,a.y+t*vy);}

    private boolean nearCorner(float x,float y,RectF b,float d){float[][]q={{b.left,b.top},{b.right,b.top},{b.left,b.bottom},{b.right,b.bottom}};for(float[]z:q)if(dist(x,y,z[0],z[1])<d)return true;return false;}
    private static class Cand{float x,y,s;Cand(float x,float y,float s){this.x=x;this.y=y;this.s=s;}}
    private static float dist(float a,float b,float c,float d){return (float)Math.hypot(a-c,b-d);} private static boolean in(int x,int y,int w,int h){return x>=0&&y>=0&&x<w&&y<h;}
    private static int pixel(int[]p,int w,int h,float x,float y){int xx=Math.max(0,Math.min(w-1,Math.round(x))),yy=Math.max(0,Math.min(h-1,Math.round(y)));return p[yy*w+xx];}
    private static float lumAt(int[]p,int w,int h,float x,float y){return luma(pixel(p,w,h,x,y));} private static float luma(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r*.299f+g*.587f+b*.114f;}
    private static float sat(int c){float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f,mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));return mx==0?0:(mx-mn)/mx;} private static float clamp(float v){return Math.max(0,Math.min(1,v));}
}
