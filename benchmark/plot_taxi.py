"""
Genere les graphiques de performance pour le benchmark Yellow Taxi NYC.

Usage :
  python plot_taxi.py taxi_avant.json
  python plot_taxi.py taxi_avant.json taxi_apres.json   # comparaison avant/apres

Recuperer les donnees (serveur sur port 8080) :
  curl.exe -s http://localhost:8080/benchmark/taxi/run -o taxi_avant.json
  curl.exe -s "http://localhost:8080/benchmark/taxi/run?les2=true" -o taxi_7M.json

Prerequis :
  pip install matplotlib
"""

import json, os, sys
try:
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import matplotlib.ticker as mticker
except ImportError:
    print("matplotlib manquant : pip install matplotlib")
    sys.exit(1)

# ---------------------------------------------------------------------------
# Mapping codes payment_type → libelle
# ---------------------------------------------------------------------------
PAYMENT_LABELS = {
    1: "Carte bancaire",
    2: "Especes",
    3: "Pas de frais",
    4: "Litige",
    5: "Inconnu",
    6: "Annule",
}

COULEURS = ["#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#00BCD4"]


# ---------------------------------------------------------------------------
# Chargement
# ---------------------------------------------------------------------------

def charger(fichier: str) -> dict:
    with open(fichier, "r", encoding="utf-8") as f:
        return json.load(f)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def fmt_ms(ms: int) -> str:
    if ms >= 1000:
        return f"{ms/1000:.1f}s"
    return f"{ms}ms"

def fmt_lignes(n: int) -> str:
    if n >= 1_000_000:
        return f"{n/1_000_000:.2f}M"
    return f"{n//1_000}k"


# ---------------------------------------------------------------------------
# Graphique 1 — Temps d'execution des 6 requetes (barres horizontales)
# ---------------------------------------------------------------------------

def graphe_temps_requetes(donnees: dict, donnees_apres: dict = None, ax=None):
    requetes = donnees["requetes"]
    titres   = [r["titre"].replace(" (", "\n(") for r in requetes]
    temps    = [r["dureeMs"] for r in requetes]

    y = range(len(titres))

    if ax is None:
        fig, ax = plt.subplots(figsize=(10, 5))

    bars = ax.barh(y, temps, color=COULEURS[:len(titres)], alpha=0.85, label="Avant optimisation")

    if donnees_apres:
        temps_ap = [r["dureeMs"] for r in donnees_apres["requetes"]]
        ax.barh([i + 0.35 for i in y], temps_ap, height=0.35,
                color="#F44336", alpha=0.7, label="Apres optimisation")

    ax.set_yticks(y)
    ax.set_yticklabels(titres, fontsize=8)
    ax.set_xlabel("Temps (ms)")
    ax.set_title("Temps d'execution des requetes business\n(Yellow Taxi NYC)", fontweight="bold")
    ax.xaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{int(v):,}"))
    ax.grid(axis="x", alpha=0.3)

    for bar, t in zip(bars, temps):
        ax.text(bar.get_width() + max(temps)*0.01, bar.get_y() + bar.get_height()/2,
                fmt_ms(t), va="center", fontsize=8)

    if donnees_apres:
        ax.legend(fontsize=8)


# ---------------------------------------------------------------------------
# Graphique 2 — Revenus par mode de paiement (Q1)
# ---------------------------------------------------------------------------

def graphe_revenus_paiement(donnees: dict, ax=None):
    q1 = next((r for r in donnees["requetes"] if "paiement" in r["titre"].lower()), None)
    if not q1 or not q1.get("apercu"):
        return

    apercu  = sorted(q1["apercu"], key=lambda r: r.get("SUM(total_amount)", 0), reverse=True)
    labels  = [PAYMENT_LABELS.get(int(r.get("payment_type", 0)), f"Type {r.get('payment_type')}") for r in apercu]
    valeurs = [r.get("SUM(total_amount)", 0) for r in apercu]
    counts  = [r.get("count", 0) for r in apercu]

    if ax is None:
        fig, ax = plt.subplots(figsize=(8, 5))

    bars = ax.bar(labels, valeurs, color=COULEURS[:len(labels)], alpha=0.85)
    ax.set_ylabel("Revenus totaux ($)")
    ax.set_title("Revenus par mode de paiement\n(SUM total_amount)", fontweight="bold")
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"${v/1_000_000:.1f}M"))
    ax.tick_params(axis="x", labelsize=8)
    ax.grid(axis="y", alpha=0.3)

    for bar, v, c in zip(bars, valeurs, counts):
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() * 1.01,
                f"${v/1_000_000:.1f}M\n({fmt_lignes(c)} courses)",
                ha="center", va="bottom", fontsize=7)


# ---------------------------------------------------------------------------
# Graphique 3 — Tarif moyen par nombre de passagers (Q3)
# ---------------------------------------------------------------------------

def graphe_tarif_passagers(donnees: dict, ax=None):
    q3 = next((r for r in donnees["requetes"] if "passager" in r["titre"].lower()), None)
    if not q3 or not q3.get("apercu"):
        return

    apercu = sorted(
        [r for r in q3["apercu"] if r.get("passenger_count") and int(r.get("passenger_count", 0)) > 0],
        key=lambda r: int(r.get("passenger_count", 0))
    )
    labels  = [f"{int(r['passenger_count'])} pass." for r in apercu]
    valeurs = [r.get("AVG(fare_amount)", 0) for r in apercu]

    if ax is None:
        fig, ax = plt.subplots(figsize=(8, 5))

    ax.plot(labels, valeurs, "o-", color="#2196F3", linewidth=2, markersize=8)
    ax.fill_between(range(len(labels)), valeurs, alpha=0.1, color="#2196F3")
    ax.set_ylabel("Tarif moyen ($)")
    ax.set_title("Tarif moyen par nombre de passagers\n(AVG fare_amount)", fontweight="bold")
    ax.set_xticks(range(len(labels)))
    ax.set_xticklabels(labels)
    ax.grid(True, alpha=0.3)

    for i, (l, v) in enumerate(zip(labels, valeurs)):
        ax.annotate(f"${v:.2f}", (i, v), textcoords="offset points",
                    xytext=(0, 8), ha="center", fontsize=8)


# ---------------------------------------------------------------------------
# Graphique 4 — Infos LOAD + débit
# ---------------------------------------------------------------------------

def graphe_load_info(donnees: dict, donnees_apres: dict = None, ax=None):
    if ax is None:
        fig, ax = plt.subplots(figsize=(6, 4))

    ax.axis("off")

    nb     = donnees["nbLignes"]
    load   = donnees["loadMs"]
    debit  = donnees.get("loadLignesParSeconde", 0)
    source = donnees.get("source", "N/A")

    lignes = [
        ("Dataset",    source),
        ("Lignes",     f"{nb:,}"),
        ("LOAD",       fmt_ms(load)),
        ("Debit",      f"{debit:,.0f} lignes/s"),
    ]

    if donnees_apres:
        load_ap  = donnees_apres["loadMs"]
        debit_ap = donnees_apres.get("loadLignesParSeconde", 0)
        gain     = round((1 - load_ap / max(load, 1)) * 100)
        lignes += [
            ("", ""),
            ("Apres optim.", ""),
            ("LOAD",         f"{fmt_ms(load_ap)}  ({gain:+d}%)"),
            ("Debit",        f"{debit_ap:,.0f} lignes/s"),
        ]

    ax.set_title("Informations LOAD", fontweight="bold")
    y = 0.9
    for cle, val in lignes:
        if cle:
            ax.text(0.05, y, cle + " :", fontsize=10, transform=ax.transAxes, color="#555")
            ax.text(0.45, y, val,         fontsize=10, transform=ax.transAxes, fontweight="bold")
        y -= 0.12


# ---------------------------------------------------------------------------
# Figure principale
# ---------------------------------------------------------------------------

def tracer(fichier_avant: str, fichier_apres: str = None, dossier: str = "graphiques"):
    os.makedirs(dossier, exist_ok=True)
    avant = charger(fichier_avant)
    apres = charger(fichier_apres) if fichier_apres else None

    fig = plt.figure(figsize=(18, 12))
    fig.suptitle(
        f"Benchmark Yellow Taxi NYC — {fmt_lignes(avant['nbLignes'])} lignes",
        fontsize=15, fontweight="bold"
    )

    ax1 = fig.add_subplot(2, 2, (1, 2))   # toute la ligne du haut
    ax2 = fig.add_subplot(2, 2, 3)
    ax3 = fig.add_subplot(2, 2, 4)

    graphe_temps_requetes(avant, apres, ax=ax1)
    graphe_revenus_paiement(avant, ax=ax2)
    graphe_tarif_passagers(avant, ax=ax3)

    plt.tight_layout(rect=[0, 0, 1, 0.96])

    chemin = os.path.join(dossier, "benchmark_taxi.png")
    plt.savefig(chemin, dpi=150, bbox_inches="tight")
    print(f"Graphique sauvegarde : {os.path.abspath(chemin)}")
    plt.show()

    # Figure LOAD séparée
    fig2, ax = plt.subplots(figsize=(6, 4))
    graphe_load_info(avant, apres, ax=ax)
    chemin2 = os.path.join(dossier, "benchmark_taxi_load.png")
    plt.savefig(chemin2, dpi=150, bbox_inches="tight")
    print(f"Graphique LOAD sauvegarde : {os.path.abspath(chemin2)}")
    plt.show()


# ---------------------------------------------------------------------------
# Tableau console
# ---------------------------------------------------------------------------

def afficher_tableau(avant: dict, apres: dict = None):
    nb = avant["nbLignes"]
    print(f"\n{'='*65}")
    print(f"  Yellow Taxi NYC — {nb:,} lignes ({avant.get('source','')})")
    print(f"  LOAD : {avant['loadMs']:,} ms  |  {avant.get('loadLignesParSeconde',0):,} lignes/s")
    print(f"{'='*65}")
    print(f"  {'Requete':<38} {'Avant':>8}  {'Resultats':>10}")
    print(f"  {'-'*60}")

    apres_map = {r["titre"]: r for r in apres["requetes"]} if apres else {}

    for r in avant["requetes"]:
        ligne = f"  {r['titre']:<38} {r['dureeMs']:>6}ms  {r['nbResultats']:>10,}"
        if r["titre"] in apres_map:
            a    = apres_map[r["titre"]]
            gain = round((1 - a["dureeMs"] / max(r["dureeMs"], 1)) * 100)
            ligne += f"   → {a['dureeMs']:>5}ms ({gain:+d}%)"
        print(ligne)

    print(f"{'='*65}\n")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(0)

    f_avant = sys.argv[1]
    f_apres = sys.argv[2] if len(sys.argv) > 2 else None

    if not os.path.exists(f_avant):
        print(f"Fichier introuvable : {f_avant}")
        print("Lance d'abord : curl.exe -s http://localhost:8080/benchmark/taxi/run -o taxi_avant.json")
        sys.exit(1)

    avant = charger(f_avant)
    apres = charger(f_apres) if f_apres else None

    afficher_tableau(avant, apres)
    tracer(f_avant, f_apres)
