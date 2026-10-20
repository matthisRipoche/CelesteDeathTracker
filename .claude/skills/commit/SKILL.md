---
name: commit
description: Méthode de commit du projet CelesteDeathTracker (Conventional Commits en français, commits atomiques, vérifications avant commit). À utiliser pour CHAQUE commit dans ce dépôt, dès que l'utilisateur demande de commiter, d'enregistrer ou de sauvegarder des changements dans Git.
---

# Commiter dans CelesteDeathTracker

Ne commiter que sur demande explicite de l'utilisateur. Ne jamais pousser (`git push`) sans son accord :
après chaque série de commits, lui **demander** s'il faut pousser (étape 7), et attendre sa réponse.

## 0. Travailler sur une branche, jamais sur `main`

`main` déclenche la CI/CD (images + déploiement) : ne **jamais** y commiter directement.

```bash
git branch --show-current
```

- Si on est sur `main`, créer une branche avant tout commit : `git switch -c <type>/<sujet-court>`
  (`feat/stats-api`, `fix/login-csrf`, `docs/workflow`, `chore/...`). Le préfixe reprend le type
  du commit principal ; le sujet est en minuscules, mots séparés par des tirets.
- Une branche = une fonctionnalité ou un correctif. Si les changements en cours couvrent
  plusieurs sujets, les répartir sur plusieurs branches.
- Si on est déjà sur une branche dont le sujet correspond, y rester.
- Les changements non commités suivent au `git switch -c`.

## 1. Examiner avant de commiter

```bash
git status --short
git diff --stat
git log --oneline -10
```

- Lire les diffs des fichiers modifiés et parcourir les fichiers nouveaux (`??`) pour comprendre
  *pourquoi* ils changent, pas seulement *quoi*.
- **Secrets** : vérifier qu'aucun fichier ajouté ne contient de clé, mot de passe ou token en clair
  (`grep -rniE "api[_-]?key|password|secret|token|AIza|cdt_"` sur les fichiers nouveaux). Une
  référence à une variable d'environnement (`${STITCH_API_KEY}`) est acceptable.
- **Fichiers interdits** : `.env`, `dashboard-legacy/deaths.json`, `web/public/local-assets/**`
  (assets du jeu sous copyright), DLL du jeu, `*.previous`. Ils sont dans `.gitignore` ; s'ils
  apparaissent, s'arrêter et prévenir l'utilisateur.
- Signaler à l'utilisateur tout fichier dont l'intention n'est pas claire au lieu de le commiter
  au hasard.

## 2. Vérifier que ça compile et que les tests passent

Selon les dossiers touchés :

| Dossier | Commande |
|---|---|
| `web/` | `cd web && npx ng build` puis `npx ng test --watch=false --browsers=ChromeHeadless` |
| `api/` | `cd api && ./mvnw test` |
| `CelesteDeathTracker/` | `dotnet build CelesteDeathTracker/CelesteDeathTracker.csproj` (nécessite Celeste installé) et `dotnet test CelesteDeathTracker.Tests` (sans Celeste) |

Si une vérification échoue : ne pas commiter, montrer l'erreur à l'utilisateur. Si une vérification
est impossible (ex. mod sans Celeste), le dire dans le compte rendu.

## 3. Découper en commits atomiques

Un commit = une intention. Regrouper les fichiers par raison de changer, puis ordonner les
commits pour que **chacun compile seul** (dépendances d'abord) :

1. dépendances / outillage (`build`, `chore`)
2. code partagé (services `core/`, composants `shared/`, styles du design system)
3. modifications des fonctionnalités existantes (`refactor`, `fix`)
4. nouvelles fonctionnalités et leurs routes (`feat`)
5. documentation / configuration annexe (`docs`, `chore`)

Ne pas mélanger un refactor et une nouvelle fonctionnalité dans le même commit. Un petit
changement cohérent reste un seul commit : ne pas découper artificiellement.

## 4. Format du message : Conventional Commits, en français

```
<type>(<portée>): <résumé à l'impératif, minuscule, sans point final, ≤ 72 car.>

<corps facultatif : le POURQUOI et ce qui n'est pas évident, lignes ≤ 72 car.,
liste à tirets si plusieurs points>

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

**Types** : `feat` (fonctionnalité), `fix` (bug), `refactor` (sans changement de comportement
visible), `perf`, `style` (formatage), `test`, `docs`, `build` (dépendances, build), `ci`
(`.github/workflows`), `chore` (le reste).

**Portées** (dossier principal touché) : `web`, `api`, `mod`, `db` (migrations Flyway),
`docker`, `deploy`, `ci`. Omettre la portée si le commit est transversal ou purement doc.

Changement cassant (contrat d'API, payload du mod, migration incompatible) : `feat(api)!: ...`
et un pied de page `BREAKING CHANGE: <ce qui casse et comment migrer>`.

Exemples tirés de l'historique :

```
build(web): ajouter ng2-charts, chart.js et @angular/cdk
feat(web): coquille de navigation et composants partagés
refactor(web): migrer dashboard, login, pair et tokens vers le design system
docs: prompt Stitch pour les maquettes et serveur MCP associé
```

La ligne `Co-Authored-By` suit la consigne d'attribution en vigueur dans la session ; la mettre à
jour si celle-ci change.

## 5. Créer les commits (Windows / Git Bash)

Ne pas enchaîner `git commit -F - <<EOF ... EOF && ...` : le heredoc suivi de `&&` casse le
parseur de l'outil Bash. Écrire chaque message dans un fichier du **scratchpad** puis :

```bash
git add <fichiers précis du commit>      # jamais `git add -A` ni `git add .`
git commit -q -F "<scratchpad>/m1.txt"
```

- Écrire les messages avec `printf '%s\n' "ligne 1" "" "ligne 3" > fichier` ou l'outil Write
  (UTF-8 : les accents passent correctement).
- Les avertissements `LF will be replaced by CRLF` sont normaux sous Windows : les ignorer.
- Ne jamais utiliser `--no-verify`, `--amend` sur un commit déjà poussé, ni réécrire l'historique
  sans demande.

## 6. Contrôler et rendre compte

```bash
git status --short          # doit être vide (ou ne contenir que ce qui a été volontairement laissé)
git log --oneline -<n>
```

Rendre compte à l'utilisateur : la liste des commits créés (hash + message), les vérifications
lancées et leur résultat, et ce qui a été volontairement laissé hors commit.

## 7. Demander pour le push

À la fin de **chaque** commit (ou série de commits), poser la question « On pousse ? » à
l'utilisateur (`AskUserQuestion` : oui / non), en indiquant la branche et le nombre de commits en
avance sur le distant (`git status -sb`).

- **Oui** → `git push` (ou `git push -u origin <branche>` si la branche n'a pas de distant).
  Ne jamais utiliser `--force`. On pousse la branche de travail, jamais `main`.
- Après le push d'une branche, rappeler que la suite est une **pull request** vers `main`
  (la CI lance les tests ; la fusion déclenche le déploiement). Ne créer la PR que si
  l'utilisateur le demande (`gh` peut ne pas être connecté).
- La PR est fusionnée en **squash** (seul mode autorisé sur le dépôt) : un seul commit sur `main`,
  dont le message est le **titre de la PR**. Écrire donc ce titre comme un message Conventional
  Commits (`feat(api): …`) et résumer l'ensemble dans la description. La branche est supprimée
  à la fusion.
- **Non** → ne rien pousser, et ne pas reposer la question avant le prochain commit.
- Si aucun remote n'est configuré (`git remote -v` vide), le dire au lieu de proposer le push.
- L'accord vaut pour ce push seulement, pas pour les suivants.
