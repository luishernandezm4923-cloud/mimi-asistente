import json
import os
import urllib.request

token = os.environ["GITHUB_TOKEN"]
repo = os.environ["GITHUB_REPOSITORY"]
sha = os.environ["GITHUB_SHA"]

with open("build_log.txt", "r", errors="replace") as f:
    tail = f.read()[-60000:]

body = "### Log de compilacion\n```\n" + tail + "\n```"

data = json.dumps({"body": body}).encode()
req = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/commits/{sha}/comments",
    data=data,
    method="POST"
)
req.add_header("Authorization", f"token {token}")
req.add_header("Accept", "application/vnd.github+json")
urllib.request.urlopen(req)
print("Log publicado como comentario del commit.")
