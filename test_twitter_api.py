import urllib.request
import json

def get_guest_token():
    req = urllib.request.Request(
        'https://api.twitter.com/1.1/guest/activate.json',
        method='POST',
        headers={
            'Authorization': 'Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA'
        }
    )
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read())['guest_token']
    except Exception as e:
        print(e)
        return None

print("Guest token:", get_guest_token())

def get_tweet_syndication(tweet_id):
    url = f"https://cdn.syndication.twimg.com/tweet-result?id={tweet_id}"
    try:
        with urllib.request.urlopen(url) as response:
            return json.loads(response.read())
    except Exception as e:
        print("Syndication error:", e)
        return None

print("Tweet:", get_tweet_syndication('1831700688643801128'))
