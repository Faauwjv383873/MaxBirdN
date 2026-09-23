import urllib.request
import re
import os

url = "https://play.google.com/store/apps/details?id=com.shikho.shikho"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
try:
    with urllib.request.urlopen(req) as response:
        html = response.read().decode("utf-8", errors="ignore")
        imgs = re.findall(r'https?://[^\s"\'<>]+', html)
        print("Scraped URLs from Play Store:")
        for l in sorted(set(imgs)):
            if any(k in l.lower() for k in ["logo", "icon", "brand", "googleusercontent"]):
                print(l)
except Exception as e:
    print("Error:", e)
