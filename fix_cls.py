import re

with open('app/src/main/java/com/example/ui/screens/ChapterLessonsScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if re.match(r'^\s*\"[A-Z0-9_]+\",\s*$', line):
        skip = True
        continue
    if skip:
        if line.strip() == ')':
            skip = False
            continue
        continue
    
    if 'import com.example.ui.components.LogType' in line:
        continue
    if 'DebugTerminalManager' in line:
        continue
    if 'DebugTerminalOverlay' in line:
        continue

    new_lines.append(line)

with open('app/src/main/java/com/example/ui/screens/ChapterLessonsScreen.kt', 'w') as f:
    f.writelines(new_lines)
