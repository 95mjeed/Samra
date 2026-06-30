import requests
from urllib.parse import urlparse


def redirect_of(url: str, amount: int = 1) -> str | None:
    """
    Get the location where a url redirects to

    :param url: the http endpoint
    :param amount: the amount of times the function is allowed to redirect
    :returns: the redirected location
    """
    for i in range(amount):
        response = requests.get(url, allow_redirects=False)
        if response.status_code not in [ 301, 302 ]:
            return None
        if "location" not in response.headers:
            return None
        url = response.headers["location"]
        # Security: never follow a redirect to a non-https location (cleartext/downgrade).
        if urlparse(url).scheme != "https":
            return None
    return url
