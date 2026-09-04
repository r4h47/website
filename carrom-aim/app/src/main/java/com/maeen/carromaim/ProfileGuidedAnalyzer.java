package com.maeen.carromaim;

import android.graphics.PointF;
import android.graphics.RectF;
import java.util.*;

public final class ProfileGuidedAnalyzer {
    private final ProfileStore.Profile profile;
    public ProfileGuidedAnalyzer(ProfileStore.Profile p){ profile=p; }

    public FrameAnalyzer.Result analyze(int[] px,int w,int h){
        if(px==null||px.length<w*h)return null;
        RectF b=new RectF(profile.boardLeft*w,profile.boardTop*h,profile.boardRight*w,profile.boardBottom*h);
        float bw=b.width();
        if(bw<100||b.height()<100)return null;
        float cr=Math.max(7,bw*profile.coinRadius), sr=Math.max(cr*1.08f,bw*profile.strikerRadius);
        float d=bw*profile.pocketInset;
        PointF[] pockets={new PointF(b.left+d,b.top+d),new PointF(b.right-d,b.top+d),new PointF(b.left+d,b.bottom-d),new PointF(b.right-d,b.bottom-d)};
        List<PointF> white=detectCoins(px,w,h,b,cr,true,pockets), black=detectCoins(px,w,h,b,cr,false,pockets), red=detectRed(px,w,h,b,cr);
        FrameAnalyzer.Side side="WHITE".equals(profile.side)?FrameAnalyzer.Side.WHITE:("BLACK".equals(profile.side)?FrameAnalyzer.Side.BLACK:inferAuto(white,black));
        List<PointF> mine=side==FrameAnalyzer.Side.BLACK?black:white;
        List<PointF> all=new ArrayList<>(); all.addAll(white);all.addAll(black);all.addAll(red);
        PointF striker=detectStriker(px,w,h,b,sr,cr,all);
        List<FrameAnalyzer.Shot> shots=striker==null?new ArrayList<>():plan(striker,mine,all,pockets,cr,sr,b);
        float conf=.55f+(mine.isEmpty()?0:.15f)+(striker==null?0:.20f)+(shots.isEmpty()?0:.10f);
        return new FrameAnalyzer.Result(b,striker,mine,all,pockets,shots,side,Math.min(1f,conf));
    }

    private FrameAnalyzer.Side inferAuto(List<PointF>w,List<PointF>b){return w.size()>=b.size()?FrameAnalyzer.Side.WHITE:FrameAnalyzer.Side.BLACK;}

    private List<PointF> detectCoins(int[]p,int w,int h,RectF b,float r,boolean white,PointF[] pockets){
        List<Cand> cs=new ArrayList<>(); int st=Math.max(6,Math.round(r*.55f));
        for(int y=(int)(b.top+r*2);y<b.bottom-r*2;y+=st)for(int x=(int)(b.left+r*2);x<b.right-r*2;x+=st){
            if(nearPocket(x,y,pockets,r*3.2f))continue; float s=coinScore(p,w,h,x,y,r,white); if(s>.50f)cs.add(new Cand(x,y,s)); }
        cs.sort((a,c)->Float.compare(c.s,a.s)); List<PointF> out=new ArrayList<>();
        for(Cand c:cs){boolean ok=true;for(PointF q:out)if(dist(q.x,q.y,c.x,c.y)<r*1.45f){ok=false;break;}if(ok)out.add(new PointF(c.x,c.y));if(out.size()>=12)break;}return out;
    }
    private float coinScore(int[]p,int w,int h,float cx,float cy,float r,boolean white){
        float center=lum(p,w,h,cx,cy), ring=0; int good=0;
        for(int k=0;k<16;k++){double a=k*Math.PI*2/16;float li=lum(p,w,h,cx+(float)Math.cos(a)*r*.60f,cy+(float)Math.sin(a)*r*.60f);float lo=lum(p,w,h,cx+(float)Math.cos(a)*r*1.42f,cy+(float)Math.sin(a)*r*1.42f);ring+=lo;if(white?(li>lo+16&&li>150):(li<lo-18&&li<135))good++;}
        ring/=16f;float contrast=white?center-ring:ring-center;return (good/16f)*.78f+clamp((contrast-6)/55f)*.22f;
    }
    private List<PointF> detectRed(int[]p,int w,int h,RectF b,float r){List<PointF>o=new ArrayList<>();int st=Math.max(7,Math.round(r*.65f));float best=0;PointF bp=null;for(int y=(int)(b.top+r*2);y<b.bottom-r*2;y+=st)for(int x=(int)(b.left+r*2);x<b.right-r*2;x+=st){int c=p[y*w+x],rr=(c>>16)&255,g=(c>>8)&255,bb=c&255;float s=(rr>150&&rr>g*1.3f&&rr>bb*1.3f)?rr-g:0;if(s>best){best=s;bp=new PointF(x,y);}}if(bp!=null)o.add(bp);return o;}
    private PointF detectStriker(int[]p,int w,int h,RectF b,float sr,float cr,List<PointF>coins){
        float y=b.top+profile.baselineY*b.height(), y0=y-b.height()*.045f,y1=y+b.height()*.045f; int st=Math.max(5,Math.round(cr*.4f));float best=-1;PointF bp=null;
        for(int yy=(int)y0;yy<y1;yy+=st)for(int x=(int)(b.left+b.width()*.12f);x<b.right-b.width()*.12f;x+=st){boolean near=false;for(PointF q:coins)if(dist(x,yy,q.x,q.y)<cr*1.3f){near=true;break;}if(near)continue;float s=strikerScore(p,w,h,x,yy,sr);if(s>best){best=s;bp=new PointF(x,yy);}}
        return best>.30f?bp:null;
    }
    private float strikerScore(int[]p,int w,int h,float cx,float cy,float r){float c=lum(p,w,h,cx,cy),out=0,diff=0;for(int k=0;k<16;k++){double a=k*Math.PI*2/16;float li=lum(p,w,h,cx+(float)Math.cos(a)*r*.55f,cy+(float)Math.sin(a)*r*.55f),lo=lum(p,w,h,cx+(float)Math.cos(a)*r*1.3f,cy+(float)Math.sin(a)*r*1.3f);out+=lo;diff+=Math.abs(li-lo);}out/=16;diff/=16;return clamp((diff-8)/45f)*.7f+clamp((Math.abs(c-out)-6)/60f)*.3f;}

    private List<FrameAnalyzer.Shot> plan(PointF s,List<PointF>mine,List<PointF>all,PointF[]pockets,float r,float sr,RectF b){
        List<FrameAnalyzer.Shot>o=new ArrayList<>();for(PointF c:mine)for(PointF p:pockets){float dx=p.x-c.x,dy=p.y-c.y,len=(float)Math.hypot(dx,dy);if(len<r*3)continue;float ux=dx/len,uy=dy/len;PointF g=new PointF(c.x-ux*(r+sr),c.y-uy*(r+sr));if(!b.contains(g.x,g.y))continue;float ax=g.x-s.x,ay=g.y-s.y,alen=(float)Math.hypot(ax,ay);if(alen<1)continue;float dot=(ax/alen)*ux+(ay/alen)*uy;float cut=(float)Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,dot))));if(cut>82)continue;boolean clear=segClear(s,g,all,c,r*1.7f)&&segClear(c,p,all,c,r*1.8f);float score=1-.5f*(cut/82f)-.2f*Math.min(1,(alen+len)/(b.width()*1.6f))-(clear?0:.35f);o.add(new FrameAnalyzer.Shot(new PointF(s.x,s.y),g,new PointF(c.x,c.y),new PointF(p.x,p.y),score,clear));}
        o.sort((a,c)->Float.compare(c.score,a.score));return o.subList(0,Math.min(6,o.size()));
    }
    private boolean segClear(PointF a,PointF b,List<PointF>obs,PointF ignore,float cl){for(PointF p:obs){if(dist(p.x,p.y,ignore.x,ignore.y)<cl*.55f)continue;if(pointSeg(p,a,b)<cl)return false;}return true;}
    private float pointSeg(PointF p,PointF a,PointF b){float vx=b.x-a.x,vy=b.y-a.y,wx=p.x-a.x,wy=p.y-a.y,c1=vx*wx+vy*wy;if(c1<=0)return dist(p.x,p.y,a.x,a.y);float c2=vx*vx+vy*vy;if(c2<=c1)return dist(p.x,p.y,b.x,b.y);float t=c1/c2;return dist(p.x,p.y,a.x+t*vx,a.y+t*vy);}
    private boolean nearPocket(float x,float y,PointF[]p,float d){for(PointF q:p)if(dist(x,y,q.x,q.y)<d)return true;return false;}
    private static class Cand{float x,y,s;Cand(float x,float y,float s){this.x=x;this.y=y;this.s=s;}}
    private static float lum(int[]p,int w,int h,float x,float y){int xx=Math.max(0,Math.min(w-1,Math.round(x))),yy=Math.max(0,Math.min(h-1,Math.round(y)));int c=p[yy*w+xx],r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r*.299f+g*.587f+b*.114f;}
    private static float dist(float a,float b,float c,float d){return (float)Math.hypot(a-c,b-d);} private static float clamp(float v){return Math.max(0,Math.min(1,v));}
}
