# jgit push after gc

Experimental project trying to reproduce a bug in jgit.

The problem observed was described in https://www.eclipse.org/lists/jgit-dev/msg03770.html

Basically after jgit gc with default options, all push operations **may** becomes slower due to inefficient counting of the objects to send. In some repositories the effect was huge (from 4s to 2 minutes), in some repositories there was no effect.

A workaround is to set `pack.singlePack=true` in the git config, which can programatically be done like this:

```
git.getRepository().getConfig();
config.setBoolean("pack", null, "singlePack", true);
config.save();
```
