# Throwaway S1 runner (D12). Usage: python3 -I spikes/s1.py MODEL SRC_MODE [--schema] ID...
# Feeds S2 entities (spikes/work/s2/<id>_<SRC_MODE>.json) to the grounded research prompt.
import json, os, re, sys, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from s2 import API, ROOT, call  # noqa: E402

OUT = f"{ROOT}/spikes/work/" + os.environ.get("RUN1", "s1")
os.makedirs(OUT, exist_ok=True)
plan = open(f"{ROOT}/docs/plan.md").read()
TEMPLATE = re.search(r"### Web research prompt.*?```text\n(.*?)```", plan, re.S).group(1)
SCHEMA = {"type": "object", "properties": {"items": {"type": "array", "items": {"type": "object", "properties": {
    "index": {"type": "integer"}, "title": {"type": "string"},
    "verdict": {"type": "string", "enum": ["supported", "disputed", "unclear", "confirmed", "best_match", "not_found"]},
    "whatItIs": {"type": "string"}, "keyFacts": {"type": "array", "items": {"type": "string"}},
    "howToTry": {"type": "string"}, "whyInVideo": {"type": "string"}},
    "required": ["index", "title", "verdict", "whatItIs", "keyFacts", "howToTry", "whyInVideo"]}}}, "required": ["items"]}


def prompt(analysis):
    ents = analysis["entities"][:6]
    head, rest = TEMPLATE.split("{for each, numbered from 0:}\n")
    line, tail = rest.split("\n", 1)
    items = "\n".join(f'{i}. [{e["type"]}] {e["name"]}{" — " + e["qualifier"] if e.get("qualifier") else ""} — the video says: "{e["saidInVideo"]}"'
                      for i, e in enumerate(ents))
    return (head + items + "\n" + tail).replace("{summary}", analysis["summary"]).replace("{language}", "English"), ents


def lenient(text):
    text = re.sub(r"^```(?:json)?|```$", "", text.strip(), flags=re.M)
    i, j = text.find("{"), text.rfind("}")
    return json.loads(text[i:j + 1])


def run(model, src, schema, rid):
    analysis = json.load(open(f"{ROOT}/spikes/work/s2/{rid}_{src}.json"))["result"]
    if not analysis["entities"]:
        print(f"{rid} no entities, skipped (no call)")
        return
    p, ents = prompt(analysis)
    req = {"contents": [{"parts": [{"text": p}]}], "tools": [{"google_search": {}}]}
    if schema:
        req["generationConfig"] = {"responseMimeType": "application/json", "responseSchema": SCHEMA}
    t0 = time.time()
    resp = json.loads(call("POST", f"{API}/v1beta/models/{model}:generateContent", req)[1])
    dt = round(time.time() - t0, 1)
    cand = resp["candidates"][0]
    text = "".join(x.get("text", "") for x in cand.get("content", {}).get("parts", []))
    gm = cand.get("groundingMetadata") or {}
    try:
        parsed = lenient(text)
    except Exception:
        parsed = None
    chunks = [c.get("web", {}) for c in gm.get("groundingChunks", [])]
    rec = {"id": rid, "model": model, "schema": schema, "latency_s": dt, "usage": resp.get("usageMetadata"),
           "entities": ents, "parsed": parsed, "raw": text, "queries": gm.get("webSearchQueries"),
           "chunks": chunks, "has_search_entry_point": bool((gm.get("searchEntryPoint") or {}).get("renderedContent")),
           "supports": len(gm.get("groundingSupports", []))}
    json.dump(rec, open(f"{OUT}/{rid}_{model}{'_schema' if schema else ''}.json", "w"), indent=1, ensure_ascii=False)
    items = [(it.get("index"), it.get("title"), it.get("verdict")) for it in (parsed or {}).get("items", [])]
    print(f"{rid} schema={schema} {dt}s parsed={parsed is not None} chunks={len(chunks)} sep={rec['has_search_entry_point']} "
          f"tok={(resp.get('usageMetadata') or {}).get('totalTokenCount')} items={items}")


if __name__ == "__main__":
    model, src, *ids = sys.argv[1:]
    schema = "--schema" in ids
    for rid in [i for i in ids if i != "--schema"]:
        try:
            run(model, src, schema, rid)
        except Exception as e:
            print(f"{rid} schema={schema} ERROR {e}")
