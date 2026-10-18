"""Writes app/src/main/res/drawable/ic_launcher_foreground.xml: the three students peeking over a
desk edge, built from the same shapes as StudentShapes.kt (keep the two in step).

    python3 tools/gen_launcher_icon.py > app/src/main/res/drawable/ic_launcher_foreground.xml
"""
import math, sys
INK="#2B364B"; MIRA="#D7F13D"; PIP="#FA4B7C"; JUNO="#EF582A"; FACE="#F5EDE2"; SHADOW="#6B2A16"
SW=2.2
def f(v): return ("%.2f"%v).rstrip('0').rstrip('.')
class T:
    def __init__(s,ox,oy,k): s.ox,s.oy,s.k=ox,oy,k
    def p(s,x,y): return f(s.ox+x*s.k)+","+f(s.oy+y*s.k)
def rounded(t,pts,bottom=2.2):
    n=len(pts); out=[]
    for i,(x,y,r) in enumerate(pts):
        px,py,_=pts[i-1]; nx,ny,_=pts[(i+1)%n]
        if r==0:
            out.append(("M" if i==0 else "L")+t.p(x,y)); continue
        def tw(ox,oy):
            dx,dy=ox-x,oy-y; L=math.hypot(dx,dy); rr=min(r,L/2); return x+dx*rr/L,y+dy*rr/L
        a=tw(px,py); b=tw(nx,ny)
        out.append(("M" if i==0 else "L")+t.p(*a)); out.append("Q"+t.p(x,y)+" "+t.p(*b))
    return "".join(out)+"Z"
def cubic(t,start,segs,end):
    s="M"+t.p(*start)
    for seg in segs: s+="C"+t.p(seg[0],seg[1])+" "+t.p(seg[2],seg[3])+" "+t.p(seg[4],seg[5])
    return s+"L"+t.p(*end)+"Z"
def ellipse(cx,cy,rx,ry):
    return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0Z"%(f(cx-rx),f(cy),f(rx),f(ry),f(2*rx),f(rx),f(ry),f(-2*rx))
def capsule(cx,cy,w,h,deg):
    r=w/2; pts=[]
    for i in range(0,181,15):
        a=math.radians(180+i); pts.append((cx+r*math.cos(a), cy-(h/2-r)+r*math.sin(a)))
    for i in range(0,181,15):
        a=math.radians(i); pts.append((cx+r*math.cos(a), cy+(h/2-r)+r*math.sin(a)))
    th=math.radians(deg); out=[]
    for j,(x,y) in enumerate(pts):
        dx,dy=x-cx,y-cy; X=cx+dx*math.cos(th)-dy*math.sin(th); Y=cy+dx*math.sin(th)+dy*math.cos(th)
        out.append(("M" if j==0 else "L")+f(X)+","+f(Y))
    return "".join(out)+"Z"
def path(d,fill=None,stroke=True,sw=SW):
    a='    <path android:pathData="%s"'%d
    if fill: a+=' android:fillColor="%s"'%fill
    if stroke: a+=' android:strokeColor="%s" android:strokeWidth="%s" android:strokeLineJoin="round"'%(INK,f(sw))
    return a+" />"
parts=[]
def mira(t):
    d=cubic(t,(0.15,2.2),[(0.10,1.16,0.10,0.94,0.16,0.80),(0.20,0.69,0.25,0.58,0.29,0.52),(0.31,0.485,0.34,0.48,0.355,0.51),(0.37,0.545,0.38,0.578,0.41,0.576),(0.47,0.566,0.56,0.548,0.59,0.52),(0.62,0.48,0.645,0.425,0.67,0.41),(0.69,0.395,0.715,0.40,0.735,0.43),(0.82,0.55,0.92,0.80,0.91,0.98),(0.905,1.10,0.88,1.22,0.84,2.2)],(0.84,2.2))
    parts.append(path(d,MIRA))
    for x,y in [(0.415,0.865),(0.655,0.855)]:
        parts.append(path(ellipse(t.ox+x*t.k,t.oy+y*t.k,0.0575*t.k,0.11*t.k),INK,False))
def pip(t):
    parts.append(path(rounded(t,[(0.76,0.34,0.07),(0.70,0.74,0.025),(0.97,0.64,0.06),(0.74,2.2,0),(0.10,2.2,0),(0.30,0.91,0.025),(0.05,0.90,0.06)]),PIP))
    for x,y,deg in [(0.4675,0.9025,8),(0.6925,0.8775,-8)]:
        parts.append(path(capsule(t.ox+x*t.k,t.oy+y*t.k,0.055*t.k,0.12*t.k,deg),INK,False))
def juno(t):
    parts.append(path(rounded(t,[(0.06,2.2,0),(0.10,1.00,0.06),(0.90,0.98,0.06),(0.94,2.2,0)]),JUNO))
    face=rounded(t,[(0.40,0.56,0.06),(0.60,0.56,0.06),(0.81,1.06,0.07),(0.19,1.08,0.07)])
    parts.append(path(face,FACE,False))
    bx,by=t.ox+0.49*t.k,t.oy+0.715*t.k; k=t.k
    parts.append('    <group>\n      <clip-path android:pathData="%s" />\n      <group android:rotation="-15" android:pivotX="%s" android:pivotY="%s">\n  %s\n      </group>\n    </group>'%(face,f(bx),f(by),path(ellipse(bx+0.18*k,by+0.0475*k,0.30*k,0.0675*k),SHADOW,False)))
    parts.append(path(face,None))
    for left in (True,False):
        X=lambda v: v if left else 1-v
        parts.append(path(rounded(t,[(X(0.06),2.2,0),(X(0.10),0.99 if left else 0.97,0.04),(X(0.20),0.86 if left else 0.84,0.035),(X(0.50),1.15,0.02),(X(0.50),2.2,0)]),JUNO))
    for x in (0.425,0.575):
        parts.append(path(capsule(t.ox+x*k,t.oy+0.905*k,0.058*k,0.112*k,0),INK,False))
    hat=[path(ellipse(bx,by-0.27*k,0.06*k,0.06*k),JUNO),
         path("M%s,%s C%s,%s %s,%s %s,%sZ"%(f(bx-0.25*k),f(by),f(bx-0.25*k),f(by-0.31*k),f(bx+0.25*k),f(by-0.31*k),f(bx+0.25*k),f(by)),JUNO),
         path(ellipse(bx,by,0.45*k,0.08*k),JUNO)]
    parts.append('    <group android:rotation="-15" android:pivotX="%s" android:pivotY="%s">\n  %s\n    </group>'%(f(bx),f(by),"\n  ".join(hat)))
DESK=79
# Mira and Juno peek from the back row; Pip stands in front, centred.
mira(T(17.8,24.8,34)); juno(T(62,28,32)); pip(T(28.5,30.8,44))
# motes over the heads
for cx,cy,r in [(40,29,3),(54,24,3),(68,29,3)]:
    parts.append(path(ellipse(cx,cy,r,r),"#FFFDF5",True,1.8))
# desk edge
parts.append(path("M0,%s L108,%s L108,%s L0,%sZ"%(f(DESK),f(DESK),f(DESK+9),f(DESK+9)),"#EDB65A",False))
parts.append(path("M0,%s L108,%s L108,108 L0,108Z"%(f(DESK+9),f(DESK+9)),"#D39740",False))
parts.append(path("M-2,%s L110,%s"%(f(DESK),f(DESK)),None,True,2.6))
parts.append(path("M-2,%s L110,%s"%(f(DESK+9),f(DESK+9)),None,True,2.2))
print('''<?xml version="1.0" encoding="utf-8"?>
<!-- The three students peeking over a desk edge, generated by tools/gen_launcher_icon.py from the shapes in StudentShapes.kt. The background is @color/gold. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">''')
print("\n".join(parts))
print("</vector>")
