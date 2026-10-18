import urllib.request
import json

def test_twitfix():
    # Attempting to fetch a public tweet from fxtwitter
    url = "https://api.fxtwitter.com/Twitter/status/1831700688643801128"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read())
            return data
    except Exception as e:
        print("fxtwitter error:", e)
        return None

print(test_twitfix())
