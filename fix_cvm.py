import re

with open('app/src/main/java/com/example/course/CourseViewModel.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    # This matches the lines that were left over from DebugTerminalManager.log(
    if re.match(r'^\s*\"[A-Z0-9_]+\",\s*$', line) and (i+1 < len(lines)) and re.match(r'^\s*\".*\",\s*$', lines[i+1]):
        skip = True
        continue
    if skip:
        if line.strip() == ')':
            skip = False
            continue
        # Also skip the message and LogType
        continue
    
    # Let's also remove import com.example.ui.components.LogType
    if 'import com.example.ui.components.LogType' in line:
        continue

    new_lines.append(line)

with open('app/src/main/java/com/example/course/CourseViewModel.kt', 'w') as f:
    f.writelines(new_lines)
