import urllib.request
import json
import urllib.parse

def get_tweet_syndication(tweet_id):
    # Syndication API
    url = f"https://cdn.syndication.twimg.com/tweet-result?id={tweet_id}&token=1"
    try:
        req = urllib.request.Request(
            url,
            headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
                'Accept': '*/*',
                'Origin': 'https://platform.twitter.com',
                'Referer': 'https://platform.twitter.com/',
            }
        )
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read())
    except Exception as e:
        print("Syndication error:", e)
        return None

tweet_id = '1796030960579735914'  # Random public tweet ID
data = get_tweet_syndication(tweet_id)
if data:
    print(json.dumps(data, indent=2)[:500])
else:
    print("Failed")
