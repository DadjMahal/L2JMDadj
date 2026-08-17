import sys, json, collections
b = json.load(sys.stdin).get("bots", [])
s = collections.Counter(x["state"] for x in b)
lv = collections.defaultdict(list)
for x in b:
    lv[x.get("race", "?")].append(x["level"])
print("players online:", len(b))
print("states:", dict(s))
for r in ["HUMAN", "ELF", "DARK_ELF", "ORC", "DWARF"]:
    l = lv[r]
    if l:
        print(f"  {r}: n{len(l)} maxL{max(l)} avgL{sum(l)//len(l)}")