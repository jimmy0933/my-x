with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

lines = content.split('\n')
while True:
    if not lines[-1].strip() or lines[-1].strip() == "}":
        lines.pop()
    else:
        break
lines.append("}")

content = "\n".join(lines)

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
