"""
Génère un fichier CSV pour les benchmarks du moteur de données.

Schéma : id, produit, categorie, prix, quantite, region, annee

Usage :
  python generate_dataset.py                        -> 4 000 000 lignes -> ventes_4M.csv
  python generate_dataset.py 1000000                -> 1 000 000 lignes -> ventes_1M.csv
  python generate_dataset.py 4000000 mon_fichier.csv

Chargement via l'API :
  GET http://localhost:8080/benchmark/csv?fichier=<chemin_absolu>
"""

import csv
import os
import random
import sys
import time

CATEGORIES = ["Electronique", "Vetements", "Alimentation", "Livres", "Sport"]
REGIONS    = ["Paris", "Lyon", "Marseille", "Bordeaux", "Lille"]
PRODUITS   = [
    "Laptop", "Telephone", "Tablette", "Montre", "Casque",
    "T-shirt", "Jean", "Veste", "Pain", "Fromage",
    "Roman", "Guide", "Velo", "Raquette", "Ballon",
]


def generer(nb_lignes: int, fichier: str) -> None:
    random.seed(42)
    debut = time.time()

    with open(fichier, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "produit", "categorie", "prix", "quantite", "region", "annee"])

        batch = []
        for i in range(1, nb_lignes + 1):
            batch.append([
                i,
                PRODUITS[i % len(PRODUITS)],
                CATEGORIES[i % len(CATEGORIES)],
                round(10 + random.random() * 990, 2),
                random.randint(1, 100),
                REGIONS[i % len(REGIONS)],
                2020 + (i % 5),
            ])
            if len(batch) == 50_000:
                writer.writerows(batch)
                batch.clear()
            if i % 500_000 == 0:
                print(f"  {i:>10,} lignes  ({i / nb_lignes * 100:.0f}%)...")

        if batch:
            writer.writerows(batch)

    duree  = time.time() - debut
    taille = os.path.getsize(fichier) / (1024 * 1024)
    debit  = nb_lignes / duree if duree > 0 else 0

    print(f"\nFichier genere : {os.path.abspath(fichier)}")
    print(f"  Lignes  : {nb_lignes:,}")
    print(f"  Taille  : {taille:.1f} Mo")
    print(f"  Duree   : {duree:.1f} s")
    print(f"  Debit   : {debit:,.0f} lignes/s")


if __name__ == "__main__":
    nb      = int(sys.argv[1]) if len(sys.argv) > 1 else 4_000_000
    suffixe = f"{nb // 1_000_000}M" if nb >= 1_000_000 else f"{nb // 1_000}k"
    fichier = sys.argv[2] if len(sys.argv) > 2 else f"ventes_{suffixe}.csv"

    print(f"Generation de {nb:,} lignes -> '{fichier}'")
    generer(nb, fichier)
