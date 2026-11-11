"""Render the map's Kotlin layout coordinates for visual QA (not a device screenshot)."""
import pathlib,re,collections
from PIL import Image,ImageDraw,ImageFont
ROOT=pathlib.Path(__file__).resolve().parents[1]
source=(ROOT/'app/src/main/java/edu/taehyeon/myconcert/data/Regions.kt').read_text(encoding='utf-8')
rows=re.findall(r'Region\("([^"]+)", "(\d+)", ([\d.]+)f,([\d.]+)f,([\d.]+)f,([\d.]+)f\)',source)
image=Image.open(ROOT/'app/src/main/res/drawable-nodpi/korea_map.png').convert('RGBA')
scale=2
canvas=Image.new('RGBA',(400*scale,500*scale),'white')
ratio=min(240/image.width,460/image.height)*scale
size=(round(image.width*ratio),round(image.height*ratio))
left=200*scale-size[0]/2;top=20*scale
canvas.alpha_composite(image.resize(size,Image.Resampling.LANCZOS),(round(left),round(top)))
draw=ImageDraw.Draw(canvas)
font=ImageFont.truetype('C:/Windows/Fonts/malgunbd.ttf',22)
for name,code,px,py,lx,ly in rows:
    pin=(left+float(px)*size[0],top+float(py)*size[1]);label=(float(lx)*scale,float(ly)*scale)
    draw.line([pin,label],fill='#B9C8DC',width=2);draw.ellipse((pin[0]-5,pin[1]-5,pin[0]+5,pin[1]+5),fill='#3B82F6')
for name,code,px,py,lx,ly in rows:
    x,y=float(lx)*scale,float(ly)*scale;r=44
    draw.ellipse((x-r,y-r,x+r,y+r),fill='#3B82F6' if code=='29' else 'white',outline='#B9C8DC',width=2)
    draw.text((x,y),name,fill='white' if code=='29' else '#243247',anchor='mm',font=font)
out=ROOT/'docs/qa/map-layout-preview.png';out.parent.mkdir(exist_ok=True,parents=True);canvas.save(out)
print(out)
