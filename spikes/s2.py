# Throwaway S2 runner (D12). Usage: python3 -I spikes/s2.py MODEL MODE ID...
# MODE: A480 | A720 | B | YT. Key read from .env, never printed. Outputs -> spikes/work/s2/
import json, os, re, subprocess, sys, tempfile, time, urllib.error, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = "https://generativelanguage.googleapis.com"
KEY = [l.split("=", 1)[1].strip().strip("\"'") for l in open(f"{ROOT}/.env") if l.startswith("GEMINI_API_KEY=")][0]
OUT = f"{ROOT}/spikes/work/" + os.environ.get("RUN", "s2")  # RUN=s2 (first run) | rerun1/s2 ...
os.makedirs(OUT, exist_ok=True)

plan = open(f"{ROOT}/docs/plan.md").read()
PROMPT = re.search(r"### Analyse prompt.*?```text\n(.*?)```", plan, re.S).group(1).replace("{language}", "English")
SCHEMA = json.loads(re.search(r"### Analyse schema.*?```json\n(.*?)```", plan, re.S).group(1))
rows = [l.rstrip("\n").split("\t") for l in open(f"{ROOT}/spikes/inputs/manifest.tsv")]
MANIFEST = {r[0]: dict(zip(rows[0], r)) for r in rows[1:]}


def call(method, url, body=None, headers=None, raw=False):
    h = {"x-goog-api-key": KEY, **(headers or {})}
    data = body if raw or body is None else json.dumps(body).encode()
    if data is not None and not raw:
        h["Content-Type"] = "application/json"
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=h, method=method), timeout=300) as r:
                out = r.headers, r.read()
            if ":generateContent" in url:
                print(f"CALL {url.split('/models/')[1].split(':')[0]} 200", file=sys.stderr)
            return out
        except urllib.error.HTTPError as e:
            msg = e.read().decode(errors="replace")
            if ":generateContent" in url:
                print(f"CALL {url.split('/models/')[1].split(':')[0]} {e.code}", file=sys.stderr)
            m = re.search(r'"retryDelay":\s*"(\d+)', msg)
            wait = int(m.group(1)) + 2 if m else 30 * (attempt + 1)
            # ponytail: failed 503s still burn the 20/day free quota, so few retries and no day-long sleeps
            if e.code in (429, 500, 503) and attempt < 2 and wait < 300:
                print(f"  HTTP {e.code}, retry in {wait}s: {msg[:200]!r}", file=sys.stderr)
                time.sleep(wait)
                continue
            raise RuntimeError(f"HTTP {e.code}: {msg[:600]}")


def upload(path, mime):
    size = os.path.getsize(path)
    hdr, _ = call("POST", f"{API}/upload/v1beta/files", {"file": {"display_name": os.path.basename(path)}}, {
        "X-Goog-Upload-Protocol": "resumable", "X-Goog-Upload-Command": "start",
        "X-Goog-Upload-Header-Content-Length": str(size), "X-Goog-Upload-Header-Content-Type": mime})
    _, body = call("POST", hdr["x-goog-upload-url"], open(path, "rb").read(),
                   {"X-Goog-Upload-Offset": "0", "X-Goog-Upload-Command": "upload, finalize"}, raw=True)
    f = json.loads(body)["file"]
    while f.get("state") != "ACTIVE":
        if f.get("state") == "FAILED":
            raise RuntimeError(f"file processing FAILED: {f}")
        time.sleep(2)
        f = json.loads(call("GET", f"{API}/v1beta/{f['name']}")[1])
    return f["uri"]


def frames_and_audio(src, tmp):
    dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", src]))
    fps = min(0.5, 20 / dur)  # ~every 2 s, at most 20 frames evenly (app rule)
    # ponytail: long edge <= 768 without upscaling (plan's literal scale=768:-2 would upscale vertical 360p)
    vf = f"fps={fps},scale='min(768,iw)':'min(768,ih)':force_original_aspect_ratio=decrease"
    subprocess.run(["ffmpeg", "-v", "error", "-i", src, "-vf", vf, "-q:v", "4", f"{tmp}/f_%02d.jpg"], check=True)
    subprocess.run(["ffmpeg", "-v", "error", "-i", src, "-vn", "-c:a", "copy", f"{tmp}/a.m4a"], check=True)
    return sorted(f"{tmp}/{n}" for n in os.listdir(tmp) if n.endswith(".jpg")), f"{tmp}/a.m4a"


def run(model, mode, rid):
    row = MANIFEST[rid]
    vid = row["url"].split("v=")[1]
    t0 = time.time()
    extra = {}
    if mode == "YT":
        parts = [{"file_data": {"file_uri": row["url"]}}]
    elif mode in ("A480", "A720"):
        parts = [{"file_data": {"mime_type": "video/mp4", "file_uri": upload(f"{ROOT}/spikes/inputs/{vid}_{mode[1:]}.mp4", "video/mp4")}}]
    else:
        tmp = tempfile.mkdtemp(dir=OUT)
        frames, audio = frames_and_audio(f"{ROOT}/spikes/inputs/{vid}_480.mp4", tmp)
        import base64
        parts = [{"inline_data": {"mime_type": "image/jpeg", "data": base64.b64encode(open(f, "rb").read()).decode()}} for f in frames]
        parts.append({"file_data": {"mime_type": "audio/mp4", "file_uri": upload(audio, "audio/mp4")}})
        extra = {"frames": len(frames), "frame_bytes": sum(os.path.getsize(f) for f in frames)}
    t1 = time.time()
    req = {"contents": [{"parts": parts + [{"text": PROMPT}]}],
           "generationConfig": {"responseMimeType": "application/json", "responseSchema": SCHEMA}}
    _, body = call("POST", f"{API}/v1beta/models/{model}:generateContent", req)
    t2 = time.time()
    resp = json.loads(body)
    text = "".join(p.get("text", "") for p in resp["candidates"][0]["content"]["parts"])
    try:
        parsed, valid = json.loads(text), True
    except Exception:
        parsed, valid = None, False
    rec = {"id": rid, "mode": mode, "model": model, "upload_s": round(t1 - t0, 1), "generate_s": round(t2 - t1, 1),
           "wall_s": round(t2 - t0, 1), "usage": resp.get("usageMetadata"), "json_valid": valid, "result": parsed,
           "raw": None if valid else text, **extra}
    json.dump(rec, open(f"{OUT}/{rid}_{mode}_{model}.json", "w"), indent=1, ensure_ascii=False)
    ents = [(e["type"], e.get("qualifier"), e["name"], e["evidence"]) for e in (parsed or {}).get("entities", [])]
    print(f"{rid} {mode} wall={rec['wall_s']}s gen={rec['generate_s']}s valid={valid} tok={(resp.get('usageMetadata') or {}).get('totalTokenCount')} ents={ents}")


if __name__ == "__main__":
    model, mode, *ids = sys.argv[1:]
    for rid in ids:
        try:
            run(model, mode, rid)
        except Exception as e:
            print(f"{rid} {mode} ERROR {e}")
            json.dump({"id": rid, "mode": mode, "model": model, "error": str(e)}, open(f"{OUT}/{rid}_{mode}_{model}.json", "w"))
