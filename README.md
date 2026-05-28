# Moteur de Donnees Haute Performance

Prototype pedagogique d'un mini moteur de donnees en Java avec Quarkus.

Le projet expose une API REST permettant de creer des tables typees, de charger des donnees, puis d'executer des requetes simples sur un stockage oriente colonnes. Il sert de base pour experimenter quelques concepts de moteur de requetes : projection, filtrage, groupement, agregations, tri, limite, parcours par curseur et traitements parallelises sur certaines operations.

## Fonctionnalites

- Creation, consultation et suppression de tables via API REST.
- Colonnes typees : `STRING`, `INT`, `LONG`, `DOUBLE`, `BOOLEAN`.
- Chargement de lignes depuis un corps JSON.
- Chargement de fichiers CSV dans une table existante.
- Chargement de fichiers Parquet dans une table existante.
- Import d'un fichier Parquet avec creation automatique de la table depuis le schema du fichier.
- Requetes avec :
  - projection (`select`) ;
  - filtre `where` ;
  - `groupBy` ;
  - agregations `SUM`, `AVG`, `MIN`, `MAX` et compteur `count` ;
  - tri `orderBy` ;
  - limite `limit`.
- Stockage oriente colonnes en memoire, avec bascule vers des segments disque temporaires lorsque le seuil de lignes en memoire est atteint.
- Execution parallelisee de certaines requetes lorsque la table est decoupee en plusieurs segments ou chunks memoire.

## Architecture

Le projet est organise autour de quatre blocs principaux :

- **API REST Quarkus** : expose les endpoints de gestion des tables, de chargement de fichiers et de requetes.
- **TableManager** : centralise le cycle de vie des tables, les schemas, le chargement de lignes et l'appel au moteur de requetes.
- **QueryEngine** : execute les operations de selection, filtrage, groupement, agregation, tri et limite.
- **Stockage** : represente les donnees sous forme de colonnes et fournit des curseurs pour parcourir les lignes en memoire et dans les segments disque temporaires.

## Structure du projet

```text
src/main/java/com/example/engine
+-- api/
|   +-- TableResource.java      # CRUD des tables et chargement JSON
|   +-- QueryResource.java      # endpoint de requetes
|   +-- FileLoadResource.java   # chargement CSV et Parquet
+-- core/
|   +-- TableManager.java       # gestionnaire des tables
|   +-- QueryEngine.java        # moteur de requetes
+-- loader/
|   +-- CsvLoader.java          # lecture de fichiers CSV
|   +-- ParquetLoader.java      # lecture de fichiers Parquet
+-- storage/
    +-- Table.java              # table orientee colonnes
    +-- Column.java             # colonne typee
    +-- Row.java                # representation logique d'une ligne
    +-- RowCursor.java          # interface de parcours
    +-- MemoryRowCursor.java    # parcours des lignes en memoire
    +-- DiskRowCursor.java      # parcours des segments disque
    +-- CompositeRowCursor.java # parcours combine disque + memoire
```

Des tests unitaires sont presents dans :

```text
src/test/java/com/example/engine/EngineUnitTest.java
```

## Lancement du projet

Prerequis :

- Java 17
- Maven wrapper fourni par le projet

Lancer l'application en mode developpement :

```bash
./mvnw quarkus:dev
```

Sous Windows :

```bash
mvnw.cmd quarkus:dev
```

L'API est disponible par defaut sur :

```text
http://localhost:8080
```

Lancer les tests :

```bash
./mvnw test
```

## Exemples d'utilisation

### Creer une table

```bash
curl -X POST http://localhost:8080/tables \
  -H "Content-Type: application/json" \
  -d '{
    "tableName": "employes",
    "columns": [
      { "name": "id", "type": "INT" },
      { "name": "nom", "type": "STRING" },
      { "name": "ville", "type": "STRING" },
      { "name": "salaire", "type": "DOUBLE" },
      { "name": "age", "type": "INT" }
    ]
  }'
```

### Charger des lignes JSON

```bash
curl -X POST http://localhost:8080/tables/employes/load \
  -H "Content-Type: application/json" \
  -d '{
    "rows": [
      { "id": 1, "nom": "Alice", "ville": "Paris", "salaire": 3500.0, "age": 30 },
      { "id": 2, "nom": "Bob", "ville": "Lyon", "salaire": 2800.0, "age": 25 },
      { "id": 3, "nom": "Charlie", "ville": "Paris", "salaire": 4200.0, "age": 35 }
    ]
  }'
```

### Executer une requete

```bash
curl -X POST http://localhost:8080/tables/employes/query \
  -H "Content-Type: application/json" \
  -d '{
    "select": ["nom", "salaire"],
    "where": { "column": "ville", "operator": "=", "value": "Paris" },
    "orderBy": "salaire",
    "orderAsc": false,
    "limit": 10
  }'
```

Le resultat contient le nombre de lignes, la liste des colonnes retournees et les valeurs sous forme de tableau :

```json
{
  "totalRows": 2,
  "columns": ["nom", "salaire"],
  "rows": [
    ["Charlie", 4200.0],
    ["Alice", 3500.0]
  ]
}
```

### Requete avec groupement et agregations

```bash
curl -X POST http://localhost:8080/tables/employes/query \
  -H "Content-Type: application/json" \
  -d '{
    "select": ["SUM(salaire)", "AVG(salaire)", "MAX(salaire)"],
    "groupBy": "ville"
  }'
```

### Charger un fichier CSV

Le fichier CSV doit contenir une ligne d'en-tete correspondant aux colonnes de la table.

```bash
curl -X POST http://localhost:8080/tables/employes/load/csv \
  -H "Content-Type: application/json" \
  -d '{ "filePath": "/chemin/absolu/employes.csv" }'
```

### Charger ou importer un fichier Parquet

Charger un Parquet dans une table existante :

```bash
curl -X POST http://localhost:8080/tables/employes/load/parquet \
  -H "Content-Type: application/json" \
  -d '{ "filePath": "/chemin/absolu/employes.parquet" }'
```

Importer un Parquet en creant automatiquement la table depuis son schema :

```bash
curl -X POST http://localhost:8080/tables/import/parquet \
  -H "Content-Type: application/json" \
  -d '{ "filePath": "/chemin/absolu/employes.parquet" }'
```

## Endpoints principaux

```text
POST   /tables                       # creer une table
GET    /tables                       # lister les tables
GET    /tables/{tableName}           # consulter le schema d'une table
DELETE /tables/{tableName}           # supprimer une table
POST   /tables/{tableName}/load      # charger des lignes JSON
GET    /tables/{tableName}/rows      # lire les lignes brutes
POST   /tables/{tableName}/query     # executer une requete
POST   /tables/{tableName}/load/csv  # charger un CSV
POST   /tables/{tableName}/load/parquet
POST   /tables/import/parquet        # creer une table depuis un Parquet
```

## Format des requetes

Exemple complet :

```json
{
  "select": ["nom", "salaire"],
  "where": {
    "column": "age",
    "operator": ">",
    "value": 28
  },
  "groupBy": null,
  "orderBy": "salaire",
  "orderAsc": false,
  "limit": 10
}
```

Operateurs `where` supportes :

```text
=, ==, !=, <>, <, >, <=, >=, CONTAINS
```

## Limites actuelles

- Les donnees ne sont pas persistantes entre deux lancements de l'application.
- Les segments disque sont temporaires et servent uniquement a limiter la pression memoire.
- Le moteur ne supporte pas le SQL : les requetes sont decrites en JSON.
- Le `where` ne gere qu'une seule condition a la fois.
- Il n'y a pas encore de jointures, d'index, de transactions ou de gestion avancee de concurrence.
- Le typage reste simple et base sur quelques types Java courants.
- `ORDER BY` sans `LIMIT` applique une limite defensive interne afin d'eviter de materialiser un volume trop important de resultats.
