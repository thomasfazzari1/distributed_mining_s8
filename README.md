# Projet Distributed Mining - Master 1 MIAGE 2023/2024

Ce projet, réalisé dans le cadre de notre formation, implémente un système de minage distribué utilisant la
communication par sockets. Il comprend un serveur centralisé et plusieurs workers, dont le rôle est d'exécuter des
tâches de minage (recherche d’un hash spécifique) de manière distribuée.

Les tâches sont obtenues via une API permettant leur génération ainsi que la validation des résultats obtenus.

## Table des matières

- [Aperçu](#aperçu)
- [Fonctionnalités](#fonctionnalités)
- [Prérequis](#prérequis)
- [Installation](#installation)
- [Usage](#usage)
    - [Serveur](#serveur)
    - [Worker](#worker)
- [Protocole](#protocole)
- [Résultats](#résultats)
- [Auteurs](#auteurs)

## Aperçu

Le système comporte trois acteurs principaux :

1. **Serveur** : Gère la connexion des workers, leur authentification, la distribution des tâches de minage et le
   lancement de celles-ci auprès de l'ensemble des workers connectés.
2. **Worker** : Se connecte au serveur, reçoit une tâche de minage, l'effectue et communique le résultat obtenu au
   serveur.
3. **API Raizo** : Génère les tâches de minage et vérifie les résultats obtenus.

## Fonctionnalités

- Authentification, connexion, déconnexion de plusieurs workers sur le serveur.
- Distribution de tâches de minage avec une difficulté spécifiée.
- Minage de tâches par l'ensemble des workers connectés et prêts.
- Communication en temps réel avec les workers (suivi de la progression, statut des différents workers, annulation du
  minage, etc.)
- Interfaces en ligne de commande permettant les interactions serveur/worker.

## Prérequis

- Java 17 ou supérieur
- Maven

## Installation

1. **Cloner le dépôt :**

   Clonez le dépôt GitHub sur votre machine locale en utilisant la commande suivante :

   ```sh
   git clone https://github.com/thomasfazzari1/distributed_mining_s8.git
   ```

2. **Accéder au répertoire du projet :**

    Naviguez vers le répertoire du projet :

    ```sh
    cd distributed_mining_s8
    ```

3. **Compiler le projet :**

    Compilez le projet en utilisant Maven :

    ```sh
    mvn clean install
    ```

## Usage

### Serveur

1. **Configurer les variables d'environnement :**

    - `API_KEY` : Clé d'API fournie par la webapp Raizo.
    - `PASSWORD` : Mot de passe du serveur pour l'authentification des workers.

2. **Lancer le serveur :**

   Pour démarrer le serveur, utilisez la commande suivante :
   ```sh
   java -jar target/server.jar
   ```

### Worker

1. **Lancer un worker :**

   Pour démarrer un worker, utilisez la commande suivante :
   ```sh
   java -jar target/worker.jar
    ```
2. **Authentifier le worker :**

   Au démarrage du worker, le dialogue avec le serveur commence, suivez le protocole pour authentifier le worker

3. **Déclarer le worker prêt au minage:**
   Utilisez la commande suivante :
   ```sh
   READY
    ```
   Le serveur devrait apporter la réponse suivante :
    ```sh
   OK
    ```

## Protocole

### Instructions serveur → client

- `WHO_ARE_YOU_?` : Première commande envoyée par le serveur à un client qui vient de se connecter. Le client doit
  répondre `ITS_ME`.
- `GIMME_PASSWORD` : Demande au client le mot de passe du serveru afin de l'authentifier. Le client doit
  répondre `PASSWD <password>`, où `<password>`correspond au mot de passe du serveur défini précédemment.
- `HELLO_YOU` : Confirme la validité du mot de passe et la connexion du client.
- `OK` : Confirmation de la prise en compte du statut `READY` d'un worker.
- `NONCE <start> <increment>` : Indique au client le nonce par lequel débuter et l'incrément à ajouter après chaque
  essai.
- `PAYLOAD <data>` : Fournit les données à utiliser pour le minage.
- `SOLVE <difficulty>` : Lance le minage pour une tâche de la difficulté spécifiée.
- `PROGRESS` : Demande l'état du minage des workers.
- `CANCELLED` : Annule la tâche en cours.
- `SOLVED` : Indique aux workers qu'une solution a été trouvée par l'un d'entre eux.

### Instructions client → serveur

- `ITS_ME` : Réponse à `WHO_ARE_YOU_?`.
- `PASSWD <password>` : Réponse à `GIMME_PASSWORD`.
- `READY` : Indique que le worker est prêt à miner.
- `FOUND <hash> <nonce>` : Indique qu'une solution a été trouvée par le worker pour la tâche courante.
- `TESTING <current_nonce>` : Réponse à `PROGRESS` si le worker est en cours de minage.
- `NOPE` : Réponse à `PROGRESS` si le worker n'est pas en cours de minage.

## Résultats

| Taille du Nonce | Statut |
|-----------------|--------|
| 1               | Passed |
| 2               | Passed |
| 3               | Passed |
| 4               | Passed |
| 5               | Passed |
| 6               | Passed |
| 7               | Passed |
| 8               | Passed |
| 9               | Passed |

## Auteurs

- FAZZARI Thomas
- CHARTIER Guillaume
- BADIR Ikram
- COLOMBANA Ugo


