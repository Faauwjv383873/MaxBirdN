import re

with open('gradle/libs.versions.toml', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if 'media3-common' in line:
        new_lines.append('androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }\n')

with open('gradle/libs.versions.toml', 'w') as f:
    f.writelines(new_lines)
    
with open('app/build.gradle.kts', 'r') as f:
    blines = f.readlines()

new_blines = []
for line in blines:
    new_blines.append(line)
    if 'media3.common' in line:
        new_blines.append('  implementation(libs.androidx.media3.session)\n')

with open('app/build.gradle.kts', 'w') as f:
    f.writelines(new_blines)
