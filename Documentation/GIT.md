# Git

## Current state

The `~/L2JM/` repository has been initialized as a new Git repository tracking only
the L2J Dadj Mahal Interlude project.

```bash
$ cd ~/L2JM && git remote -v
origin	https://github.com/DadjMahal/L2JMDadj.git (fetch)
origin	https://github.com/DadjMahal/L2JMDadj.git (push)
```

- The original Mobius history was removed from this repo.
- The upstream Mobius repository is preserved separately in `~/L2JM/L2JMobiusUpstream/`.
- `L2JMobiusUpstream/` is a shallow clone (`--depth 1`) of the original
  `https://gitlab.com/MobiusDevelopment/L2J_Mobius.git` at the latest commit.

## L2JMobiusUpstream

Contains all Mobius chronicles (Ertheia, HighFive, Classic, Essence, Interlude, etc.)
as a reference. This directory is excluded from the L2JMDadj git repository.
It can be updated with:

```bash
cd ~/L2JM/L2JMobiusUpstream && git pull
```
