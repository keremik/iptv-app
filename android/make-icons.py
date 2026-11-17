from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os
RES='/home/user/iptv-app/android/app/src/main/res'
FONT='/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf'
RED=(229,9,20)

def background(w,h):
    im=Image.new('RGB',(w,h))
    d=ImageDraw.Draw(im)
    for y in range(h):   # dunkler Verlauf, leicht heller in der Mitte
        t=abs(y-h/2)/(h/2)
        c=int(26-14*t)
        d.line([(0,y),(w,y)],fill=(c,c,c))
    return im

def wordmark(text, height):
    # kräftiger, schmaler Schriftzug: groß rendern, dann horizontal stauchen
    f=ImageFont.truetype(FONT, height*2)
    bb=f.getbbox(text)
    tw,th=bb[2]-bb[0], bb[3]-bb[1]
    m=Image.new('L',(tw+20,th+20),0)
    ImageDraw.Draw(m).text((10-bb[0],10-bb[1]),text,font=f,fill=255)
    m=m.resize((int((tw+20)*0.72), th+20), Image.LANCZOS)
    return m

def compose(w,h,text,fill_w):
    im=background(w,h)
    m=wordmark(text, h)
    sc=fill_w*w/m.width
    if m.height*sc > h*0.62: sc=h*0.62/m.height
    m=m.resize((int(m.width*sc),int(m.height*sc)),Image.LANCZOS)
    x,y=(w-m.width)//2,(h-m.height)//2
    shadow=Image.new('L',im.size,0); shadow.paste(m,(x+int(h*0.012),y+int(h*0.02)))
    shadow=shadow.filter(ImageFilter.GaussianBlur(h*0.02))
    im.paste((0,0,0),(0,0),shadow.point(lambda v:int(v*0.7)))
    # leichter Verlauf im Rot (oben heller, unten dunkler) wie bei Streaming-Logos
    grad=Image.new('RGB',m.size)
    gd=ImageDraw.Draw(grad)
    for yy in range(m.height):
        t=yy/max(1,m.height-1)
        gd.line([(0,yy),(m.width,yy)],fill=(int(240-50*t),int(14-8*t),int(24-12*t)))
    im.paste(grad,(x,y),m)
    return im

os.makedirs(RES+'/drawable-xhdpi',exist_ok=True); os.makedirs(RES+'/drawable-xxxhdpi',exist_ok=True)
compose(320,180,'IPFLIX',0.84).save(RES+'/drawable-xhdpi/banner.png')
compose(640,360,'IPFLIX',0.84).save(RES+'/drawable-xxxhdpi/banner.png')
for d,s in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    os.makedirs(RES+'/mipmap-'+d,exist_ok=True)
    big=compose(s*4,s*4,'IPFLIX',0.86).resize((s,s),Image.LANCZOS)
    big.save(RES+'/mipmap-'+d+'/ic_launcher.png')
compose(1280,720,'IPFLIX',0.84).save('/tmp/claude-0/-home-user-iptv-app/321b8c5c-c6e2-51e9-b0c4-603e6a281397/scratchpad/banner_preview.png')
print('ok')
