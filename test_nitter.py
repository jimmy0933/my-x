import urllib.request
import json

def test_nitter(username):
    url = f"https://nitter.net/{username}/media/rss"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            return response.read()[:500]
    except Exception as e:
        print("Nitter error:", e)
        return None

print(test_nitter('Twitter'))
