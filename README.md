<<<<<<< Updated upstream
# Moteur-de-Donn-es-Haute-Performance
=======
# Moteur de Donnees Haute Performance

Ce depot contient un seul projet backend actif :
- `mini-engine-maven`

## Lancer le projet

```powershell
cd "mini-engine-maven"
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd quarkus:dev
```

## Fonctionnalites implementees (version simple)

- creation de table (nom, colonnes, types)
- chargement de donnees en memoire
- lecture des lignes chargees
- endpoint query en mode placeholder
>>>>>>> Stashed changes
