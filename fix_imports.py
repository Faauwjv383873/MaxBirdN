import re

def deduplicate_imports(filepath):
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    new_lines = []
    seen_imports = set()
    
    for line in lines:
        if line.startswith('import '):
            if line.strip() in seen_imports:
                continue
            seen_imports.add(line.strip())
        new_lines.append(line)
        
    with open(filepath, 'w') as f:
        f.writelines(new_lines)

deduplicate_imports('app/src/main/java/com/example/ui/screens/LessonDetailPlayerScreen.kt')
deduplicate_imports('app/src/main/java/com/example/ui/screens/VideoPlayerScreen.kt')
