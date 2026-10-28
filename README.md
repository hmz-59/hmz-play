# HMZ Play

Écran de voiture façon CarPlay pour une tablette Android, relié à un iPhone en Bluetooth — sans dongle, sans appli sur l'iPhone.

## Ce que ça fait
- Écran d'accueil : carte (sombre), musique en cours, dernière notification
- Grille d'applis (Waze, Maps, Spotify… celles installées sur la tablette)
- Musique de l'iPhone : titre, artiste, progression, lecture/pause/suivant/précédent, volume (Apple Media Service)
- Notifications de l'iPhone (SMS, WhatsApp, Instagram…) avec bannière (Apple Notification Center Service)
- Appel entrant : nom de l'appelant, **Accepter / Refuser** depuis la tablette
- Batterie de l'iPhone
- Reconnexion automatique à chaque démarrage de la voiture

## Installation
1. Sur la tablette, télécharge `HMZ-Play.apk` depuis la dernière release :
   `https://github.com/<utilisateur>/hmz-play/releases/latest/download/HMZ-Play.apk`
2. Ouvre le fichier et autorise « Installer des applis inconnues ».
3. Lance HMZ Play, accepte les autorisations.
4. Sur l'iPhone : Réglages → Bluetooth → touche le nom de la tablette sous « Autres appareils », accepte le jumelage et « Afficher les notifications ».
5. (Conseillé) Réglages de HMZ Play → « Écran d'accueil » → choisis HMZ Play.

Le son (musique, appels) reste en Bluetooth entre l'iPhone et la voiture, comme d'habitude.

## Limites (imposées par Apple)
- Pas de lecture/réponse aux SMS ni de lancement d'appel depuis la tablette
- Pas de pochette d'album

## Build
Chaque push sur `main` compile l'APK via GitHub Actions et publie une release.
