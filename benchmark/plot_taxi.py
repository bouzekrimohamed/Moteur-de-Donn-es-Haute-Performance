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
    import matplotlib.ticker as mticker
except ImportError:
    print("matplotlib manquant : pip install matplotlib")
    sys.exit(1)

PAYMENT_LABELS = {
    1: "Carte bancaire",
    2: "Especes",
    3: "Pas de frais",
    4: "Litige",
    5: "Inconnu",
    6: "Annule",
}

COULEURS = ["#2196F3", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#00BCD4"]
C_BLEU   = "#2196F3"
C_VERT   = "#4CAF50"
C_ORANGE = "#FF9800"
C_ROUGE  = "#F44336"
C_VIOLET = "#9C27B0"


# ---------------------------------------------------------------------------
# Chargement / helpers
# ---------------------------------------------------------------------------

def charger(fichier):
    with open(fichier, "r", encoding="utf-8") as f:
        return json.load(f)

def fmt_ms(ms):
    if ms >= 1000:
        return f"{ms/1000:.1f}s"
    return f"{ms}ms"

def fmt_lignes(n):
    if n >= 1_000_000:
        return f"{n/1_000_000:.2f}M"
    return f"{n//1_000}k"

def trouver_section(donnees, mot_cle):
    for s in donnees.get("sections", []):
        if mot_cle.lower() in s.get("categorie", "").lower():
            return s
    return None


# ---------------------------------------------------------------------------
# FIGURE 1 — Requetes business
# ---------------------------------------------------------------------------

def graphe_requetes_business(donnees, donnees_apres=None, ax=None):
    requetes = donnees["requetes"]
    titres   = [r["titre"].replace(" – ", "\n").replace(" (", "\n(") for r in requetes]
    temps    = [r["dureeMs"] for r in requetes]
    y        = list(range(len(titres)))

    if ax is None:
        _, ax = plt.subplots(figsize=(12, 6))

    bars = ax.barh(y, temps, color=COULEURS[:len(titres)], alpha=0.85,
                   label="Avant" if donnees_apres else None, height=0.5)

    if donnees_apres:
        temps_ap = [r["dureeMs"] for r in donnees_apres["requetes"]]
        ax.barh([i + 0.5 for i in y], temps_ap, height=0.5,
                color=C_ROUGE, alpha=0.65, label="Apres optimisation")

    ax.set_yticks(y if not donnees_apres else [i + 0.25 for i in y])
    ax.set_yticklabels(titres, fontsize=8)
    ax.set_xlabel("Temps (ms)")
    ax.set_title("Requetes business — Yellow Taxi NYC", fontweight="bold")
    ax.xaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{int(v):,}"))
    ax.grid(axis="x", alpha=0.3)
    for bar, t in zip(bars, temps):
        ax.text(bar.get_width() + max(temps) * 0.01,
                bar.get_y() + bar.get_height() / 2,
                fmt_ms(t), va="center", fontsize=8)
    if donnees_apres:
        ax.legend(fontsize=8)


def graphe_revenus_paiement(donnees, ax=None):
    q1 = next((r for r in donnees["requetes"] if "paiement" in r["titre"].lower()), None)
    if not q1 or not q1.get("apercu"):
        return
    apercu  = sorted(q1["apercu"], key=lambda r: r.get("SUM(total_amount)", 0), reverse=True)
    labels  = [PAYMENT_LABELS.get(int(r.get("payment_type", 0)), f"Type {r.get('payment_type')}") for r in apercu]
    valeurs = [r.get("SUM(total_amount)", 0) for r in apercu]
    counts  = [r.get("count", 0) for r in apercu]

    if ax is None:
        _, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(labels, valeurs, color=COULEURS[:len(labels)], alpha=0.85)
    ax.set_ylabel("Revenus totaux ($)")
    ax.set_title("Revenus par mode de paiement", fontweight="bold")
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"${v/1_000_000:.1f}M"))
    ax.tick_params(axis="x", labelsize=8)
    ax.grid(axis="y", alpha=0.3)
    for bar, v, c in zip(bars, valeurs, counts):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() * 1.01,
                f"${v/1_000_000:.1f}M\n({fmt_lignes(c)} courses)",
                ha="center", va="bottom", fontsize=7)


def graphe_tarif_passagers(donnees, ax=None):
    q3 = next((r for r in donnees["requetes"] if "passager" in r["titre"].lower()), None)
    if not q3 or not q3.get("apercu"):
        return
    apercu  = sorted(
        [r for r in q3["apercu"] if r.get("passenger_count") and int(r.get("passenger_count", 0)) > 0],
        key=lambda r: int(r.get("passenger_count", 0))
    )
    labels  = [f"{int(r['passenger_count'])} pass." for r in apercu]
    valeurs = [r.get("AVG(fare_amount)", 0) for r in apercu]

    if ax is None:
        _, ax = plt.subplots(figsize=(8, 5))
    ax.plot(labels, valeurs, "o-", color=C_BLEU, linewidth=2, markersize=8)
    ax.fill_between(range(len(labels)), valeurs, alpha=0.1, color=C_BLEU)
    ax.set_ylabel("Tarif moyen ($)")
    ax.set_title("Tarif moyen par nombre de passagers", fontweight="bold")
    ax.set_xticks(range(len(labels)))
    ax.set_xticklabels(labels)
    ax.grid(True, alpha=0.3)
    for i, v in enumerate(valeurs):
        ax.annotate(f"${v:.2f}", (i, v), textcoords="offset points",
                    xytext=(0, 8), ha="center", fontsize=8)


def graphe_load_info(donnees, donnees_apres=None, ax=None):
    if ax is None:
        _, ax = plt.subplots(figsize=(6, 4))
    ax.axis("off")
    load  = donnees["loadMs"]
    debit = donnees.get("loadLignesParSeconde", 0)
    lignes = [
        ("Dataset", donnees.get("source", "N/A")),
        ("Lignes",  f"{donnees['nbLignes']:,}"),
        ("LOAD",    fmt_ms(load)),
        ("Debit",   f"{debit:,.0f} lignes/s"),
    ]
    if donnees_apres:
        la   = donnees_apres["loadMs"]
        da   = donnees_apres.get("loadLignesParSeconde", 0)
        gain = round((1 - la / max(load, 1)) * 100)
        lignes += [("", ""), ("Apres optim.", ""),
                   ("LOAD",  f"{fmt_ms(la)}  ({gain:+d}%)"),
                   ("Debit", f"{da:,.0f} lignes/s")]
    ax.set_title("Informations LOAD", fontweight="bold")
    y = 0.9
    for cle, val in lignes:
        if cle:
            ax.text(0.05, y, cle + " :", fontsize=10, transform=ax.transAxes, color="#555")
            ax.text(0.45, y, val,         fontsize=10, transform=ax.transAxes, fontweight="bold")
        y -= 0.12


# ---------------------------------------------------------------------------
# FIGURE 2 — WHERE : selectivite
# ---------------------------------------------------------------------------

def graphe_where_selectivite(donnees, ax=None):
    section = trouver_section(donnees, "WHERE")
    if not section:
        return
    mesures    = section["mesures"]
    etiquettes = []
    for m in mesures:
        t = m["titre"]
        if "70%" in t:   etiquettes.append("~70%\n(payment=1)")
        elif "15%" in t: etiquettes.append("~15%\n(tip>10$)")
        elif "2%"  in t: etiquettes.append("~2%\n(dist>20mi)")
        elif "1%"  in t: etiquettes.append("~1%\n(total<5$)")
        else:            etiquettes.append(t[:15])

    temps     = [m["dureeMs"]    for m in mesures]
    resultats = [m["nbResultats"] for m in mesures]

    if ax is None:
        _, ax = plt.subplots(figsize=(9, 5))

    bars = ax.bar(etiquettes, temps, color=COULEURS[:len(mesures)], alpha=0.85, width=0.5)
    ax.set_ylabel("Temps (ms)")
    ax.set_title("WHERE — Selectivite du filtre\n(parallel scan : temps quasi constant quel que soit le % de lignes)",
                 fontweight="bold")
    ax.grid(axis="y", alpha=0.3)

    for bar, t, nb in zip(bars, temps, resultats):
        ax.text(bar.get_x() + bar.get_width() / 2,
                bar.get_height() + max(temps) * 0.02,
                f"{fmt_ms(t)}\n{fmt_lignes(nb)} lignes",
                ha="center", va="bottom", fontsize=8)

    moy = sum(temps) / len(temps)
    ax.axhline(moy, color="red", linestyle="--", linewidth=1.2, alpha=0.7,
               label=f"Moyenne : {fmt_ms(int(moy))}")
    ax.legend(fontsize=8)


# ---------------------------------------------------------------------------
# FIGURE 3 — GROUP BY : cardinalite + agregations
# ---------------------------------------------------------------------------

def graphe_groupby_cardinalite(donnees, ax=None):
    section = trouver_section(donnees, "cardinalit") or trouver_section(donnees, "Cardinalit")
    if not section:
        return
    mesures    = section["mesures"]
    etiquettes = []
    for m in mesures:
        t = m["titre"]
        if "2 groupe" in t or "VendorID" in t:
            etiquettes.append("VendorID\n(2 groupes)")
        elif "6 groupe" in t or "payment" in t:
            etiquettes.append("payment_type\n(6 groupes)")
        elif "9 groupe" in t or "passenger" in t:
            etiquettes.append("passenger_count\n(9 groupes)")
        else:
            etiquettes.append(t[:20])

    temps = [m["dureeMs"] for m in mesures]

    if ax is None:
        _, ax = plt.subplots(figsize=(7, 4))

    bars = ax.bar(etiquettes, temps, color=[C_BLEU, C_VERT, C_ORANGE], alpha=0.85, width=0.45)
    ax.set_ylabel("Temps (ms)")
    ax.set_title("GROUP BY — Cardinalite\n(impact du nombre de groupes distincts)",
                 fontweight="bold")
    ax.grid(axis="y", alpha=0.3)
    maxT = max(temps) if temps else 1
    for bar, t in zip(bars, temps):
        ax.text(bar.get_x() + bar.get_width() / 2,
                bar.get_height() + maxT * 0.02,
                fmt_ms(t), ha="center", va="bottom", fontsize=9, fontweight="bold")


def graphe_agregations(donnees, ax=None):
    section = (trouver_section(donnees, "agr") or
               trouver_section(donnees, "SUM") or
               trouver_section(donnees, "agregation"))
    if not section:
        return
    mesures    = section["mesures"]
    etiquettes = []
    for m in mesures:
        u = m["titre"].upper()
        if   "SUM" in u: etiquettes.append("SUM")
        elif "AVG" in u: etiquettes.append("AVG")
        elif "MIN" in u: etiquettes.append("MIN")
        elif "MAX" in u: etiquettes.append("MAX")
        else:            etiquettes.append(u[:6])

    temps = [m["dureeMs"] for m in mesures]
    maxT  = max(temps) if temps else 1

    if ax is None:
        _, ax = plt.subplots(figsize=(7, 4))

    bars = ax.bar(etiquettes, temps,
                  color=[C_BLEU, C_VERT, C_ORANGE, C_VIOLET], alpha=0.85, width=0.45)
    ax.set_ylabel("Temps (ms)")
    ax.set_title("GROUP BY — Type d'agregation\n(SUM / AVG / MIN / MAX — meme colonne, meme dataset)",
                 fontweight="bold")
    ax.grid(axis="y", alpha=0.3)
    ax.set_ylim(0, maxT * 1.35)
    for bar, t in zip(bars, temps):
        ax.text(bar.get_x() + bar.get_width() / 2,
                bar.get_height() + maxT * 0.02,
                fmt_ms(t), ha="center", va="bottom", fontsize=9, fontweight="bold")


# ---------------------------------------------------------------------------
# FIGURE 4 — TOP-N
# ---------------------------------------------------------------------------

def graphe_topn(donnees, ax=None):
    section = trouver_section(donnees, "top")
    if not section:
        return
    mesures = section["mesures"]
    labels  = []
    for m in mesures:
        t = m["titre"]
        if   "1000" in t: labels.append("N = 1 000")
        elif "100"  in t: labels.append("N = 100")
        elif "10"   in t: labels.append("N = 10")
        else:             labels.append(t[:10])
    labels.reverse()
    temps = [m["dureeMs"] for m in mesures]
    temps.reverse()

    if ax is None:
        _, ax = plt.subplots(figsize=(8, 5))

    ax.plot(labels, temps, "o-", color=C_BLEU, linewidth=2.5, markersize=10)
    ax.fill_between(range(len(labels)), temps, alpha=0.1, color=C_BLEU)
    ax.set_ylabel("Temps (ms)")
    ax.set_title("TOP-N — Impact de la taille de N\n(min-heap O(n log k) — jamais de tri complet du dataset)",
                 fontweight="bold")
    ax.set_xticks(range(len(labels)))
    ax.set_xticklabels(labels)
    ax.grid(True, alpha=0.3)
    ax.set_ylim(0, max(temps) * 1.45 if temps else 100)

    for i, t in enumerate(temps):
        ax.annotate(fmt_ms(t), (i, t), textcoords="offset points",
                    xytext=(0, 10), ha="center", fontsize=9, fontweight="bold")

    ax.text(0.98, 0.08,
            "log(10)=3  log(100)=7  log(1000)=10\n→ temps presque constant",
            transform=ax.transAxes, fontsize=8, color="#555", ha="right", va="bottom",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#f0f0f0", alpha=0.8))


# ---------------------------------------------------------------------------
# Generation de toutes les figures
# ---------------------------------------------------------------------------

def tracer_tout(f_avant, f_apres=None, dossier="graphiques"):
    os.makedirs(dossier, exist_ok=True)
    avant = charger(f_avant)
    apres = charger(f_apres) if f_apres else None
    nb    = fmt_lignes(avant["nbLignes"])
    print(f"\nGeneration des graphiques — {nb} lignes\n")

    # Figure 1 — requetes business
    fig = plt.figure(figsize=(18, 12))
    fig.suptitle(f"Yellow Taxi NYC — {nb} lignes | Requetes business",
                 fontsize=14, fontweight="bold")
    graphe_requetes_business(avant, apres, ax=fig.add_subplot(2, 2, (1, 2)))
    graphe_revenus_paiement(avant,         ax=fig.add_subplot(2, 2, 3))
    graphe_tarif_passagers(avant,          ax=fig.add_subplot(2, 2, 4))
    plt.tight_layout(rect=[0, 0, 1, 0.96])
    _sauver(fig, dossier, "benchmark_taxi_business.png")

    # Figure 2 — WHERE selectivite
    if trouver_section(avant, "WHERE"):
        fig, ax = plt.subplots(figsize=(10, 5))
        fig.suptitle(f"Yellow Taxi NYC — {nb} lignes | WHERE Selectivite",
                     fontsize=13, fontweight="bold")
        graphe_where_selectivite(avant, ax=ax)
        plt.tight_layout(rect=[0, 0, 1, 0.93])
        _sauver(fig, dossier, "benchmark_taxi_where.png")
    else:
        print("[INFO] Section WHERE absente — regenere le JSON avec le serveur mis a jour")

    # Figure 3 — GROUP BY
    if trouver_section(avant, "cardinalit"):
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
        fig.suptitle(f"Yellow Taxi NYC — {nb} lignes | GROUP BY",
                     fontsize=13, fontweight="bold")
        graphe_groupby_cardinalite(avant, ax=ax1)
        graphe_agregations(avant,         ax=ax2)
        plt.tight_layout(rect=[0, 0, 1, 0.93])
        _sauver(fig, dossier, "benchmark_taxi_groupby.png")
    else:
        print("[INFO] Sections GROUP BY absentes — regenere le JSON")

    # Figure 4 — TOP-N
    if trouver_section(avant, "top"):
        fig, ax = plt.subplots(figsize=(9, 5))
        fig.suptitle(f"Yellow Taxi NYC — {nb} lignes | TOP-N",
                     fontsize=13, fontweight="bold")
        graphe_topn(avant, ax=ax)
        plt.tight_layout(rect=[0, 0, 1, 0.93])
        _sauver(fig, dossier, "benchmark_taxi_topn.png")
    else:
        print("[INFO] Section TOP-N absente — regenere le JSON")

    # Figure LOAD
    fig, ax = plt.subplots(figsize=(6, 4))
    graphe_load_info(avant, apres, ax=ax)
    _sauver(fig, dossier, "benchmark_taxi_load.png")

    print(f"\nTous les graphiques -> {os.path.abspath(dossier)}/")


def _sauver(fig, dossier, nom):
    chemin = os.path.join(dossier, nom)
    fig.savefig(chemin, dpi=150, bbox_inches="tight")
    print(f"[OK] {os.path.abspath(chemin)}")
    plt.close(fig)   # ferme sans bloquer — toutes les figures generees d'un coup


# ---------------------------------------------------------------------------
# Tableau console
# ---------------------------------------------------------------------------

def afficher_tableau(avant, apres=None):
    nb = avant["nbLignes"]
    print(f"\n{'='*72}")
    print(f"  Yellow Taxi NYC — {nb:,} lignes ({avant.get('source','')})")
    print(f"  LOAD : {avant['loadMs']:,} ms  |  {avant.get('loadLignesParSeconde',0):,.0f} lignes/s")
    print(f"{'='*72}")
    print(f"  {'Requete':<47} {'Temps':>7}  {'Resultats':>12}")
    print(f"  {'-'*70}")

    apres_map = {r["titre"]: r for r in apres["requetes"]} if apres else {}
    for r in avant["requetes"]:
        ligne = f"  {r['titre']:<47} {r['dureeMs']:>5}ms  {r['nbResultats']:>12,}"
        if r["titre"] in apres_map:
            a    = apres_map[r["titre"]]
            gain = round((1 - a["dureeMs"] / max(r["dureeMs"], 1)) * 100)
            ligne += f"  -> {a['dureeMs']:>5}ms ({gain:+d}%)"
        print(ligne)

    for section in avant.get("sections", []):
        print(f"\n  [{section['categorie']}]")
        for m in section.get("mesures", []):
            print(f"    {m['titre']:<52} {m['dureeMs']:>5}ms  {m['nbResultats']:>10,}")

    print(f"\n{'='*72}\n")


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
        print("Lance : curl.exe -s http://localhost:8080/benchmark/taxi/run -o taxi_avant.json")
        sys.exit(1)

    avant = charger(f_avant)
    apres = charger(f_apres) if f_apres else None

    afficher_tableau(avant, apres)
    tracer_tout(f_avant, f_apres)
