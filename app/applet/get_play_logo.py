import urllib.request
import re
import os

package_ids = ["com.shikho.shikhoapp", "com.shikho.app", "com.shikho.learning", "com.shikho", "com.shikho.student"]

for pkg in package_ids:
    url = f"https://play.google.com/store/apps/details?id={pkg}"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    try:
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
            icons = re.findall(r"https://play-lh\.googleusercontent\.com/[^\s\"\'=]+", html)
            if icons:
                icon_url = icons[0] + "=s512"
                print(f"FOUND Shikho Play Store icon for {pkg}: {icon_url}")
                img_req = urllib.request.Request(icon_url, headers={"User-Agent": "Mozilla/5.0"})
                with urllib.request.urlopen(img_req) as img_resp:
                    img_bytes = img_resp.read()
                    os.makedirs("app/src/main/res/drawable", exist_ok=True)
                    os.makedirs("app/src/main/res/mipmap-xxhdpi", exist_ok=True)
                    
                    # Save to drawable
                    with open("app/src/main/res/drawable/shikho_logo.png", "wb") as f:
                        f.write(img_bytes)
                    print("SUCCESSFULLY saved official Shikho logo to app/src/main/res/drawable/shikho_logo.png!")
                    break
    except Exception as e:
        print(f"Failed for {pkg}: {e}")
