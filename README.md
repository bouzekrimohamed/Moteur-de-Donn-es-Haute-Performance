# Moteur de Données Haute Performance

Mini moteur de données en mémoire exposé via une API REST (Quarkus).

## Lancer le projet

```bash
./mvnw quarkus:dev
```

L'API est accessible sur `http://localhost:8080`.

---

## Endpoints disponibles

### 1. Créer une table

```http
POST /tables
Content-Type: application/json

{
  "tableName": "employes",
  "columns": [
    { "name": "id",     "type": "INT"    },
    { "name": "nom",    "type": "STRING" },
    { "name": "ville",  "type": "STRING" },
    { "name": "salaire","type": "DOUBLE" },
    { "name": "age",    "type": "INT"    }
  ]
}
```

Types supportés : `STRING`, `INT`, `LONG`, `DOUBLE`, `BOOLEAN`

---

### 2. Charger des données

```http
POST /tables/employes/load
Content-Type: application/json

{
  "rows": [
    { "id": 1, "nom": "Alice",   "ville": "Paris",   "salaire": 3500.0, "age": 30 },
    { "id": 2, "nom": "Bob",     "ville": "Lyon",    "salaire": 2800.0, "age": 25 },
    { "id": 3, "nom": "Charlie", "ville": "Paris",   "salaire": 4200.0, "age": 35 },
    { "id": 4, "nom": "Diana",   "ville": "Lyon",    "salaire": 3100.0, "age": 28 }
  ]
}
```

---

### 3. Requêtes

```http
POST /tables/employes/query
Content-Type: application/json
```

#### SELECT * (toutes les colonnes)
```json
{}
```

#### SELECT colonnes précises
```json
{ "select": ["nom", "salaire"] }
```

#### WHERE simple
```json
{
  "where": { "column": "age", "operator": ">", "value": 28 }
}
```

Opérateurs WHERE : `=`, `!=`, `<`, `>`, `<=`, `>=`, `CONTAINS`

#### WHERE + SELECT
```json
{
  "select": ["nom", "ville"],
  "where": { "column": "ville", "operator": "=", "value": "Paris" }
}
```

#### GROUP BY
```json
{
  "groupBy": "ville"
}
```

#### GROUP BY + agrégation
```json
{
  "select": ["SUM(salaire)", "AVG(salaire)", "MAX(salaire)"],
  "groupBy": "ville"
}
```

#### ORDER BY + LIMIT
```json
{
  "orderBy": "salaire",
  "orderAsc": false,
  "limit": 3
}
```

#### Requête complète
```json
{
  "select": ["nom", "salaire"],
  "where": { "column": "ville", "operator": "=", "value": "Paris" },
  "orderBy": "salaire",
  "orderAsc": false,
  "limit": 10
}
```

---

### 4. Autres endpoints

```http
GET    /tables                  → liste toutes les tables
GET    /tables/{tableName}      → schéma d'une table
GET    /tables/{tableName}/rows → toutes les lignes brutes
DELETE /tables/{tableName}      → supprimer une table
```

---

## Architecture

```
com.example.engine
├── api/
│   ├── TableResource.java   → endpoints CRUD tables + chargement
│   └── QueryResource.java   → endpoint requêtes
├── core/
│   ├── TableManager.java    → orchestrateur principal (@ApplicationScoped)
│   └── QueryEngine.java     → moteur SELECT / WHERE / GROUP BY / ORDER BY / LIMIT
└── storage/
    ├── Table.java           → représentation d'une table (orientée colonne)
    └── Column.java          → colonne typée avec stockage List<Object>
```
