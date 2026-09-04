package com.maeen.carromaim;

import android.graphics.PointF;
import android.graphics.RectF;
import java.util.*;

public final class ProfileGuidedAnalyzer {
    private final ProfileStore.Profile profile;
    public ProfileGuidedAnalyzer(ProfileStore.Profile p){ profile=p; }

    public FrameAnalyzer.Result analyze(int[] px,int w,int h){
        if(px==null||px.length<w*h)return null;

        // Board geometry is kept square in pixel coordinates. This prevents the
        // previous profile bottom value from stretching the board into the UI below it.
        float left=profile.boardLeft*w;
        float right=profile.boardRight*w;
        float bw=Math.max(1f,right-left);
        float top=profile.boardTop*h;
        float bottom=top+bw;
        if(bottom>h){ bottom=h; top=Math.max(0,bottom-bw); }
        RectF b=new RectF(Math.max(0,left),Math.max(0,top),Math.min(w,right),Math.min(h,bottom));
        bw=b.width();
        if(bw<200||Math.abs(b.width()-b.height())>bw*.04f)return null;

        float cr=Math.max(7,bw*profile.coinRadius), sr=Math.max(cr*1.10f,bw*profile.strikerRadius);
        float inset=Math.max(bw*.045f,Math.min(bw*.080f,bw*profile.pocketInset));
        PointF[] expected={
                new PointF(b.left+inset,b.top+inset),
                new PointF(b.right-inset,b.top+inset),
                new PointF(b.left+inset,b.bottom-inset),
                new PointF(b.right-inset,b.bottom-inset)};
        PointF[] pockets=new PointF[4];
        for(int i=0;i<4;i++)pockets[i]=refinePocket(px,w,h,expected[i],bw*.075f);

        List<PointF> light=detectCoins(px,w,h,b,cr,true,pockets);
        List<PointF> dark=detectCoins(px,w,h,b,cr,false,pockets);
        List<PointF> red=detectRed(px,w,h,b,cr);

        FrameAnalyzer.Side side="WHITE".equals(profile.side)?FrameAnalyzer.Side.WHITE:("BLACK".equals(profile.side)?FrameAnalyzer.Side.BLACK:inferAuto(light,dark));
        List<PointF> mine=side==FrameAnalyzer.Side.BLACK?dark:light;
        List<PointF> all=new ArrayList<>(); all.addAll(light);all.addAll(dark);all.addAll(red);
        all=dedupe(all,cr*1.35f);
        mine=dedupe(mine,cr*1.35f);

        PointF striker=detectStriker(px,w,h,b,sr,cr,all);
        List<FrameAnalyzer.Shot> shots=(striker==null||!TurnGate.isLocalTurn(b,striker))?new ArrayList<>():plan(striker,mine,all,pockets,cr,sr,b);
        float conf=.60f+(mine.isEmpty()?0:.12f)+(striker==null?0:.18f)+(TurnGate.isLocalTurn(b,striker)?.10f:0f);
        return new FrameAnalyzer.Result(b,striker,mine,all,pockets,shots,side,Math.min(1f,conf));
    }

    private PointF refinePocket(int[]p,int w,int h,PointF e,float search){
        float best=Float.MAX_VALUE;PointF out=new PointF(e.x,e.y);int step=Math.max(3,Math.round(search/12f));
        for(int y=(int)(e.y-search);y<=e.y+search;y+=step)for(int x=(int)(e.x-search);x<=e.x+search;x+=step){
            if(x<2||y<2||x>=w-2||y>=h-2)continue;
            float s=0;int n=0;for(int dy=-6;dy<=6;dy+=4)for(int dx=-6;dx<=6;dx+=4){s+=lum(p,w,h,x+dx,y+dy);n++;}
            s/=Math.max(1,n); if(s<best){best=s;out=new PointF(x,y);}
        }
        return out;
    }

    private FrameAnalyzer.Side inferAuto(List<PointF>w,List<PointF>b){return w.size()>=b.size()?FrameAnalyzer.Side.WHITE:FrameAnalyzer.Side.BLACK;}

    private List<PointF> detectCoins(int[]p,int w,int h,RectF b,float r,boolean white,PointF[] pockets){
        List<Cand> cs=new ArrayList<>(); int st=Math.max(5,Math.round(r*.45f));
        float margin=r*1.7f;
        for(int y=(int)(b.top+margin);y<b.bottom-margin;y+=st)for(int x=(int)(b.left+margin);x<b.right-margin;x+=st){
            if(nearPocket(x,y,pockets,r*3.0f))continue; float s=coinScore(p,w,h,x,y,r,white); if(s>.58f)cs.add(new Cand(x,y,s)); }
        cs.sort((a,c)->Float.compare(c.s,a.s)); List<PointF> out=new ArrayList<>();
        for(Cand c:cs){boolean ok=true;for(PointF q:out)if(dist(q.x,q.y,c.x,c.y)<r*1.65f){ok=false;break;}if(ok)out.add(new PointF(c.x,c.y));if(out.size()>=10)break;}return out;
    }
    private float coinScore(int[]p,int w,int h,float cx,float cy,float r,boolean white){
        float center=lum(p,w,h,cx,cy), outer=0; int good=0;
        for(int k=0;k<20;k++){double a=k*Math.PI*2/20;float li=lum(p,w,h,cx+(float)Math.cos(a)*r*.58f,cy+(float)Math.sin(a)*r*.58f);float lo=lum(p,w,h,cx+(float)Math.cos(a)*r*1.38f,cy+(float)Math.sin(a)*r*1.38f);outer+=lo;if(white?(li>lo+18&&li>145):(li<lo-20&&li<140))good++;}
        outer/=20f;float contrast=white?center-outer:outer-center;return (good/20f)*.82f+clamp((contrast-8)/58f)*.18f;
    }
    private List<PointF> detectRed(int[]p,int w,int h,RectF b,float r){
        List<Cand> cs=new ArrayList<>();int st=Math.max(6,Math.round(r*.55f));
        for(int y=(int)(b.top+r*1.7f);y<b.bottom-r*1.7f;y+=st)for(int x=(int)(b.left+r*1.7f);x<b.right-r*1.7f;x+=st){int c=p[y*w+x],rr=(c>>16)&255,g=(c>>8)&255,bb=c&255;if(rr>145&&rr>g*1.22f&&rr>bb*1.20f)cs.add(new Cand(x,y,(rr-g)+(rr-bb)));}
        cs.sort((a,c)->Float.compare(c.s,a.s));List<PointF>o=new ArrayList<>();for(Cand c:cs){boolean ok=true;for(PointF q:o)if(dist(q.x,q.y,c.x,c.y)<r*1.6f){ok=false;break;}if(ok)o.add(new PointF(c.x,c.y));if(o.size()>=10)break;}return o;
    }

    private PointF detectStriker(int[]p,int w,int h,RectF b,float sr,float cr,List<PointF>coins){
        // Search both baselines. The service decides whether the detected striker is
        // on the local (bottom) end before showing any overlay.
        PointF bottom=findStrikerBand(p,w,h,b,sr,cr,coins,.835f,.905f);
        PointF top=findStrikerBand(p,w,h,b,sr,cr,coins,.055f,.145f);
        float sb=bottom==null?-1:strikerScore(p,w,h,bottom.x,bottom.y,sr);
        float st=top==null?-1:strikerScore(p,w,h,top.x,top.y,sr);
        return sb>=st?bottom:top;
    }
    private PointF findStrikerBand(int[]p,int w,int h,RectF b,float sr,float cr,List<PointF>coins,float n0,float n1){
        float y0=b.top+n0*b.height(),y1=b.top+n1*b.height();int step=Math.max(4,Math.round(cr*.35f));float best=.34f;PointF bp=null;
        for(int yy=(int)y0;yy<=y1;yy+=step)for(int x=(int)(b.left+b.width()*.14f);x<=b.right-b.width()*.14f;x+=step){
            boolean near=false;for(PointF q:coins)if(dist(x,yy,q.x,q.y)<cr*1.20f){near=true;break;}if(near)continue;
            float s=strikerScore(p,w,h,x,yy,sr);if(s>best){best=s;bp=new PointF(x,yy);}}
        return bp;
    }
    private float strikerScore(int[]p,int w,int h,float cx,float cy,float r){float center=lum(p,w,h,cx,cy),outer=0,diff=0;for(int k=0;k<20;k++){double a=k*Math.PI*2/20;float li=lum(p,w,h,cx+(float)Math.cos(a)*r*.50f,cy+(float)Math.sin(a)*r*.50f),lo=lum(p,w,h,cx+(float)Math.cos(a)*r*1.25f,cy+(float)Math.sin(a)*r*1.25f);outer+=lo;diff+=Math.abs(li-lo);}outer/=20;diff/=20;return clamp((diff-10)/48f)*.72f+clamp((Math.abs(center-outer)-8)/62f)*.28f;}

    private List<FrameAnalyzer.Shot> plan(PointF s,List<PointF>mine,List<PointF>all,PointF[]pockets,float r,float sr,RectF b){
        List<FrameAnalyzer.Shot>o=new ArrayList<>();for(PointF c:mine)for(PointF p:pockets){float dx=p.x-c.x,dy=p.y-c.y,len=(float)Math.hypot(dx,dy);if(len<r*3)continue;float ux=dx/len,uy=dy/len;PointF g=new PointF(c.x-ux*(r+sr),c.y-uy*(r+sr));if(!b.contains(g.x,g.y))continue;float ax=g.x-s.x,ay=g.y-s.y,alen=(float)Math.hypot(ax,ay);if(alen<1)continue;float dot=(ax/alen)*ux+(ay/alen)*uy;float cut=(float)Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,dot))));if(cut>72)continue;boolean clear=segClear(s,g,all,c,r*1.65f)&&segClear(c,p,all,c,r*1.75f);if(!clear)continue;float score=1-.58f*(cut/72f)-.22f*Math.min(1,(alen+len)/(b.width()*1.55f));o.add(new FrameAnalyzer.Shot(new PointF(s.x,s.y),g,new PointF(c.x,c.y),new PointF(p.x,p.y),score,true));}
        o.sort((a,c)->Float.compare(c.score,a.score));return new ArrayList<>(o.subList(0,Math.min(3,o.size())));
    }
    private boolean segClear(PointF a,PointF b,List<PointF>obs,PointF ignore,float cl){for(PointF p:obs){if(ignore!=null&&dist(p.x,p.y,ignore.x,ignore.y)<cl*.55f)continue;if(pointSeg(p,a,b)<cl)return false;}return true;}
    private float pointSeg(PointF p,PointF a,PointF b){float vx=b.x-a.x,vy=b.y-a.y,wx=p.x-a.x,wy=p.y-a.y,c1=vx*wx+vy*wy;if(c1<=0)return dist(p.x,p.y,a.x,a.y);float c2=vx*vx+vy*vy;if(c2<=c1)return dist(p.x,p.y,b.x,b.y);float t=c1/c2;return dist(p.x,p.y,a.x+t*vx,a.y+t*vy);}
    private List<PointF> dedupe(List<PointF>src,float d){List<PointF>o=new ArrayList<>();for(PointF p:src){boolean ok=true;for(PointF q:o)if(dist(p.x,p.y,q.x,q.y)<d){ok=false;break;}if(ok)o.add(p);}return o;}
    private boolean nearPocket(float x,float y,PointF[]p,float d){for(PointF q:p)if(dist(x,y,q.x,q.y)<d)return true;return false;}
    private static class Cand{float x,y,s;Cand(float x,float y,float s){this.x=x;this.y=y;this.s=s;}}
    private static float lum(int[]p,int w,int h,float x,float y){int xx=Math.max(0,Math.min(w-1,Math.round(x))),yy=Math.max(0,Math.min(h-1,Math.round(y)));int c=p[yy*w+xx],r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r*.299f+g*.587f+b*.114f;}
    private static float dist(float a,float b,float c,float d){return (float)Math.hypot(a-c,b-d);} private static float clamp(float v){return Math.max(0,Math.min(1,v));}
}
