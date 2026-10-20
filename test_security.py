import re

with open('gradle/libs.versions.toml', 'r') as f:
    content = f.read()

print("security" in content)
