# Audit 50 - committed-secret hygiene (task_0027)

Date: 2026-08-12 . Findings REDACTED - full values deliberately NOT printed.

## Findings table (where / what / risk / fix)
| Where | What | Risk | Fix |
|---|---|---|---|
| git remote origin URL | Embedded GitHub PAT (https://token@github.com/...) | Live token in repo history; anyone with repo read can push/auth as owner | Revoke+rotate the PAT in GitHub settings; strip token from remote (set to https://github.com/DadjMahal/L2JMDadj.git); use a credential helper. Rotate is REQUIRED - token is already baked into history. |
| scripts/*.sh, probes, docs, TASKS.md | gameserver DB password literal (mysql user l2j) | Plaintext prod DB cred in repo | Rotate the gameserver DB password; externalize via env/.env; gitignore. |
| AIPlayerEngine/examples/*.java, engine/*, protocol/* | same DB/account password literals | Same as above | Externalize via config/env, not committed defaults. |
| tmp/patch0/final_upgrade copies | duplicate copies of the above in temp patch dirs | Redundant copies of secrets | Exclude tmp/ from commit (gitignore) or scrub. |

## Recommendation
1. Rotate BOTH the GitHub PAT and the gameserver DB password now (already public in history).
2. Committed docs should keep only a redacted placeholder (l2j / ********).
3. Add .gitignore for /tmp*, .env, *.pem, and a pre-commit secret scan.
4. Scope the GH PAT minimally; prefer SSH deploy keys going forward.
