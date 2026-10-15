from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from collections import defaultdict
import json
import os

app = Flask(__name__, static_folder="public")
CORS(app)

DATA_FILE = "deaths.json"

deaths: list[dict] = []
if os.path.exists(DATA_FILE):
    with open(DATA_FILE) as f:
        deaths = json.load(f)


def save():
    with open(DATA_FILE, "w") as f:
        json.dump(deaths, f, indent=2)


# --- Routes API ---
# Ces routes sont intentionnellement identiques à celles qu'exposera le serveur Express futur.
# Migration : remplacer ce fichier par server.js sans toucher au mod C# ni au frontend.

@app.route("/api/deaths", methods=["POST"])
def receive_death():
    d = request.get_json(force=True)
    d["id"] = len(deaths)
    deaths.append(d)
    save()
    print(f"[death] #{d['id']} | {d.get('chapter')} | room: {d.get('room')}")
    return jsonify({"ok": True, "id": d["id"], "total": len(deaths)})


@app.route("/api/events", methods=["POST"])
def receive_event():
    e = request.get_json(force=True)
    print(f"[event] {e.get('type')} | {e.get('chapter')}")
    return jsonify({"ok": True})


@app.route("/api/stats", methods=["GET"])
def stats():
    if not deaths:
        return jsonify({"total": 0, "byRoom": {}, "byChapter": {}, "recentDeaths": [], "lastDeath": None})

    by_room: dict[str, int] = defaultdict(int)
    by_chapter: dict[str, int] = defaultdict(int)

    for d in deaths:
        key = f"{d.get('chapter', '?')} / {d.get('room', '?')}"
        by_room[key] += 1
        by_chapter[d.get("chapter", "?")] += 1

    top_rooms = dict(sorted(by_room.items(), key=lambda x: x[1], reverse=True)[:10])

    return jsonify({
        "total":        len(deaths),
        "byRoom":       top_rooms,
        "byChapter":    dict(by_chapter),
        "recentDeaths": deaths[-50:],
        "lastDeath":    deaths[-1],
    })


@app.route("/api/deaths", methods=["DELETE"])
def reset():
    deaths.clear()
    save()
    print("[reset] données effacées")
    return jsonify({"ok": True})


# Sert le dashboard statique (index.html dans public/)
@app.route("/", defaults={"path": ""})
@app.route("/<path:path>")
def serve_static(path):
    target = os.path.join("public", path)
    if path and os.path.exists(target):
        return send_from_directory("public", path)
    return send_from_directory("public", "index.html")


if __name__ == "__main__":
    os.makedirs("public", exist_ok=True)
    print("Dashboard Celeste Death Tracker → http://localhost:5000")
    app.run(host="0.0.0.0", port=5000, debug=False)
