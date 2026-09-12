"""Read-only structural audit for an Anki APKG collection."""
import json
import re
import sqlite3
import sys
import tempfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: audit_apkg.py APKG_PATH OUTPUT_JSON")

apkg = Path(sys.argv[1])
output = Path(sys.argv[2])
with tempfile.TemporaryDirectory(prefix="japanese-apkg-audit-") as tmp:
    collection = Path(tmp) / "collection.anki21"
    with zipfile.ZipFile(apkg) as archive:
        with archive.open("collection.anki21") as source, collection.open("wb") as destination:
            while chunk := source.read(1024 * 1024):
                destination.write(chunk)
    db = sqlite3.connect(f"file:{collection}?mode=ro", uri=True)
    db.create_collation("unicase", lambda left, right: (left.casefold() > right.casefold()) - (left.casefold() < right.casefold()))
    db.row_factory = sqlite3.Row
    models = []
    for note_type in db.execute("select id, name from notetypes"):
        fields = [row[0] for row in db.execute("select name from fields where ntid=? order by ord", (note_type["id"],))]
        stats = {name: {"empty": 0, "html": 0, "maxLength": 0, "examples": []} for name in fields}
        tags = Counter()
        total = 0
        rows = db.execute("select id, tags, flds from notes where mid=?", (note_type["id"],))
        for row in rows:
            total += 1
            tags.update(tag for tag in row["tags"].split() if tag)
            values = row["flds"].split("\x1f")
            for index, name in enumerate(fields):
                value = values[index] if index < len(values) else ""
                normalized = value.replace("\u2063", "").strip()
                current = stats[name]
                if not normalized:
                    current["empty"] += 1
                if re.search(r"<[^>]+>", value):
                    current["html"] += 1
                current["maxLength"] = max(current["maxLength"], len(value))
                if normalized and len(current["examples"]) < 3:
                    current["examples"].append(value[:600])
        models.append({
            "id": note_type["id"], "name": note_type["name"], "notes": total, "fieldCount": len(fields),
            "fields": [{"name": name, **stats[name]} for name in fields],
            "tags": tags.most_common(100),
        })
    db.close()
    output.write_text(json.dumps({"apkg": str(apkg), "models": models}, ensure_ascii=False, indent=2), encoding="utf-8")
