import urllib.request
import re
import os

url = "https://shikho.com/"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
try:
    with urllib.request.urlopen(req) as response:
        html = response.read().decode("utf-8", errors="ignore")
        imgs = re.findall(r'https?://[^\s"\'<>]+', html)
        print("Scraped URLs from shikho.com:")
        for l in sorted(set(imgs)):
            if any(k in l.lower() for k in ["logo", "icon", "brand", "svg", "png", "webp"]):
                print(l)
except Exception as e:
    print("Error:", e)
