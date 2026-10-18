import urllib.request
import json

def test_vxtwitter():
    url = "https://api.vxtwitter.com/x/status/1785514605963231454"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read())
            return data
    except Exception as e:
        print("vxtwitter error:", e)
        return None

print(test_vxtwitter())
