with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    lines = f.readlines()

out = []
for line in lines:
    if line.startswith("import") and out and not out[-1].startswith("import") and not out[-1].startswith("package"):
        if "sealed class" in "".join(out):
            pass
        else:
            out.append(line)
    else:
        out.append(line)

final_out = []
imports_done = False
for line in out:
    if line.startswith("sealed class"):
        imports_done = True
    if line.startswith("import") and imports_done:
        continue
    final_out.append(line)

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.writelines(final_out)
