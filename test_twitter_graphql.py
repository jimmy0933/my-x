import urllib.request
import json
import re

def test_graphql():
    # Let's try to get a guest token
    req = urllib.request.Request(
        'https://api.twitter.com/1.1/guest/activate.json',
        method='POST',
        headers={
            'Authorization': 'Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA'
        }
    )
    try:
        with urllib.request.urlopen(req) as response:
            gt = json.loads(response.read())['guest_token']
    except Exception as e:
        print("Guest token failed:", e)
        return

    # UserByScreenName
    # url: https://twitter.com/i/api/graphql/sbs1yV0KkXQ2hQ6-vSj4HQ/UserByScreenName?variables=%7B%22screen_name%22%3A%22X%22%2C%22withSafetyModeUserFields%22%3Atrue%7D&features=%7B%22hidden_profile_likes_enabled%22%3Atrue%2C%22hidden_profile_subscriptions_enabled%22%3Atrue%2C%22responsive_web_graphql_exclude_directive_enabled%22%3Atrue%2C%22verified_phone_label_enabled%22%3Afalse%2C%22subscriptions_verification_info_is_identity_verified_enabled%22%3Atrue%2C%22subscriptions_verification_info_verified_since_enabled%22%3Atrue%2C%22highlights_tweets_tab_ui_enabled%22%3Atrue%2C%22creator_subscriptions_tweet_preview_api_enabled%22%3Atrue%2C%22responsive_web_graphql_skip_user_profile_image_extensions_enabled%22%3Afalse%2C%22responsive_web_graphql_timeline_navigation_graphql_enabled%22%3Atrue%7D
    url = 'https://twitter.com/i/api/graphql/sbs1yV0KkXQ2hQ6-vSj4HQ/UserByScreenName?variables={"screen_name":"X","withSafetyModeUserFields":true}&features={"hidden_profile_likes_enabled":true,"hidden_profile_subscriptions_enabled":true,"responsive_web_graphql_exclude_directive_enabled":true,"verified_phone_label_enabled":false,"subscriptions_verification_info_is_identity_verified_enabled":true,"subscriptions_verification_info_verified_since_enabled":true,"highlights_tweets_tab_ui_enabled":true,"creator_subscriptions_tweet_preview_api_enabled":true,"responsive_web_graphql_skip_user_profile_image_extensions_enabled":false,"responsive_web_graphql_timeline_navigation_graphql_enabled":true}'
    url = url.replace(" ", "%20")

    req = urllib.request.Request(
        url,
        headers={
            'Authorization': 'Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA',
            'x-guest-token': gt,
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
        }
    )
    try:
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read())
            print(json.dumps(data)[:500])
    except Exception as e:
        print("User query failed:", e)

test_graphql()
