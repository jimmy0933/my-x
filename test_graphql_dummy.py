import urllib.request

url = 'https://twitter.com/i/api/graphql/sbs1yV0KkXQ2hQ6-vSj4HQ/UserByScreenName?variables={"screen_name":"X","withSafetyModeUserFields":true}&features={"hidden_profile_likes_enabled":true,"hidden_profile_subscriptions_enabled":true,"responsive_web_graphql_exclude_directive_enabled":true,"verified_phone_label_enabled":false,"subscriptions_verification_info_is_identity_verified_enabled":true,"subscriptions_verification_info_verified_since_enabled":true,"highlights_tweets_tab_ui_enabled":true,"creator_subscriptions_tweet_preview_api_enabled":true,"responsive_web_graphql_skip_user_profile_image_extensions_enabled":false,"responsive_web_graphql_timeline_navigation_graphql_enabled":true}'

req = urllib.request.Request(
    url,
    headers={
        'Authorization': 'Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA',
        'x-csrf-token': 'dummy_ct0',
        'Cookie': 'auth_token=dummy_auth; ct0=dummy_ct0',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    }
)

try:
    with urllib.request.urlopen(req) as response:
        print(response.read())
except Exception as e:
    print(e)
    if hasattr(e, 'read'):
        print(e.read())
