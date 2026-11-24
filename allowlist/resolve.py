"""Looks up each name in names.txt on YouTube's channel search and writes the top results to candidates.tsv."""
import json
import time
import subprocess
import sys
import urllib.parse

CHANNEL_FILTER = "EgIQAg%3D%3D"  # YouTube search filter: channels only


def search(name: str, count: int = 4) -> list[dict]:
    for attempt in range(3):
        entries = search_once(name, count)
        if entries:
            return entries
        time.sleep(5 * (attempt + 1))
    return []


def search_once(name: str, count: int) -> list[dict]:
    url = (
        "https://www.youtube.com/results?search_query="
        + urllib.parse.quote(name)
        + "&sp="
        + CHANNEL_FILTER
    )
    out = subprocess.run(
        ["yt-dlp", "--flat-playlist", "-J", "--playlist-end", str(count), url],
        capture_output=True, text=True, timeout=120,
    )
    if out.returncode != 0:
        print(f"search failed for {name}: {out.stderr.strip()[-300:]}", file=sys.stderr)
        return []
    return json.loads(out.stdout).get("entries") or []


def main(names_path: str, out_path: str) -> None:
    rows = ["section\tname\trank\tchannel_id\ttitle\tsubscribers\turl"]
    section = ""
    for raw in open(names_path, encoding="utf-8"):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("# section:"):
            section = line.split(":", 1)[1].strip()
            continue
        if line.startswith("#"):
            continue
        for rank, e in enumerate(search(line), 1):
            cid = e.get("channel_id") or e.get("id") or ""
            rows.append("\t".join([
                section, line, str(rank), cid,
                (e.get("title") or e.get("channel") or "").replace("\t", " "),
                str(e.get("channel_follower_count") or ""),
                e.get("url") or "",
            ]))
        print(f"{line}: done", file=sys.stderr)
    open(out_path, "w", encoding="utf-8").write("\n".join(rows) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
