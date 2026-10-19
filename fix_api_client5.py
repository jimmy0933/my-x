with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

lines = content.split('\n')
while lines[-1].strip() == "}":
    lines.pop()
lines.append("}")

content = "\n".join(lines)

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
