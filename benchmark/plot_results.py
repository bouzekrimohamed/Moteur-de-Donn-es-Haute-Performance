"""
Genere les graphiques de benchmark a partir du JSON retourne par /benchmark/run.

Usage :
  # Mesures "avant" seulement :
  python plot_results.py avant.json

  # Comparaison "avant" vs "apres" optimisation :
  python plot_results.py avant.json apres.json

Prerequis :
  pip install matplotlib

Recuperer les donnees (serveur demarre sur port 8080) :
  curl http://localhost:8080/benchmark/run > avant.json
"""

import json
import os
import sys

try:
    import matplotlib.pyplot as plt
    import matplotlib.ticker as mticker
except ImportError:
    print("matplotlib manquant. Installez-le : pip install matplotlib")
    sys.exit(1)


def charger(fichier: str) -> list:
    with open(fichier, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data.get("resultats", [])


METRIQUES = [
    ("loadMs",        "Chargement (LOAD)",              "#2196F3"),
    ("whereMs",       "Filtre WHERE (annee = 2022)",     "#4CAF50"),
    ("groupByMs",     "GROUP BY categorie",              "#FF9800"),
    ("aggregationMs", "GROUP BY region + SUM(prix)",     "#9C27B0"),
]


def fmt_x(nb: int) -> str:
    return f"{nb / 1_000_000:.1f}M" if nb >= 1_000_000 else f"{nb // 1_000}k"


def tracer(avant: list, apres: list = None, dossier: str = "graphiques"):
    os.makedirs(dossier, exist_ok=True)

    nb_x   = [r["nbLignes"] for r in avant]
    labels = [fmt_x(n) for n in nb_x]

    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    titre = "Benchmark Moteur de Donnees - Temps d'execution (ms)"
    if apres:
        titre += "\n(bleu = avant optimisation  |  rouge = apres)"
    fig.suptitle(titre, fontsize=13, fontweight="bold", y=1.01)

    for ax, (cle, titre_ax, couleur) in zip(axes.flat, METRIQUES):
        vals_avant = [r[cle] for r in avant]

        ax.plot(nb_x, vals_avant, "o-", color=couleur,
                linewidth=2, markersize=6, label="Avant")

        if apres:
            nb_ap   = [r["nbLignes"] for r in apres]
            vals_ap = [r[cle] for r in apres]
            ax.plot(nb_ap, vals_ap, "s--", color="#F44336",
                    linewidth=2, markersize=6, label="Apres")
            ax.legend(fontsize=9)

        ax.set_title(titre_ax, fontweight="bold")
        ax.set_xlabel("Nombre de lignes")
        ax.set_ylabel("Temps (ms)")
        ax.set_xticks(nb_x)
        ax.set_xticklabels(labels, rotation=15)
        ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{int(v):,}"))
        ax.grid(True, alpha=0.3)

        for x, y in zip(nb_x, vals_avant):
            ax.annotate(f"{y:,}ms", (x, y),
                        textcoords="offset points", xytext=(0, 8),
                        ha="center", fontsize=7)

    plt.tight_layout()
    chemin = os.path.join(dossier, "benchmark.png")
    plt.savefig(chemin, dpi=150, bbox_inches="tight")
    print(f"\nGraphique sauvegarde : {os.path.abspath(chemin)}")
    plt.show()


def afficher_tableau(avant: list, apres: list = None):
    entete = f"{'Lignes':>10} | {'LOAD ms':>9} | {'WHERE ms':>9} | {'GROUP BY ms':>11} | {'SUM ms':>8} | {'Debit L/s':>12}"
    print("\n" + "=" * len(entete))
    print(entete)
    print("-" * len(entete))

    apres_map = {r["nbLignes"]: r for r in apres} if apres else {}

    for r in avant:
        print(
            f"{r['nbLignes']:>10,} | {r['loadMs']:>9,} | {r['whereMs']:>9,} | "
            f"{r['groupByMs']:>11,} | {r['aggregationMs']:>8,} | "
            f"{r.get('loadLignesParSeconde', 0):>12,}"
        )

    if apres:
        print("\n" + "=" * len(entete))
        print(f"{'--- APRES OPTIMISATION ---':^{len(entete)}}")
        print(entete)
        print("-" * len(entete))
        for r in avant:
            a = apres_map.get(r["nbLignes"])
            if not a:
                continue
            gain_load  = round((1 - a["loadMs"]  / max(r["loadMs"],  1)) * 100)
            gain_where = round((1 - a["whereMs"] / max(r["whereMs"], 1)) * 100)
            print(
                f"{a['nbLignes']:>10,} | {a['loadMs']:>9,} | {a['whereMs']:>9,} | "
                f"{a['groupByMs']:>11,} | {a['aggregationMs']:>8,} | "
                f"{a.get('loadLignesParSeconde', 0):>12,}"
                f"   (LOAD {gain_load:+d}%  WHERE {gain_where:+d}%)"
            )

    print("=" * len(entete))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(0)

    f_avant = sys.argv[1]
    f_apres = sys.argv[2] if len(sys.argv) > 2 else None

    if not os.path.exists(f_avant):
        print(f"Fichier introuvable : {f_avant}")
        print("Demarre le serveur puis : curl http://localhost:8080/benchmark/run > avant.json")
        sys.exit(1)

    avant = charger(f_avant)
    apres = charger(f_apres) if f_apres else None

    afficher_tableau(avant, apres)
    tracer(avant, apres)
