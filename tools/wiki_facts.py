"""Fiches « En savoir plus » : téléchargement des sources Wikipédia, vérification des citations, génération.

Chaque texte d'une fiche (tools/facts_sources.json) est accompagné d'une ou plusieurs citations
tirées d'un article Wikipédia en français. Ce script :

  fetch           télécharge l'article de chaque département (texte brut + numéro de révision)
                  dans tools/raw/wiki/ ; pauses et nouvelles tentatives contre les erreurs 429 ;
  fetch-title T   télécharge un article supplémentaire (cité par une fiche avec "article") ;
  show ID [SECTIONS]   affiche les sections d'un article (par défaut : toponymie, histoire, légendes…) ;
  grep ID MOT...       cherche des mots dans un article et affiche le contexte ;
  build           vérifie que chaque citation figure mot pour mot dans la révision téléchargée,
                  puis écrit app/src/main/assets/geo/facts.json et le tableau de
                  docs/verification-fiches.md. S'arrête à la première citation introuvable.

Le texte de la fiche est une reformulation ; la citation est l'extrait qui la justifie.

Usage : python -I -X utf8 tools/wiki_facts.py build
"""
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "tools" / "raw" / "wiki"
SOURCES = ROOT / "tools" / "facts_sources.json"
DEPARTMENTS = ROOT / "app" / "src" / "main" / "assets" / "geo" / "departements.json"
FACTS = ROOT / "app" / "src" / "main" / "assets" / "geo" / "facts.json"
REGISTER = ROOT / "docs" / "verification-fiches.md"
API = "https://fr.wikipedia.org/w/api.php"
USER_AGENT = "FranceGeographyQuiz/1.0 (https://github.com/saksa84/france-geography-quiz; outil de vérification)"
MAX_TEXT = 300
MAX_ANECDOTES = 3

# Titres dont la forme « Nom (département) » n'existe pas ou désigne autre chose.
TITLE_OVERRIDES = {
    "DEP-59": "Nord (département français)",
    "DEP-75": "Paris",
    "DEP-971": "Guadeloupe",
    "DEP-972": "Martinique",
    "DEP-973": "Guyane",
    "DEP-974": "La Réunion",
    "DEP-976": "Mayotte",
}

DEFAULT_SECTIONS = ("topon", "étymolog", "origine du nom", "nom", "histoire", "légende", "curiosit",
                    "patrimoine", "personnalit", "symbol", "héraldique", "anecdote")


def departments():
    data = json.loads(DEPARTMENTS.read_text(encoding="utf-8"))
    return {e["id"]: e["name"] for e in data["entities"] if e["type"] == "department"}


def api_get(params):
    params = {**params, "format": "json", "formatversion": "2"}
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):
                time.sleep(5 * (attempt + 1))
                continue
            raise
    raise RuntimeError("Wikipédia ne répond pas : " + url)


def fetch_article(title):
    """Texte brut (sections au format == Titre ==) et numéro de révision de l'article, redirections suivies."""
    data = api_get({"action": "query", "prop": "extracts|revisions", "explaintext": 1,
                    "exsectionformat": "wiki", "rvprop": "ids|timestamp", "redirects": 1, "titles": title})
    page = data["query"]["pages"][0]
    if page.get("missing") or "extract" not in page:
        return None
    rev = page["revisions"][0]
    return {"title": page["title"], "revid": rev["revid"], "timestamp": rev["timestamp"],
            "retrieved": time.strftime("%Y-%m-%d"), "text": page["extract"]}


def cache_path(key):
    return RAW / (re.sub(r"[^\w-]+", "_", key) + ".json")

def save(key, article):
    RAW.mkdir(parents=True, exist_ok=True)
    cache_path(key).write_text(json.dumps(article, ensure_ascii=False, indent=1), encoding="utf-8")


def load(key):
    path = cache_path(key)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def cmd_fetch():
    for dep_id, name in departments().items():
        if load(dep_id):
            continue
        candidates = [TITLE_OVERRIDES[dep_id]] if dep_id in TITLE_OVERRIDES else [name + " (département)", name]
        article = None
        for title in candidates:
            article = fetch_article(title)
            time.sleep(1.5)
            if article and (dep_id in TITLE_OVERRIDES or "département" in article["text"][:3000]):
                break
            article = None
        if not article:
            print("INTROUVABLE", dep_id, name)
            continue
        save(dep_id, article)
        print(dep_id, article["title"], article["revid"], len(article["text"]))


def cmd_fetch_title(title):
    article = fetch_article(title)
    if not article:
        sys.exit("Article introuvable : " + title)
    save("A_" + title, article)
    print(article["title"], article["revid"], len(article["text"]))


def sections(text):
    """Découpe le texte brut en (titre, contenu)."""
    parts = re.split(r"^(={2,}\s*.+?\s*={2,})\s*$", text, flags=re.M)
    result = [("Introduction", parts[0])]
    for i in range(1, len(parts), 2):
        result.append((parts[i].strip("= ").strip(), parts[i + 1]))
    return result


def article_for(key):
    article = load(key) or load("A_" + key)
    if not article:
        sys.exit("Article non téléchargé : " + key)
    return article


def cmd_show(key, wanted=None):
    article = article_for(key)
    keys = [w.lower() for w in wanted] if wanted else DEFAULT_SECTIONS
    print("#", article["title"], article["revid"])
    for title, body in sections(article["text"]):
        if title == "Introduction" or any(k in title.lower() for k in keys):
            body = body.strip()
            if body:
                print("\n==", title, "==\n" + body[:2500])


def cmd_grep(key, words):
    text = article_for(key)["text"]
    for word in words:
        for m in re.finditer(re.escape(word), text, flags=re.I):
            print("…" + text[max(0, m.start() - 250):m.end() + 250].replace("\n", " ") + "…\n")


def norm(s):
    s = unicodedata.normalize("NFC", s).replace("’", "'").replace(" ", " ").replace(" ", " ")
    return re.sub(r"\s+", " ", s).strip()


def url_of(article):
    title = article["title"].replace(" ", "_")
    return f"https://fr.wikipedia.org/w/index.php?title={urllib.parse.quote(title)}&oldid={article['revid']}"


def cmd_build():
    deps = departments()
    entries = json.loads(SOURCES.read_text(encoding="utf-8"))["facts"]
    facts, rows, errors = [], [], []
    seen = set()
    for entry in entries:
        dep_id = entry["id"]
        if dep_id not in deps:
            errors.append(f"{dep_id} : identifiant inconnu")
            continue
        if dep_id in seen:
            errors.append(f"{dep_id} : en double")
        seen.add(dep_id)
        out = {"id": dep_id}
        items = [(k, entry[k]) for k in ("nameOrigin", "history", "legend") if k in entry]
        items += [("anecdote", a) for a in entry.get("anecdotes", [])]
        if len([1 for k, _ in items if k == "anecdote"]) > MAX_ANECDOTES:
            errors.append(f"{dep_id} : trop d'anecdotes")
        for kind, item in items:
            text = item["text"]
            if len(text) > MAX_TEXT:
                errors.append(f"{dep_id} {kind} : {len(text)} caractères (> {MAX_TEXT})")
            if not item.get("quotes"):
                errors.append(f"{dep_id} {kind} : aucune citation")
            for quote in item.get("quotes", []):
                # "article" : article supplémentaire (fetch-title) ; "dep" : article d'un autre département.
                key = ("A_" + item["article"]) if "article" in item else item.get("dep", dep_id)
                article = load(key)
                if not article:
                    errors.append(f"{dep_id} {kind} : article non téléchargé ({key})")
                    continue
                if norm(quote) not in norm(article["text"]):
                    errors.append(f"{dep_id} {kind} : citation introuvable dans {article['title']} : « {quote} »")
                    continue
                rows.append((dep_id, deps[dep_id], kind, article, quote))
            if kind == "anecdote":
                out.setdefault("anecdotes", []).append(text)
            else:
                out[kind] = text
        out["reviewed"] = True
        facts.append(out)
    if errors:
        print("\n".join(errors))
        sys.exit(f"{len(errors)} erreur(s) : rien n'est écrit.")

    order = list(deps)
    facts.sort(key=lambda f: order.index(f["id"]))
    origin = ("Fiches rédigées avec Claude Code à partir d'articles de Wikipédia en français (CC BY-SA 4.0), "
              "reformulés ; chaque texte est justifié par un extrait d'une révision précise, listé dans "
              "docs/verification-fiches.md et vérifié par tools/wiki_facts.py.")
    lines = ['{', '  "schemaVersion": 1,', '  "origin": ' + json.dumps(origin, ensure_ascii=False) + ',', '  "facts": [']
    lines.append(",\n".join("    " + json.dumps(f, ensure_ascii=False) for f in facts))
    lines += ['  ]', '}', '']
    FACTS.write_text("\n".join(lines), encoding="utf-8")

    labels = {"nameOrigin": "Origine du nom", "history": "Histoire", "legend": "Légende", "anecdote": "Anecdote"}
    rows.sort(key=lambda r: order.index(r[0]))
    table = ["| Lieu | Rubrique | Source (révision) | Extrait qui justifie le texte | Consultée le |",
             "|---|---|---|---|---|"]
    for dep_id, name, kind, article, quote in rows:
        q = norm(quote).replace("|", "\\|")
        table.append(f"| {dep_id} {name} | {labels[kind]} | [{article['title']}, révision {article['revid']}]({url_of(article)}) "
                     f"| « {q} » | {article['retrieved']} |")
    register = REGISTER.read_text(encoding="utf-8")
    head = register.split("## Fiches vérifiées")[0]
    REGISTER.write_text(head + "## Fiches vérifiées\n\nTableau produit par `tools/wiki_facts.py build` "
                        "(ne pas le modifier à la main : modifier `tools/facts_sources.json`).\n\n"
                        + "\n".join(table) + "\n", encoding="utf-8")
    print(f"{len(facts)} fiches, {len(rows)} citations vérifiées.")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if args[0] == "fetch":
        cmd_fetch()
    elif args[0] == "fetch-title":
        cmd_fetch_title(" ".join(args[1:]))
    elif args[0] == "show":
        cmd_show(args[1], args[2:] or None)
    elif args[0] == "grep":
        cmd_grep(args[1], args[2:])
    elif args[0] == "build":
        cmd_build()
    else:
        sys.exit(__doc__)
